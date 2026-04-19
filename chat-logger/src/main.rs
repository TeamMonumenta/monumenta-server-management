use chrono::Local;
use lapin::{
    options::*,
    types::{AMQPValue, FieldTable},
    Connection, ConnectionProperties,
};
use serde_json::Value;
use std::{
    env,
    fs::{File, OpenOptions},
    io::{BufWriter, Write},
    path::{Path, PathBuf},
};
use tokio_stream::StreamExt;

const CHAT_CHANNEL: &str = "com.playmonumenta.networkchat.Message";
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
// Adventure Component → plain text
// ---------------------------------------------------------------------------

fn plain_text(v: &Value) -> String {
    match v {
        // Gson shorthand: a bare JSON string is a text component
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
// Message parsing
// ---------------------------------------------------------------------------

fn format_message(body: &[u8]) -> Option<String> {
    let root: Value = serde_json::from_slice(body).ok()?;
    if root.get("channel")?.as_str()? != CHAT_CHANNEL {
        return None;
    }
    let source = root.get("source").and_then(Value::as_str).unwrap_or("?");
    let data = root.get("data")?;
    let sender = data.get("senderName").and_then(Value::as_str).unwrap_or("?");
    let message = data.get("message").map(plain_text).unwrap_or_default();
    Some(format!("[{source}] {sender}: {message}"))
}

// ---------------------------------------------------------------------------
// Entry point
// ---------------------------------------------------------------------------

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let uri = env::var("AMQP_URI").unwrap_or_else(|_| "amqp://guest:guest@127.0.0.1:5672".into());
    let queue = env::var("SHARD_NAME").unwrap_or_else(|_| "chat-logger".into());

    let mut log = match RollingLog::open(Path::new("/logs")) {
        Ok(l) => l,
        Err(e) => {
            eprintln!("Failed to open log dir '/logs': {e}");
            std::process::exit(1);
        }
    };

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
                if let Some(line) = format_message(&delivery.data) {
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
