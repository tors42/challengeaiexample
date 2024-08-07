package example;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import com.sun.net.httpserver.*;
import chariot.*;
import chariot.Client.*;

public class Main {

    static String challengeForm = """
      <html>
        <head>
          <title>Challenge AI</title>
        </head>
        <body>
          <form action="/loginAndChallenge">
            <input type="submit" value="Challenge AI (5+3, level 1, casual)"/>
          </form>
        </body>
      </html>""";

    static String gameLinkTemplate = """
      <html>
        <head><title>Game</title></head>
        <body>
          Game: <a href="%1$s">%1$s</a>
        </body>
      </html>""";

    static Map<UUID, Session> sessionCache = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        new Main(Settings.load(args));
    }

    Main(Settings settings) throws Exception {
        URI lichessUri        = settings.lichessURI();
        URI publicUri         = settings.publicURI();
        var bindSocketAddress = settings.bindSocketAddress();

        System.out.println("Public URL: " + publicUri);
        System.out.println("Binding to: " + bindSocketAddress);
        System.out.println("Lichess URL: " + lichessUri);

        var httpServer = HttpServer.create(bindSocketAddress, 0);
        httpServer.setExecutor(Executors.newCachedThreadPool());
        httpServer.createContext("/", exchange -> {
            switch (exchange.getRequestURI().getPath()) {

                case "/" -> respond(exchange, 200, challengeForm);

                case "/loginAndChallenge" -> {
                    var session = new Data(UUID.randomUUID(), new CompletableFuture<>(), new CompletableFuture<>());
                    sessionCache.put(session.id(), session);
                    exchange.getResponseHeaders().put("Set-Cookie", List.of("id=" + session.id()));
                    var authResult = Client.auth(c -> c.api(lichessUri),
                            uri -> redirect(exchange, uri.toString()),
                            pkce -> pkce
                                .scope(Scope.challenge_write)
                                .customRedirect(
                                    publicUri.resolve("/redirect"),
                                    () -> session.codeAndStateFuture().join())
                            );
                    session.authResultFuture().complete(authResult);
                }

                case "/redirect" -> {
                    if (! (readSession(exchange) instanceof Data(var id, var csFuture, var authFuture))) {
                        respond(exchange, 400, "Missing cookie");
                        return;
                    }
                    var params = parseQueryParams(exchange.getRequestURI().getQuery());
                    String code = params.getOrDefault("code", "");
                    String state = params.getOrDefault("state", "");

                    csFuture.complete(new CodeAndState(code, state));
                    var authResult = authFuture.join();
                    if (authResult instanceof AuthOk(var client)) {
                        client.challenges().challengeAI(conf -> conf.clockBlitz5m3s().level(1))
                            .ifPresentOrElse(challenge -> {
                                sessionCache.remove(id);
                                exchange.getResponseHeaders().put("Set-Cookie", List.of("id=deleted"));
                                redirect(exchange, String.format("/game?gameId=%s", challenge.id()));
                            },
                            () -> respond(exchange, 503, "Failed to challenge AI"));
                        client.revokeToken();
                    } else {
                        respond(exchange, 503, String.valueOf(authResult));
                    }
                }

                case "/game" -> {
                    var params = parseQueryParams(exchange.getRequestURI().getQuery());
                    String gameId = params.getOrDefault("gameId", "");
                    String body = String.format(gameLinkTemplate, lichessUri.resolve("/" + gameId));
                    respond(exchange, 200, body);
                }

                default -> respond(exchange, 404, "Not Found");
            }
        });
        httpServer.start();
    }

    static Session readSession(HttpExchange exchange) {
        try {
            return exchange.getRequestHeaders().entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().equals("cookie"))
                .map(Entry::getValue)
                .flatMap(List::stream)
                .flatMap(v -> Arrays.stream(v.split(";")))
                .map(String::trim)
                .filter(v -> v.startsWith("id="))
                .map(v -> v.substring("id=".length()))
                .filter(v -> !v.equals("deleted"))
                .filter(v -> !v.isBlank())
                .map(UUID::fromString)
                .findAny().map(uuid -> sessionCache.getOrDefault(uuid, Session.none))
                .orElse(Session.none);
        } catch (Exception e) {
            return Session.none;
        }
    }

    static void redirect(HttpExchange exchange, String location) {
        exchange.getResponseHeaders().put("Location", List.of(location));
        try {
            exchange.sendResponseHeaders(302, -1);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        } finally {
            exchange.close();
        }
    }

    static void respond(HttpExchange exchange, int code, String body) {
        var bytes = body.getBytes();
        try {
            exchange.getResponseHeaders().put("content-type", List.of("text/html"));
            exchange.sendResponseHeaders(code, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        } finally{
            exchange.close();
        }
    }

    static Map<String, String> parseQueryParams(String query) {
        return Arrays.stream(query.split("&")).collect(Collectors.toMap(
                    s -> s.split("=")[0],
                    s -> s.split("=")[1]));
    }

    sealed interface Session { static Session none = new None(); }
    record None() implements Session {}
    record Data(UUID id,
            CompletableFuture<CodeAndState> codeAndStateFuture,
            CompletableFuture<AuthResult> authResultFuture) implements Session {}

    record Settings(List<Option> options) {

        sealed interface Option permits PublicURI, LichessURI, BindPort, BindAddress {}
        record LichessURI(URI uri) implements Option {}
        record PublicURI(URI uri) implements Option {}
        record BindPort(int port) implements Option {}
        record BindAddress(InetAddress address) implements Option {}

        static Settings load(String[] args) throws Exception {
            var options = parseArgs(args);
            if (System.getenv("PORT") instanceof String port) options.add(new BindPort(Integer.parseInt(port)));
            if (System.getenv("LICHESS_URI") instanceof String uri) options.add(new LichessURI(URI.create(uri)));
            if (System.getenv("BIND_ADDRESS") instanceof String bind) options.add(new BindAddress(InetAddress.getByName(bind)));
            return new Settings(List.copyOf(options));
        }

        static List<Option> parseArgs(String[] args) {
            var options = new ArrayList<Option>();
            if (args.length == 1) options.add(new PublicURI(URI.create(args[0])));
            return options;
        }

        URI lichessURI() {
            return find(LichessURI.class).map(LichessURI::uri)
                .orElseGet(() -> URI.create("https://lichess.org"));
        }

        URI publicURI() {
            return find(PublicURI.class).map(PublicURI::uri)
                .orElseGet(() -> URI.create("http://localhost:8000/"));
        }

        int bindPort() {
            return find(BindPort.class).map(BindPort::port)
                .orElse(8000);
        }

        InetAddress bindAddress() throws UnknownHostException {
            return find(BindAddress.class).map(BindAddress::address)
                .orElse(InetAddress.getByName("0.0.0.0"));
        }

        InetSocketAddress bindSocketAddress() throws UnknownHostException {
            return new InetSocketAddress(bindAddress(), bindPort());
        }

        <T> Optional<T> find(Class<T> clazz) {
            return find(options, clazz);
        }

        static <T> Optional<T> find(List<Option> options, Class<T> clazz) {
            return options.stream()
                .filter(clazz::isInstance)
                .map(clazz::cast)
                .findAny();
        }
    }
}
