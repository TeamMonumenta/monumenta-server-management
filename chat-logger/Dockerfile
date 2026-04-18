FROM eclipse-temurin:21

ARG USERNAME
ARG UID
ARG GID
RUN : "${USERNAME:?'USERNAME' argument needs to be set and non-empty.}"
RUN : "${UID:?'UID' argument needs to be set and non-empty.}"
RUN : "${GID:?'GID' argument needs to be set and non-empty.}"

ENV USERHOME=/home/$USERNAME

RUN groupadd --non-unique -g $GID $USERNAME && \
        useradd --non-unique -lmNs /bin/bash -u $UID -g $GID $USERNAME

COPY chat-logger/build/libs/chat-logger.jar /app/chat-logger.jar
COPY chat-logger/docker/start.sh /start.sh
RUN chmod +x /start.sh

# Mount points:
#   /config/network_relay.yml  - NetworkRelay config (rabbitmq URI, shard name)
#   /logs/chat                 - chat log output directory
# Environment variables:
#   CHAT_LOGGER_CONFIG  - override config file path (default: /config/network_relay.yml)
#   CHAT_LOG_DIR        - override log directory (default: /logs/chat)
#   JAVA_MEM            - JVM heap size (default: 256m)

USER $USERNAME
WORKDIR $USERHOME
CMD ["/start.sh"]
