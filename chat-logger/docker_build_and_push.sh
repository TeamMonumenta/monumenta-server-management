#!/bin/bash

docker build . --build-arg USERNAME=epic --build-arg UID=1000 --build-arg GID=1000 -t ghcr.io/teammonumenta/monumenta-automation/monumenta-chat-logger && docker push ghcr.io/teammonumenta/monumenta-automation/monumenta-chat-logger
