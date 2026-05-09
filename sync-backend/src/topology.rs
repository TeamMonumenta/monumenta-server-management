// management of "leafs"

use std::{collections::HashMap, num::NonZeroU64};

use derive_more::{Debug, Display};
use proc_macros::BinarySerde;
use rand::Rng;

use crate::protocol::{CommonInHelloPacket, HelloData};

#[derive(Clone, Copy, Display, Debug, BinarySerde, PartialEq, Eq, Hash)]
#[display("{_0:x}")]
#[debug("SessionId({_0:x})")]
pub struct SessionId(u64);

impl SessionId {
    pub fn generate() -> SessionId {
        let mut rng = rand::rng();
        SessionId(rng.random::<NonZeroU64>().into())
    }
}

#[derive(BinarySerde, Clone)]
pub struct CommonNodeData {
    session_id: SessionId,
    name: String,
    start_time: u64,
}

// represents a node in the topology of things
#[derive(BinarySerde, Clone)]
pub enum Node {
    Proxy {
        common: CommonNodeData,
        parent: SessionId,
    },
    Shard {
        common: CommonNodeData,
        parent: SessionId,
    },
    Sync {
        common: CommonNodeData,
        parent: SessionId,
    },
    Root {
        common: CommonNodeData,
    },
}

impl Node {
    pub fn from_packet(packet: &CommonInHelloPacket, self_session_id: SessionId) -> Node {
        let common = CommonNodeData {
            session_id: SessionId::generate(),
            name: packet.name.clone(),
            start_time: packet.start_time,
        };

        match packet.data {
            HelloData::Child => Node::Sync {
                common,
                parent: self_session_id,
            },
            HelloData::Proxy => Node::Proxy {
                common,
                parent: self_session_id,
            },
            HelloData::Shard => Node::Shard {
                common,
                parent: self_session_id,
            },
        }
    }

    fn common_data(&self) -> &CommonNodeData {
        match self {
            Node::Proxy { common, parent: _ } => common,
            Node::Shard { common, parent: _ } => common,
            Node::Sync { common, parent: _ } => common,
            Node::Root { common } => common,
        }
    }

    pub fn session_id(&self) -> SessionId {
        self.common_data().session_id
    }

    pub fn parent(&self) -> Option<&SessionId> {
        match self {
            Node::Proxy { common: _, parent } => Some(parent),
            Node::Shard { common: _, parent } => Some(parent),
            Node::Sync { common: _, parent } => Some(parent),
            Node::Root { common: _ } => None,
        }
    }
}

// The main "topology" of the network. The topology is controlled by the root instance.
pub struct Topology {
    root_id: SessionId,
    nodes: HashMap<SessionId, Node>,
}

impl Topology {
    pub fn add_node(&mut self, node: Node) -> bool {
        self.nodes
            .try_insert(node.common_data().session_id, node)
            .is_ok()
    }

    pub fn remove_node() {}

    pub fn as_map(&self) -> HashMap<SessionId, Node> {
        self.nodes.clone()
    }

    pub fn root(id: SessionId) -> Topology {
        Topology {
            root_id: id,
            nodes: HashMap::new(),
        }
    }
}
