#!/bin/bash

docker login

docker tag zenhub-api-mirror jgwest/zenhub-api-mirror

docker push jgwest/zenhub-api-mirror

