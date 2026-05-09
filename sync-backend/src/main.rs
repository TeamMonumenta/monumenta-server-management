#![feature(map_try_insert)]
#![feature(nonpoison_mutex)]
#![feature(sync_nonpoison)]

use std::{
    sync::{Arc, RwLock},
    time::{SystemTime, UNIX_EPOCH},
};

use anyhow::Result;
use log::{debug, error, info, warn};
use serde::Deserialize;
use tokio::{fs::read, sync::mpsc::unbounded_channel};

use crate::{
    connection::{Connection, ConnectionHandle, ConnectionManager},
    events::{Event, EventBus},
    player::OnlinePlayerTracker,
    protocol::{
        BinarySerde, CommonInHelloPacket, CommonOutHelloPacket, ProtocolPhase, ProxyInPacket,
        ProxyOutPacket,
    },
    topology::{Node, SessionId, Topology},
    util::{CancelContext, poll_signals},
};

mod connection;
mod events;
mod player;
mod protocol;
mod topology;
mod util;

pub const VERSION: &str = concat!(env!("CARGO_PKG_VERSION"), "-", env!("GIT_VERSION"));

#[derive(Deserialize)]
struct Config {
    bind_addr: String,
    postgres_addr: String,
    upstream: Option<String>,
}

pub struct Server {
    config: RwLock<Config>,
    is_root: bool,
    session_id: SessionId,
    topology: Topology,
    cancel_ctx: CancelContext,
    players: OnlinePlayerTracker,
}

impl Server {
    fn disconnect(connection: &Connection, reason: String) {
        match connection.state.phase {
            ProtocolPhase::WaitHello => {
                connection.send(CommonOutHelloPacket::Error { reason });
                connection.close();
            }
            ProtocolPhase::ProxyRunning => {
                connection.send(ProxyOutPacket::Disconnect { reason });
                connection.close();
            }
            ProtocolPhase::ShardRunning => todo!(),
            ProtocolPhase::ChildRunning => todo!(),
        }
    }

    fn handle_hello_packet(&mut self, connection: &mut Connection, packet: CommonInHelloPacket) {
        let phase = connection.state.phase;

        debug!(
            remote:% = connection.address(), phase:% = phase;
            "got hello: {:?}",
            packet
        );

        // TODO: notify upstream of topology changes, if relevant
        // this is an entirely different codepath but we can leave it unimplemented for
        // now
        assert!(!self.is_root);

        // update the topology
        let node = Node::from_packet(&packet, self.session_id);

        let session_id = node.session_id();

        if !self.topology.add_node(node) {
            error!(remote:% = connection.address(), phase:% = phase; "failed getting unique session id, this is unexpected");
            Self::disconnect(
                connection,
                "failed to add node: duplicate session identifier, please retry".into(),
            );
            return;
        }

        connection.send(CommonOutHelloPacket::Ok {
            session_id,
            server_session_id: self.session_id,
            server_identifier: format!("{} (rs) {VERSION}", env!("CARGO_PKG_NAME")),
            topology: self.topology.as_map(),
        });

        // move on to the next state
        connection.state.phase = packet.to_protocol_phase();
        connection.state.session_id = Some(session_id);
    }

    fn handle_proxy_packet(&mut self, connection: &mut Connection, packet: ProxyInPacket) {
        let phase = connection.state.phase;

        match packet {
            ProxyInPacket::Disconnect => {
                // just kill the connection, there's nothing we can't handle on the disconnect
                // message
                info!(remote:% = connection.address(), phase:% = phase; "client disconnected");
                connection.close();
            }
            ProxyInPacket::Pong { seq } => {
                debug!(remote:% = connection.address(), phase:% = phase; "got ping packet {seq}");
                connection
                    .state
                    .ping_tracker
                    .complete(seq, SystemTime::now());
            }
            ProxyInPacket::RequestPlayerJoin {
                seq,
                uuid,
                name,
                acceptable_lock_timeout,
            } => {
                let Some(proxy_id) = connection.state.session_id else {
                    error!(
                        remote:% = connection.address(), phase:% = phase;
                        "illegal state: got RequestPlayerJoin packet but session id was not assigned?"
                    );
                    Self::disconnect(connection, "internal state error".to_string());
                    return;
                };

                // check if the player is online, else wait timeout...
                // TODO: implement this logic it seems annoying so -w-
                match self.players.proxy_connect(uuid, name, proxy_id) {
                    Ok(res) => {
                        // always accept the player
                        // TODO: we can handle bans here maybe?
                        connection.send(ProxyOutPacket::PlayerJoinResponse {
                            seq,
                            accept: true,
                            target: SessionId::generate(),
                        });
                    }
                    Err(x) => todo!(),
                }
            }
            ProxyInPacket::PlayerDisconnect { uuid } => {
                let Some(proxy_id) = connection.state.session_id else {
                    error!(
                        remote:% = connection.address(), phase:% = phase;
                        "illegal state: got PlayerDisconnect packet but session id was not assigned?"
                    );
                    Self::disconnect(connection, "internal state error".to_string());
                    return;
                };

                let _ = self.players.proxy_disconnect(uuid, proxy_id);
                // TODO: handle error
                // let _ = self.players.on_proxy_disconnect(uuid);
                // TODO: handle some shit
            }
        }
    }

