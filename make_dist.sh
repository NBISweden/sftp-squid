#!/bin/bash

# Script to create distribution zip file

[ -d dist ] && rm -rf dist

MVN_VERSION=$(mvn -q \
    -Dexec.executable="echo" \
    -Dexec.args='${project.version}' \
    --non-recursive \
    org.codehaus.mojo:exec-maven-plugin:1.3.1:exec)


DIR=dist/sftpsquid-$MVN_VERSION
mkdir -p $DIR

mvn clean compile assembly:single

cp sftpsquid $DIR
cp target/sftpsquid.jar $DIR
cp README.md $DIR
cp LICENSE $DIR

cd dist
zip sftpsquid-${MVN_VERSION}.zip sftpsquid-${MVN_VERSION}/*
mv sftpsquid-${MVN_VERSION}.zip ..

