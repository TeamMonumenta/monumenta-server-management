#!/bin/bash

check_var() {
    var="$1"
    if [[ "${!var} " == " " ]]; then
        echo "${var} environment variable must be set"
        exit 1
    fi
}

# CHAT_LOGGER_CONFIG - path to network_relay.yml (default: /config/network_relay.yml)
# CHAT_LOG_DIR       - directory for chat log files (default: /logs/chat)
# JAVA_MEM           - heap size (default: 256m)

export CHAT_LOGGER_CONFIG="${CHAT_LOGGER_CONFIG:-/config/network_relay.yml}"
export CHAT_LOG_DIR="${CHAT_LOG_DIR:-/logs/chat}"

MEM="${JAVA_MEM:-256m}"

mkdir -p "$CHAT_LOG_DIR"

echo "Executing:"
echo java -Xmx"$MEM" -Xms"$MEM" -jar /app/chat-logger.jar
exec java -Xmx"$MEM" -Xms"$MEM" -jar /app/chat-logger.jar
