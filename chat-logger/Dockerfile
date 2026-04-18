FROM rust:1-bookworm AS builder
WORKDIR /build
COPY . .
RUN cargo build --release

FROM debian:bookworm-slim
RUN apt-get update \
 && apt-get install -y --no-install-recommends libssl3 ca-certificates \
 && rm -rf /var/lib/apt/lists/*

ARG USERNAME
ARG UID
ARG GID
RUN : "${USERNAME:?'USERNAME' argument needs to be set and non-empty.}"
RUN : "${UID:?'UID' argument needs to be set and non-empty.}"
RUN : "${GID:?'GID' argument needs to be set and non-empty.}"

RUN groupadd --non-unique -g $GID $USERNAME \
 && useradd --non-unique -lmNs /bin/bash -u $UID -g $GID $USERNAME

COPY --from=builder /build/target/release/chat-logger /app/chat-logger

# Mount points:
#   /logs        - chat log output directory
# Environment variables:
#   AMQP_URI     - RabbitMQ connection string (default: amqp://guest:guest@127.0.0.1:5672)
#   SHARD_NAME   - queue name / identity on the network (default: chat-logger)

USER $USERNAME
WORKDIR /home/$USERNAME
CMD ["/app/chat-logger"]
