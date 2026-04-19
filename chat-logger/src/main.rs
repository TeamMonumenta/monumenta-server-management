use chrono::Local;
use lapin::{
    options::*,
    types::{AMQPValue, FieldTable},
    Connection, ConnectionProperties,
};
use redis::AsyncCommands;
use serde_json::Value;
use std::{
    collections::HashMap,
    env,
    fs::{File, OpenOptions},
    io::{BufWriter, Write},
    path::{Path, PathBuf},
};
use tokio::signal::unix::{signal, SignalKind};
use tokio_stream::StreamExt;

const CHAT_CHANNEL: &str = "com.playmonumenta.networkchat.Message";
const UUID2NAME_KEY: &str = "uuid2name";
const REDIS_CHANNELS_PATH: &str = "networkchat:channels";
const MAX_FILE_BYTES: u64 = 50 * 1024 * 1024;

// ---------------------------------------------------------------------------
// Rolling log file
// ---------------------------------------------------------------------------

struct RollingLog {
    dir: PathBuf,
    writer: BufWriter<File>,
    date: chrono::NaiveDate,
    bytes: u64,
}

impl RollingLog {
    fn open(dir: &Path) -> std::io::Result<Self> {
        std::fs::create_dir_all(dir)?;
        let today = Local::now().date_naive();
        let path = dir.join("latest.log");
        let file = OpenOptions::new().create(true).append(true).open(&path)?;
        let bytes = file.metadata()?.len();
        Ok(Self {
            dir: dir.to_path_buf(),
            writer: BufWriter::new(file),
            date: today,
            bytes,
        })
    }

    fn write_line(&mut self, line: &str) -> std::io::Result<()> {
        let now = Local::now();
        let today = now.date_naive();
        if today != self.date || self.bytes >= MAX_FILE_BYTES {
            self.rotate()?;
            self.date = today;
        }
        let ts = now.format("%Y-%m-%d %H:%M:%S");
        let full = format!("[{ts}]: {line}\n");
        self.writer.write_all(full.as_bytes())?;
        self.writer.flush()?;
        self.bytes += full.len() as u64;
        Ok(())
    }

    fn rotate(&mut self) -> std::io::Result<()> {
        self.writer.flush()?;
        let latest = self.dir.join("latest.log");
        let mut n = 1u32;
        loop {
            let dest = self
                .dir
                .join(format!("{}-{}.log", self.date.format("%Y-%m-%d"), n));
            if !dest.exists() {
                std::fs::rename(&latest, dest)?;
                break;
            }
            n += 1;
        }
        let file = OpenOptions::new().create(true).append(true).open(&latest)?;
        self.writer = BufWriter::new(file);
        self.bytes = 0;
        Ok(())
    }
}

// ---------------------------------------------------------------------------
// Channel info and color helpers
// ---------------------------------------------------------------------------

type Rgb = (u8, u8, u8);

struct ChannelInfo {
    name: String,
    channel_type: String,
    color: Option<Rgb>,
}

// Parses "#rrggbb" or a Minecraft NamedTextColor name as used by Adventure/network-chat.
fn parse_color(s: &str) -> Option<Rgb> {
    let s = s.trim();
    if let Some(hex) = s.strip_prefix('#') {
        if hex.len() == 6 {
            let r = u8::from_str_radix(&hex[0..2], 16).ok()?;
            let g = u8::from_str_radix(&hex[2..4], 16).ok()?;
            let b = u8::from_str_radix(&hex[4..6], 16).ok()?;
            return Some((r, g, b));
        }
    }
    // NamedTextColor RGB values from the Adventure library (Minecraft standard)
    match s {
        "black"        => Some((0, 0, 0)),
        "dark_blue"    => Some((0, 0, 170)),
        "dark_green"   => Some((0, 170, 0)),
        "dark_aqua"    => Some((0, 170, 170)),
        "dark_red"     => Some((170, 0, 0)),
        "dark_purple"  => Some((170, 0, 170)),
        "gold"         => Some((255, 170, 0)),
        "gray"         => Some((170, 170, 170)),
        "dark_gray"    => Some((85, 85, 85)),
        "blue"         => Some((85, 85, 255)),
        "green"        => Some((85, 255, 85)),
        "aqua"         => Some((85, 255, 255)),
        "red"          => Some((255, 85, 85)),
        "light_purple" => Some((255, 85, 255)),
        "yellow"       => Some((255, 255, 85)),
        "white"        => Some((255, 255, 255)),
        _              => None,
    }
}

