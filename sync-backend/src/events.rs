use tokio::sync::mpsc::{UnboundedReceiver, UnboundedSender};

use crate::connection::{ConnectionHandle, RawPacket};

pub enum Event {
    StopServer,
    ReloadConfig,
    Connect(ConnectionHandle),
    Disconnect(ConnectionHandle),
    IncomingPacket(ConnectionHandle, RawPacket),
}

pub type EventBusSender = UnboundedSender<Event>;
pub type EventBus = UnboundedReceiver<Event>;
