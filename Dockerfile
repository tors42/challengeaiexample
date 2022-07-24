FROM alpine:latest AS build
RUN apk add curl
RUN apk add binutils
RUN apk add openjdk17

COPY src /src

ARG chariotversion=0.0.44

RUN mkdir /modules
RUN curl \
--silent \
--location \
--output /modules/chariot.jar \
https://repo1.maven.org/maven2/io/github/tors42/chariot/$chariotversion/chariot-$chariotversion.jar

RUN javac \
--enable-preview \
--release 17 \
--module-path /modules \
--module-source-path src/ \
--module challengeaiexample \
-d out/classes

ARG version=0.0.1-SNAPSHOT

RUN jar \
--create \
--module-version $version \
--main-class example.Main \
--file /modules/challengeaiexample.jar \
-C out/classes/challengeaiexample .

RUN jlink \
--add-options=" --enable-preview" \
--compress 2 \
--no-man-pages \
--no-header-files \
--strip-debug \
--launcher challengeaiexample=challengeaiexample \
--output out/runtime \
--module-path /modules \
--add-modules challengeaiexample

FROM alpine:latest
COPY --from=build out/runtime /runtime

RUN /runtime/bin/java --list-modules

ARG appurl=https://challengeaiexample.herokuapp.com

ENV APPURL $appurl

CMD ["sh", "-c", "/runtime/bin/challengeaiexample $APPURL"]