// Default colors from NetworkChatPlugin.java mDefaultMessageColors.
// Returns None for channel types not defined there.
fn default_color_for_type(channel_type: &str) -> Option<Rgb> {
    match channel_type {
        "announcement" => Some((255, 85, 85)),   // NamedTextColor.RED
        "global"       => Some((255, 255, 255)), // NamedTextColor.WHITE
        "local"        => Some((255, 255, 85)),  // NamedTextColor.YELLOW
        "world"        => Some((85, 85, 255)),   // NamedTextColor.BLUE
        "party"        => Some((255, 85, 255)),  // NamedTextColor.LIGHT_PURPLE
        "team"         => Some((255, 255, 255)), // NamedTextColor.WHITE
        "whisper"      => Some((170, 170, 170)), // NamedTextColor.GRAY
        _              => None,
    }
}

fn parse_channel_json(json_str: &str) -> Option<ChannelInfo> {
    let v: Value = serde_json::from_str(json_str).ok()?;
    let name = v.get("name")?.as_str()?.to_string();
    let channel_type = v.get("type")?.as_str()?.to_string();
    let color = v
        .get("messageColor")
        .and_then(Value::as_str)
        .and_then(parse_color);
    Some(ChannelInfo {
        name,
        channel_type,
        color,
    })
}

// ---------------------------------------------------------------------------
// Redis state: player name cache + channel info cache
// ---------------------------------------------------------------------------

struct RedisState {
    uuid_map: HashMap<String, String>,
    // None value = already queried, not found in Redis
    channel_map: HashMap<String, Option<ChannelInfo>>,
    conn: Option<redis::aio::MultiplexedConnection>,
}

impl RedisState {
    async fn new(uri: Option<&str>) -> Self {
        let Some(uri) = uri else {
            return Self {
                uuid_map: HashMap::new(),
                channel_map: HashMap::new(),
                conn: None,
            };
        };

        let client = match redis::Client::open(uri) {
            Ok(c) => c,
            Err(e) => {
                eprintln!("Redis: invalid URI: {e}. UUID lookups and channel info will be unavailable.");
                return Self {
                    uuid_map: HashMap::new(),
                    channel_map: HashMap::new(),
                    conn: None,
                };
            }
        };

        let mut conn = match client.get_multiplexed_async_connection().await {
            Ok(c) => c,
            Err(e) => {
                eprintln!("Redis: connection failed: {e}. UUID lookups and channel info will be unavailable.");
                return Self {
                    uuid_map: HashMap::new(),
                    channel_map: HashMap::new(),
                    conn: None,
                };
            }
        };

        let uuid_map: HashMap<String, String> = conn
            .hgetall(UUID2NAME_KEY)
            .await
            .unwrap_or_default();
        eprintln!(
            "Redis: loaded {} UUID→name mappings from {UUID2NAME_KEY}",
            uuid_map.len()
        );

        Self {
            uuid_map,
            channel_map: HashMap::new(),
            conn: Some(conn),
        }
    }

    async fn lookup_player(&mut self, uuid: &str) -> String {
        if let Some(name) = self.uuid_map.get(uuid) {
            return name.clone();
        }
        if let Some(conn) = &mut self.conn {
            let result: redis::RedisResult<Option<String>> =
                conn.hget(UUID2NAME_KEY, uuid).await;
            if let Ok(Some(name)) = result {
                self.uuid_map.insert(uuid.to_string(), name.clone());
                return name;
            }
        }
        uuid.to_string()
    }

