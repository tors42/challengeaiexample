# Challenge AI Example

A simple web application which uses Chariot to perform a OAuth 2.0 PKCE authentication flow,
to get a token which has the permission to create a challenge,
and then uses that token to challenge the AI.

## Build

    A JDK can be downloaded from https://jdk.java.net and unpacked in some directory

    $ JAVA_HOME=<path-to-unpacked-jdk-directory> mvn clean verify

## Run

    $ target/maven-jlink/default/bin/main

## GitHub Codespaces

[![Open in GitHub Codespaces](https://github.com/codespaces/badge.svg)](https://github.com/codespaces/new?hide_repo_select=true&ref=main&repo=507622401)
