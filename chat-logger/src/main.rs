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
use tokio_stream::StreamExt;

const CHAT_CHANNEL: &str = "com.playmonumenta.networkchat.Message";
const UUID2NAME_KEY: &str = "uuid2name";
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
// UUID → player name cache backed by Redis
// ---------------------------------------------------------------------------

struct UuidCache {
    map: HashMap<String, String>,
    conn: Option<redis::aio::MultiplexedConnection>,
}

impl UuidCache {
    async fn new(uri: Option<&str>) -> Self {
        let Some(uri) = uri else {
            return Self {
                map: HashMap::new(),
                conn: None,
            };
        };

        let client = match redis::Client::open(uri) {
            Ok(c) => c,
            Err(e) => {
                eprintln!("Redis: invalid URI: {e}. UUID lookups will fall back to raw UUIDs.");
                return Self {
                    map: HashMap::new(),
                    conn: None,
                };
            }
        };

        let mut conn = match client.get_multiplexed_async_connection().await {
            Ok(c) => c,
            Err(e) => {
                eprintln!("Redis: connection failed: {e}. UUID lookups will fall back to raw UUIDs.");
                return Self {
                    map: HashMap::new(),
                    conn: None,
                };
            }
        };

        let map: HashMap<String, String> = conn
            .hgetall(UUID2NAME_KEY)
            .await
            .unwrap_or_default();
        eprintln!("Redis: loaded {} UUID→name mappings from {UUID2NAME_KEY}", map.len());

        Self {
            map,
            conn: Some(conn),
        }
    }

    async fn lookup(&mut self, uuid: &str) -> String {
        if let Some(name) = self.map.get(uuid) {
            return name.clone();
        }
        if let Some(conn) = &mut self.conn {
            let result: redis::RedisResult<Option<String>> = conn.hget(UUID2NAME_KEY, uuid).await;
            if let Ok(Some(name)) = result {
                self.map.insert(uuid.to_string(), name.clone());
                return name;
            }
        }
        uuid.to_string()
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
// Message parsing and formatting
// ---------------------------------------------------------------------------

async fn format_message(body: &[u8], cache: &mut UuidCache, debug: bool) -> Option<String> {
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

    let line = match data.get("extra") {
        Some(Value::Object(extra)) => {
            if let Some(receiver_uuid) = extra.get("receiver").and_then(Value::as_str) {
                // Whisper / DM
                let receiver = cache.lookup(receiver_uuid).await;
                format!("<whisper> {sender} → {receiver} » {message}")
            } else if extra.contains_key("fromWorld") {
                // World channel — includes shard and world name
                let shard = extra.get("fromShard").and_then(Value::as_str).unwrap_or("?");
                format!("[{shard}] <wc> {sender} » {message}")
            } else if let Some(shard) = extra.get("fromShard").and_then(Value::as_str) {
                // Local channel
                format!("[{shard}] <l> {sender} » {message}")
            } else if let Some(team) = extra.get("team").and_then(Value::as_str) {
                // Team channel
                format!("<team:{team}> {sender} » {message}")
            } else {
                // Unknown extra structure; treat as global
                format!("<g> {sender} » {message}")
            }
        }
        // No extra: global, party, or announcement — indistinguishable from message data alone
        _ => format!("<g> {sender} » {message}"),
    };

    Some(line)
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let uri = env::var("AMQP_URI").unwrap_or_else(|_| "amqp://guest:guest@127.0.0.1:5672".into());
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

    let mut cache = UuidCache::new(redis_uri.as_deref()).await;

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

    loop {
        match consumer.next().await {
            Some(Ok(delivery)) => {
                if let Some(line) = format_message(&delivery.data, &mut cache, debug).await {
                    let now = Local::now();
                    let ts = now.format("%Y-%m-%d %H:%M:%S");
                    println!("[{ts}]: {line}");
                    if let Err(e) = log.write_line(&line) {
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