    // Returns (name, channel_type, per-channel color override) if found.
    async fn lookup_channel(&mut self, channel_id: &str) -> Option<(String, String, Option<Rgb>)> {
        if !self.channel_map.contains_key(channel_id) {
            let info = match &mut self.conn {
                Some(conn) => {
                    let result: redis::RedisResult<Option<String>> =
                        conn.hget(REDIS_CHANNELS_PATH, channel_id).await;
                    match result {
                        Ok(Some(json_str)) => parse_channel_json(&json_str),
                        _ => None,
                    }
                }
                None => None,
            };
            self.channel_map.insert(channel_id.to_string(), info);
        }
        self.channel_map
            .get(channel_id)?
            .as_ref()
            .map(|info| (info.name.clone(), info.channel_type.clone(), info.color))
    }
}

// ---------------------------------------------------------------------------
// Adventure Component → plain text
// ---------------------------------------------------------------------------

fn plain_text(v: &Value) -> String {
    match v {
        Value::String(s) => s.clone(),
        Value::Object(obj) => {
            let mut out = String::new();
            if let Some(Value::String(t)) = obj.get("text") {
                out.push_str(t);
            }
            if let Some(Value::Array(extra)) = obj.get("extra") {
                for child in extra {
                    out.push_str(&plain_text(child));
                }
            }
            out
        }
        _ => String::new(),
    }
}

// ---------------------------------------------------------------------------
// Message formatting
// ---------------------------------------------------------------------------

struct FormattedMessage {
    // Plain text for the log file (no ANSI codes).
    plain: String,
    // ANSI-colored for stdout: channel name and message body are colored,
    // brackets, sender, and separator are not.
    colored: String,
}

fn build_message(
    shard_prefix: &str,
    label: &str,
    sender: &str,
    message: &str,
    color: Option<Rgb>,
) -> FormattedMessage {
    let plain = format!("{shard_prefix}<{label}> {sender} » {message}");
    let colored = match color {
        Some((r, g, b)) => format!(
            "{shard_prefix}<\x1b[38;2;{r};{g};{b}m{label}\x1b[0m> {sender} » \x1b[38;2;{r};{g};{b}m{message}\x1b[0m"
        ),
        None => plain.clone(),
    };
    FormattedMessage { plain, colored }
}

