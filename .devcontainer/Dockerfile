# See here for image contents: https://github.com/microsoft/vscode-dev-containers/tree/v0.159.0/containers/java/.devcontainer/base.Dockerfile
ARG VARIANT="17"
FROM mcr.microsoft.com/vscode/devcontainers/java:0-${VARIANT}

ARG CHARIOT="0.0.56"
RUN mkdir -p lib
RUN curl --location --output lib/chariot.jar https://repo1.maven.org/maven2/io/github/tors42/chariot/${CHARIOT}/chariot-${CHARIOT}.jar
