#!/bin/bash
mvn clean install -DskipTests
docker build -t n7fr846yfa6ohlhe/mbugai:binary-proxy-$1 -f Dockerfile .
docker push n7fr846yfa6ohlhe/mbugai:binary-proxy-$1

read -p "Press any key..."