async fn format_message(
    body: &[u8],
    redis: &mut RedisState,
    debug: bool,
) -> Option<FormattedMessage> {
    let root: Value = serde_json::from_slice(body).ok()?;
    if root.get("channel")?.as_str()? != CHAT_CHANNEL {
        return None;
    }

    if debug {
        eprintln!(
            "DEBUG: {}",
            serde_json::to_string_pretty(&root).unwrap_or_default()
        );
    }

    let data = root.get("data")?;
    let sender = data.get("senderName").and_then(Value::as_str).unwrap_or("?");
    let message = data.get("message").map(plain_text).unwrap_or_default();
    let extra = data.get("extra").and_then(Value::as_object);

    // Whispers are handled specially: the channel name in Redis is auto-generated
    // and not human-readable, so we use the extra data format instead.
    if let Some(extra) = extra {
        if let Some(receiver_uuid) = extra.get("receiver").and_then(Value::as_str) {
            let receiver = redis.lookup_player(receiver_uuid).await;
            let plain = format!("<whisper> {sender} → {receiver} » {message}");
            let (r, g, b) = default_color_for_type("whisper").unwrap();
            let colored = format!(
                "<\x1b[38;2;{r};{g};{b}mwhisper\x1b[0m> {sender} → {receiver} » \x1b[38;2;{r};{g};{b}m{message}\x1b[0m"
            );
            return Some(FormattedMessage { plain, colored });
        }
    }

    // Shard prefix for channels where location context is useful (world/local).
    let shard_prefix = extra
        .and_then(|e| e.get("fromShard").and_then(Value::as_str))
        .map(|s| format!("[{s}] "))
        .unwrap_or_default();

    // Look up channel by ID from Redis for name and color.
    if let Some(channel_id) = data.get("channelId").and_then(Value::as_str) {
        if let Some((name, channel_type, per_channel_color)) =
            redis.lookup_channel(channel_id).await
        {
            let color = per_channel_color.or_else(|| default_color_for_type(&channel_type));
            return Some(build_message(&shard_prefix, &name, sender, &message, color));
        }
    }

    // Fallback when Redis is unavailable or channel not found: use heuristics.
    let (label, color) = if let Some(extra) = extra {
        if extra.contains_key("fromWorld") {
            ("wc".to_string(), default_color_for_type("world"))
        } else if extra.contains_key("fromShard") {
            ("l".to_string(), default_color_for_type("local"))
        } else if let Some(team) = extra.get("team").and_then(Value::as_str) {
            (format!("team:{team}"), default_color_for_type("team"))
        } else {
            ("g".to_string(), default_color_for_type("global"))
        }
    } else {
        ("g".to_string(), default_color_for_type("global"))
    };

    Some(build_message(&shard_prefix, &label, sender, &message, color))
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let uri =
        env::var("AMQP_URI").unwrap_or_else(|_| "amqp://guest:guest@127.0.0.1:5672".into());
    let queue = env::var("SHARD_NAME").unwrap_or_else(|_| "chat-logger".into());
    let redis_uri = env::var("REDIS_URI").ok();
    let debug = env::var("CHAT_LOGGER_DEBUG").is_ok();

    let mut log = match RollingLog::open(Path::new("/logs")) {
        Ok(l) => l,
        Err(e) => {
            eprintln!("Failed to open log dir '/logs': {e}");
            std::process::exit(1);
        }
    };

    let mut redis = RedisState::new(redis_uri.as_deref()).await;

    let conn = Connection::connect(
        &uri,
        ConnectionProperties::default().enable_auto_recover(),
    )
    .await?;
    let ch = conn.create_channel().await?;

    ch.exchange_declare(
        "broadcast".into(),
        lapin::ExchangeKind::Fanout,
        ExchangeDeclareOptions {
            durable: false,
            ..Default::default()
        },
        FieldTable::default(),
    )
    .await?;

    let mut args = FieldTable::default();
    args.insert("x-expires".into(), AMQPValue::LongInt(1_200_000));
    ch.queue_declare(
        queue.as_str().into(),
        QueueDeclareOptions {
            durable: false,
            ..Default::default()
        },
        args,
    )
    .await?;

    ch.queue_bind(
        queue.as_str().into(),
        "broadcast".into(),
        "".into(),
        QueueBindOptions::default(),
        FieldTable::default(),
    )
    .await?;

    let mut consumer = ch
        .basic_consume(
            queue.as_str().into(),
            "chat-logger".into(),
            BasicConsumeOptions::default(),
            FieldTable::default(),
        )
        .await?;

    eprintln!("Connected to RabbitMQ, listening on queue '{queue}'");

    let mut sigterm = signal(SignalKind::terminate())?;

    loop {
        tokio::select! {
            biased;
            _ = sigterm.recv() => {
                eprintln!("Received SIGTERM, shutting down.");
                break;
            }
            _ = tokio::signal::ctrl_c() => {
                eprintln!("Received SIGINT, shutting down.");
                break;
            }
            delivery = consumer.next() => {
                match delivery {
                    Some(Ok(delivery)) => {
                        if let Some(msg) = format_message(&delivery.data, &mut redis, debug).await {
                            let now = Local::now();
                            let ts = now.format("%Y-%m-%d %H:%M:%S");
                            println!("[{ts}]: {}", msg.colored);
                            if let Err(e) = log.write_line(&msg.plain) {
                                eprintln!("Failed to write log line: {e}");
                            }
                        }
                        delivery.ack(BasicAckOptions::default()).await?;
                    }
                    Some(Err(err)) => {
                        eprintln!("Connection error: {err}. Waiting for recovery...");
                        ch.wait_for_recovery(err).await?;
                        eprintln!("Recovered, resuming.");
                    }
                    None => return Err("Consumer stream ended unexpectedly".into()),
                }
            }
        }
    }

    Ok(())
}