    async fn handle_packet(&mut self, handle: ConnectionHandle, packet: &[u8]) {
        let mut connection = handle.lock().await;
        let phase = connection.state.phase;

        match phase {
            ProtocolPhase::WaitHello => match CommonInHelloPacket::read_packet(packet) {
                Ok(packet) => self.handle_hello_packet(&mut connection, packet),
                Err(err) => {
                    warn!(remote:% = connection.address(), phase:% = phase; "failed to parse hello packet: {err}");
                    Self::disconnect(&connection, err.to_string());
                }
            },
            ProtocolPhase::ProxyRunning => match ProxyInPacket::read_packet(packet) {
                Ok(packet) => self.handle_proxy_packet(&mut connection, packet),
                Err(err) => {
                    warn!(remote:% = connection.address(), phase:% = phase; "failed to parse proxy packet: {err}");
                    Self::disconnect(&connection, err.to_string());
                }
            },
            ProtocolPhase::ShardRunning => todo!(),
            ProtocolPhase::ChildRunning => todo!(),
        }
    }

    async fn main_loop(&mut self, bus: &mut EventBus) {
        while let Some(event) = bus.recv().await {
            match event {
                Event::StopServer => break,
                Event::ReloadConfig => {}
                Event::Connect(connection) => {
                    info!("connected: {:?}", Arc::as_ptr(connection.get_arc()))
                }
                Event::Disconnect(connection) => {
                    info!("disconnected: {:?}", Arc::as_ptr(connection.get_arc()))
                }
                Event::IncomingPacket(connection, packet) => {
                    self.handle_packet(connection, &packet).await;
                }
            }
        }
    }
}

#[tokio::main]
async fn main() -> Result<()> {
    log4rs::init_file("log4rs.yml", Default::default())?;
    let config: Config = toml::from_str(str::from_utf8(&read("Config.toml").await?)?)?;

    let start_time = SystemTime::now().duration_since(UNIX_EPOCH)?.as_millis();

    let session_id = SessionId::generate();
    info!(
        "starting server {} v{VERSION}: start_time={start_time}, session_id={session_id}",
        env!("CARGO_PKG_NAME")
    );

    match config.upstream {
        Some(upstream) => {
            info!("upstream is set in config, connecting to {upstream}...");
            todo!();
        }
        None => {
            info!("upstream not set in config, running root server")
        }
    };

    /*
    let pool = PgPoolOptions::new()
        .max_connections(5)
        .connect(&config.postgres_addr)
        .await?;

    info!("connected to postgres at {}", config.postgres_addr);*/

    let cancel_ctx = CancelContext::new();
    let (bus_sender, mut bus) = unbounded_channel();

    let mut handler = ConnectionManager::bind(cancel_ctx.clone(), &config.bind_addr).await?;

    // TODO: move some of this logic into Server

    // spawn the server
    {
        let token = cancel_ctx.token_copy();
        let bus_sender = bus_sender.clone();
        let addr = config.bind_addr.clone();
        cancel_ctx.tracker.spawn(async move {
            info!("listening on {}", addr);
            if let Err(x) = handler.start(bus_sender).await {
                error!("socket server failed with: {x}");
                token.cancel();
            }
        });
    }

    poll_signals(bus_sender.clone(), cancel_ctx.clone())?;

    // main loop
    let is_root = config.upstream.is_none();

    let mut server = Server {
        config: RwLock::new(config),
        is_root,
        session_id,
        topology: Topology::root(session_id),
        cancel_ctx: cancel_ctx.clone(),
        players: OnlinePlayerTracker::new(bus_sender),
    };

    server.main_loop(&mut bus).await;

    cancel_ctx.stop().await;

    info!("server stopped");

    Ok(())
}
