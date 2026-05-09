mod serde;

pub use serde::*;

use std::{collections::HashMap, time::Duration};

use derive_more::Display;
use proc_macros::BinarySerde;

use crate::topology::{Node, SessionId};

#[derive(Clone, Copy, Display, Debug)]
pub enum ProtocolPhase {
    WaitHello,
    ProxyRunning,
    ShardRunning,
    ChildRunning,
}

#[derive(BinarySerde, Debug)]
pub enum HelloData {
    Child,
    Proxy,
    Shard,
}

#[derive(BinarySerde, Debug)]
pub struct CommonInHelloPacket {
    pub start_time: u64,
    pub client_identifier: String,
    pub name: String,
    pub k8_data: Option<(String, String)>,
    pub data: HelloData,
}

#[derive(BinarySerde)]
pub enum CommonOutHelloPacket {
    Ok {
        // id of the node
        session_id: SessionId,
        // id of the server
        server_session_id: SessionId,
        // version identifier
        server_identifier: String,
        // topology info,
        topology: HashMap<SessionId, Node>,
    },
    Error {
        reason: String,
    },
}

impl CommonInHelloPacket {
    pub fn to_protocol_phase(&self) -> ProtocolPhase {
        match self.data {
            HelloData::Child => ProtocolPhase::ChildRunning,
            HelloData::Proxy => ProtocolPhase::ProxyRunning,
            HelloData::Shard => ProtocolPhase::ShardRunning,
        }
    }
}

#[derive(BinarySerde)]
pub enum ProxyInPacket {
    Disconnect,
    Pong {
        seq: u64,
    },
    RequestPlayerJoin {
        seq: u64,
        uuid: u128,
        name: String,
        acceptable_lock_timeout: Duration,
    },
    PlayerDisconnect {
        uuid: u128,
    },
}

#[derive(BinarySerde)]
pub enum ProxyOutPacket {
    Ping {
        seq: u64,
    },
    Disconnect {
        reason: String,
    },
    PlayerJoinResponse {
        seq: u64,
        accept: bool,
        target: SessionId,
    },
}

#[derive(BinarySerde)]
pub struct PlayerSaveData {
    pub scores: HashMap<String, i32>,
    pub nbt: Box<[u8]>,
    pub advancement: String,
    pub plugin: HashMap<String, Box<[u8]>>,
}

#[derive(BinarySerde)]
pub enum ShardInPacket {
    // common packets
    // sadly rust doesn't support extending enums
    Disconnect,
    Pong {
        seq: u64,
    },

    // -- player data packets --
    // these follow the typical login sequence of a player
    // they join the server, play for a while, and then disconnect
    RequestPlayerJoin {
        seq: u64,
        uuid: u128,
        name: String,
        acceptable_lock_timeout: Duration,
    },

    // player data update
    UpdatePlayerData {
        scores: Option<HashMap<String, i32>>,
        nbt: Option<Box<[u8]>>,
        advancement: Option<String>,
        plugin: Option<HashMap<String, Box<[u8]>>>,
    },
    // requests for a history push
    PushHistory {
        reason: String,
    },
    // requests a transfer to a shard
    TransferShardRequest {
        shard: SessionId,
    },
    ChangeProfileRequest {},
    LoadHistoryRequest {},
    PlayerDisconnect {
        uuid: u128,
    },
}

#[derive(BinarySerde)]
pub enum ShardOutPacket {
    Ping {
        seq: u64,
    },
    Disconnect {
        reason: String,
    },
    PlayerJoinResponse {
        seq: u64,
        data: Option<PlayerSaveData>,
    },
}
