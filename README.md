# Challenge AI Example

A simple web application which uses Chariot to perform a OAuth 2.0 PKCE authentication flow,
to get a token which has the permission to create a challenge,
and then uses that token to challenge the AI.

# Test on localhost

## Build

Example building an image for local testing,

    $ docker build --tag challengeaiexample --build-arg .
    ...
    Successfully tagged challengeaiexample:latest

## Run

Start a container named "challengeaiexample" from the image tagged "challengeaiexample",

    $ docker run -it --rm --name challengeaiexample --env PORT=8000 --publish 8000:8000 challengeaiexample

Then open a web browser and navigate to http://localhost:8000

# Test gitpod.io

You can deploy the app in gitpod https://gitpod.io/#https://github.com/tors42/challengeaiexample
