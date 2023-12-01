#!/bin/bash

docker service rm backend-local_push-verifier
(DEPLOY_VERSION=1 docker stack deploy --with-registry-auth -c backend.yml backend-local)
docker service logs -f backend-local_push-verifier
docker service rm backend-local_push-verifier

#read -p "Press any key..."