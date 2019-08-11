#!/bin/bash

export SCRIPT_LOCT=$( cd $( dirname $0 ); pwd )
cd $SCRIPT_LOCT

NUM_ZAM_DATA_VOLUMES=`docker volume ls | grep "zenhub-api-mirror-data-volume" | wc -l`
#NUM_ZAM_CONFIG_VOLUMES=`docker volume ls | grep "zenhub-api-mirror-config-volume" | wc -l`

set -e

if [ "$NUM_ZAM_DATA_VOLUMES" != "1" ]; then
    docker volume create zenhub-api-mirror-data-volume
fi
set +e

docker rm -f zenhub-api-mirror-container > /dev/null 2>&1

set -e

# Erase the config volume after each start
#if [ "$NUM_ZAM_CONFIG_VOLUMES" != "1" ]; then
docker volume rm -f zenhub-api-mirror-config-volume
docker volume create zenhub-api-mirror-config-volume
#fi


docker run  -d  -p 9443:9443 --name zenhub-api-mirror-container \
    -v zenhub-api-mirror-data-volume:/home/default/data \
    -v $SCRIPT_LOCT/../../ZenHubApiMirrorLiberty/resources/zenhub-settings.yaml:/config/zenhub-settings.yaml \
    -v zenhub-api-mirror-config-volume:/config \
    --restart always \
    --cap-drop=all \
    --tmpfs /opt/ol/wlp/output --tmpfs /logs \
    --read-only \
    zenhub-api-mirror
