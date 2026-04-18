# chat-logger

Standalone Java service that connects to RabbitMQ and logs all Monumenta network chat messages to a rolling log file. Runs without a Minecraft server — no Bukkit/Paper required.

## How it works

The service uses the `NetworkRelayGeneric` API (from the `network-relay` module) to subscribe to the RabbitMQ exchange. When a `com.playmonumenta.networkchat.Message` packet arrives, it deserializes the Adventure component payload, converts it to plain text, and writes it via the `NetworkChatLog` log4j2 logger.

Each log line is prefixed with the originating shard name (from the RabbitMQ message source field):

```
[2026-04-18 14:23:01]: [valley] Playername: hello everyone
[2026-04-18 14:23:05]: [build] OtherPlayer: working on the new dungeon
```

## Building

From the project root:

```bash
./gradlew :chat-logger:shadowJar
# Output: chat-logger/build/libs/chat-logger.jar
```

## Configuration

### `network_relay.yml`

Controls the RabbitMQ connection. The most important fields:

| Field | Description |
|-------|-------------|
| `rabbitmq-uri` | AMQP connection string, e.g. `amqp://user:pass@rabbitmq:5672` |
| `shard-name` | Identity of this service on the network. Must be unique. Can be set via the `NETWORK_RELAY_NAME` env var instead (comment out `shard-name` in the file to use the env var). |
| `heartbeat-interval` | Seconds between heartbeat broadcasts (default: 3) |
| `destination-timeout` | Seconds before a destination is considered offline (default: 10) |

See `deploy/deployment.example.yml` for a full example embedded in a ConfigMap.

### Environment variables

| Variable | Default | Description |
|----------|---------|-------------|
| `NETWORK_RELAY_NAME` | `default-shard` | Shard name (overridden by `shard-name` in config file if set) |
| `CHAT_LOGGER_CONFIG` | `config/network_relay.yml` | Path to the `network_relay.yml` config file |
| `CHAT_LOG_DIR` | `logs/chat` | Directory where rolling chat log files are written |
| `JAVA_MEM` | `128m` | JVM heap size |

### Log output

Log formats match the server's `log4j2-shard.xml`:

- **File** (`$CHAT_LOG_DIR/latest.log`): `[yyyy-MM-dd HH:mm:ss]: <message>`
- **Console**: `[HH:mm:ss CHAT]: <message>`

Log files roll daily and at 50 MB, retaining up to 1000 files.

## Docker

Build the image from the **project root** (the Dockerfile COPYs the pre-built JAR):

```bash
./gradlew :chat-logger:shadowJar
docker build -f chat-logger/Dockerfile \
  --build-arg USERNAME=myuser \
  --build-arg UID=$(id -u) \
  --build-arg GID=$(id -g) \
  -t monumenta-chat-logger .
```

## Kubernetes

See `deploy/deployment.example.yml` for a reference Deployment + ConfigMap. Copy it to your ops repo, fill in:

- `rabbitmq-uri` in the ConfigMap
- `kubernetes.io/hostname` node selector
- `hostPath` for the log output directory
- Image name/tag
