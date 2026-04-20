# chat-logger

Standalone service that connects to RabbitMQ and logs all Monumenta network chat messages to a rolling log file and stdout.

Each log line is prefixed with a timestamp and channel indicator:

```
[2026-04-18 14:23:01]: [valley] <l> Playername » hello (local chat)
[2026-04-18 14:23:02]: <g> OtherPlayer » global announcement
[2026-04-18 14:23:03]: [valley] <wc> Playername » world chat message
[2026-04-18 14:23:04]: <whisper> Sender → Receiver » private message
[2026-04-18 14:23:05]: <team:TeamName> Playername » team chat
```

Channel prefixes: `<l>` local, `<wc>` world, `<whisper>` DM, `<team:X>` team, `<g>` global/party/announcement.

### Special message content

Some messages carry extra information that has no plain-text representation. These are appended inline using `⟦…⟧` annotations (U+27E6/U+27E7), which are unlikely to appear in normal chat and are grep-friendly.

**Spoilers** — a player sends `||hidden text||`, which renders in-game as bold `SPOILER` with the original text revealed on hover. The reveal text is logged in a spoiler annotation:

```
[2026-04-18 14:23:06]: [valley] <l> Playername » SPOILER ⟦spoiler: hidden text⟧
```

**Item shares** — a player sends `<mainhand>` (or similar), which substitutes their held item as a clickable component. The item's display name is rendered in brackets and the item type and stack size are logged in an annotation:

```
[2026-04-18 14:23:07]: [valley] <l> Playername » [Pulsating Dust] ⟦item: 64x minecraft:sugar⟧
```

In console output, `⟦…⟧` annotations are rendered in gray to distinguish them from the channel-colored message text.

The service reconnects automatically to RabbitMQ on connection loss, using lapin's built-in auto-recovery. Non-recoverable errors exit the process (rely on Kubernetes to restart the pod).

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `AMQP_URI` | `amqp://guest:guest@127.0.0.1:5672` | RabbitMQ connection string |
| `SHARD_NAME` | `chat-logger` | Queue name. Must be unique across the network. |
| `REDIS_URI` | _(required)_ | Redis connection string (e.g. `redis://127.0.0.1:6379`). Used to resolve player UUIDs to names and look up channel info. Process exits if unset or unreachable. |
| `CHAT_LOGGER_DEBUG` | _(unset)_ | If set to any value, prints the full raw JSON of each chat message to stderr before formatting. |

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
