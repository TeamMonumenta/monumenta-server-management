# chat-logger

Standalone service that connects to RabbitMQ and logs all Monumenta network chat messages to a rolling log file and stdout.

Each log line is prefixed with the originating shard name and a timestamp:

```
[2026-04-18 14:23:01]: [valley] Playername: hello everyone
[2026-04-18 14:23:05]: [build] OtherPlayer: working on the new dungeon
```

The service reconnects automatically to RabbitMQ on connection loss, using lapin's built-in auto-recovery. Non-recoverable errors exit the process (rely on Kubernetes to restart the pod).

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `AMQP_URI` | `amqp://guest:guest@127.0.0.1:5672` | RabbitMQ connection string |
| `SHARD_NAME` | `chat-logger` | Queue name. Must be unique across the network. |

## Log output

Format: `[yyyy-MM-dd HH:mm:ss]: <message>`

Logs are written to `/logs`. Mount a volume there to persist them. The active file is `/logs/latest.log`; files roll daily and at 50 MB, producing `YYYY-MM-DD-N.log` archives.

## Building

```bash
docker build \
  --build-arg USERNAME=myuser \
  --build-arg UID=$(id -u) \
  --build-arg GID=$(id -g) \
  -t monumenta-chat-logger \
  chat-logger-rust/
```

## Kubernetes

See `deployment.example.yml` for a reference Deployment. Fill in:

- `AMQP_URI` with your RabbitMQ connection string
- `kubernetes.io/hostname` node selector
- `hostPath` for the log output directory
- Image name/tag
