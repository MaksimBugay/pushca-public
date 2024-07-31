#!/bin/bash

docker service rm backend-local_binary-proxy
docker config rm binary-proxy-config-0
(DEPLOY_VERSION=2 docker stack deploy --with-registry-auth -c backend.yml backend-local)

docker service logs -f backend-local_binary-proxy