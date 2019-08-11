#!/bin/bash
docker rm -f zenhub-api-mirror-container > /dev/null 2>&1

echo "WARNING: Running 'docker volume prune', this may erase other unused volumes on your system."

docker volume prune

