#!/bin/bash

rm -rf out
rm -rf lib

mkdir -p lib

curl \
    --silent \
    --location \
    --output lib/chariot.jar \
    https://repo1.maven.org/maven2/io/github/tors42/chariot/0.0.56/chariot-0.0.56.jar

javac \
    --module-path lib/ \
    --module-source-path src/ \
    --module challengeaiexample \
    -d out/classes/

jlink \
    --compress 2 \
    --no-man-pages \
    --no-header-files \
    --strip-debug \
    --launcher challengeaiexample=challengeaiexample/example.Main \
    --output out/runtime \
    --module-path lib:out/classes \
    --add-modules challengeaiexample

out/runtime/bin/challengeaiexample $@
