#!/bin/bash
docker service rm backend-local_binary-proxy
docker config rm binary-proxy-config-0

docker image rm --force n7fr846yfa6ohlhe/mbugai:binary-proxy-1

mvn clean install -DskipTests

docker login -p=sec^GHRmfZu7uTv -u=n7fr846yfa6ohlhe
docker build -t n7fr846yfa6ohlhe/mbugai:binary-proxy-1 -f Dockerfile .
docker push n7fr846yfa6ohlhe/mbugai:binary-proxy-1

docker tag n7fr846yfa6ohlhe/mbugai:binary-proxy-1 n7fr846yfa6ohlhe/mbugai:binary-proxy-"$1"
#docker push n7fr846yfa6ohlhe/mbugai:binary-proxy-"$1"

read -p "Press any key..."