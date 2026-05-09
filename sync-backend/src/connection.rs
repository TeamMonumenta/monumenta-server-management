use anyhow::Result;
use bytes::Buf;
use log::{debug, error, info, warn};
use std::{
    collections::HashSet,
    net::SocketAddr,
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    time::SystemTime,
};
use tokio::{
    io::{AsyncReadExt, AsyncWriteExt},
    net::TcpListener,
    select,
    sync::{
        Mutex, RwLock,
        mpsc::{self, UnboundedSender},
    },
};
use tokio_util::sync::CancellationToken;

use crate::{
    events::{Event, EventBusSender},
    protocol::{BinarySerde, ProtocolPhase},
    topology::SessionId,
    util::{ArcId, CancelContext, SequenceTracker},
};

pub type ConnectionHandle = ArcId<Mutex<Connection>>;

pub struct ConnectionManager {
    cancel_ctx: CancelContext,
    connections: Arc<RwLock<HashSet<ConnectionHandle>>>,
    listener: TcpListener,
}

pub type RawPacket = Box<[u8]>;

pub struct ConnectionState {
    pub phase: ProtocolPhase,
    pub session_id: Option<SessionId>,
    pub ping_tracker: SequenceTracker<SystemTime>,
}

impl ConnectionState {
    pub fn new(cancel_ctx: CancelContext) -> Self {
        Self {
            phase: ProtocolPhase::WaitHello,
            session_id: None,
            ping_tracker: SequenceTracker::new(cancel_ctx),
        }
    }
}

pub struct Connection {
    address: SocketAddr,
    write_channel: UnboundedSender<RawPacket>,
    close_token: CancellationToken,
    is_closed: AtomicBool,
    has_done_close: AtomicBool,
    pub state: ConnectionState,
}

impl Connection {
    pub fn address(&self) -> SocketAddr {
        self.address
    }

    pub fn close(&self) {
        self.close_token.cancel();
        self.is_closed.store(true, Ordering::SeqCst);
    }

    pub fn is_closed(&self) -> bool {
        self.is_closed.load(Ordering::SeqCst)
    }

    pub fn send<T: BinarySerde>(&self, packet: T) {
        let mut buf = Vec::new();
        packet.write(&mut buf);

        if self.is_closed() {
            warn!("attempted to send packet after connection was marked as closed");
            return;
        }

        if self.write_channel.send(buf.into_boxed_slice()).is_err() {
            warn!("failed to send packet because write queue was closed");
        }
    }
}

// TODO: fire events on error
impl ConnectionManager {
    pub async fn bind(cancel_ctx: CancelContext, listen: &str) -> Result<Self> {
        Ok(ConnectionManager {
            cancel_ctx,
            connections: Arc::new(RwLock::new(HashSet::new())),
            listener: TcpListener::bind(listen).await?,
        })
    }

    pub async fn start(&mut self, bus: EventBusSender) -> Result<()> {
        while let Some((stream, address)) = select! {
            biased;

            _ = self.cancel_ctx.token.cancelled() => None,
            v = self.listener.accept() => Some(v?),
        } {
            info!(remote:% = address; "new client connected from {}", address);

            stream.set_nodelay(true)?;

            let (mut stream_read, mut stream_write) = stream.into_split();
            let (write_send, mut write_recv) = mpsc::unbounded_channel();

            let child_cancel_token = self.cancel_ctx.token.child_token();

            let con_ref = ArcId::new(Mutex::new(Connection {
                address,
                write_channel: write_send,
                close_token: child_cancel_token.clone(),
                is_closed: AtomicBool::new(false),
                has_done_close: AtomicBool::new(false),
                state: ConnectionState::new(self.cancel_ctx.clone()),
            }));

            if let Err(err) = bus.send(Event::Connect(con_ref.clone())) {
                error!("failed to send Event::Connect to bus: {err}");
            }

            // spawn the read and write tasks

            let cancel = {
                let child_cancel = child_cancel_token.clone();
                let con_ref = con_ref.clone();
                let connections = self.connections.clone();
                let bus = bus.clone();
                async move || {
                    let con = con_ref.lock().await;

                    if !con.has_done_close.swap(true, Ordering::SeqCst) {
                        info!(remote:% = address; "closing channel");

                        if let Err(err) = bus.send(Event::Disconnect(con_ref.clone())) {
                            error!("failed to send Event::Disconnect to bus: {err}");
                        }

                        connections.write().await.remove(&con_ref);
                        con.is_closed.store(true, Ordering::SeqCst);
                        child_cancel.cancel();
                    }
                }
            };

            {
                let token = child_cancel_token.clone();
                let cancel = cancel.clone();
                let bus = bus.clone();
                let con_ref = con_ref.clone();
                self.cancel_ctx.tracker.spawn(async move {
                    loop {
                        let len = select! {
                            biased;

                            _ = token.cancelled() => break,
                            v = stream_read.read_u32() => v,
                        };

                        let len = match len {
                            Ok(x) => x,
                            Err(err) => {
                                error!(remote:% = address; "failed to read packet header: {err}");
                                break;
                            }
                        };

                        debug!(remote:% = address; "read packet header; len = {len}");

                        let mut buf = vec![0; len.try_into().unwrap()];

                        if let Err(err) = select! {
                            biased;

                            _ = token.cancelled() => break,
                            v = stream_read.read_exact(&mut buf) => v,
                        } {
                            error!(remote:% = address; "failed to read packet contents: {err}");
                            break;
                        }

                        if let Err(err) = bus.send(Event::IncomingPacket(
                            con_ref.clone(),
                            buf.into_boxed_slice(),
                        )) {
                            error!(remote:% = address; "failed to queue packet: {err}");
                            break;
                        }
                    }

                    cancel().await;
                });
            }

            {
                let cancel = cancel.clone();
                let token = child_cancel_token.clone();
                self.cancel_ctx.tracker.spawn(async move {
                    loop {
                        let packet = select! {
                            biased;

                            _ = token.cancelled() => break,
                            v = write_recv.recv() => v,
                        };

                        let Some(packet) = packet else {
                            break;
                        };

                        let len = &packet.len().to_ne_bytes()[..];
                        let mut buf = Buf::chain(len, &*packet);

                        select! {
                            biased;

                            _ = token.cancelled() => break,
                            _ = stream_write.write_all_buf(&mut buf) => {},
                        }
                    }

                    cancel().await;
                });
            }

            self.connections.write().await.insert(con_ref);
        }

        Ok(())
    }
}
