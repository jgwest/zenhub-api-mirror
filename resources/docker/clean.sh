#!/bin/bash
docker rm -f zenhub-api-mirror-container > /dev/null 2>&1

docker volume prune -f 

