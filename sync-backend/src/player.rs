use std::collections::HashMap;

use crate::{events::EventBusSender, topology::SessionId};

// a struct to manage player data
// the player data model is basically git:
// - you have the current working dir, which is modifiable
// - you can have commits ("history" entries)
// - you can have branches ("profiles")
//
// there are several operations:
// - "switch profiles" - checkout to branch
// - "load history entry" - reset branch to commit
// - "load history temporarily" - check out branch and enter detached head state
//
// history is preserved for {n} entries
//
// playerdata-database interactions only occur on the *root* sync node, the child sync instances
// don't control playerdata (only caches)
pub struct PlayerData {}

pub struct OnlinePlayer {
    uuid: u128,
    name: String,
    shard: Option<SessionId>,
    proxy: Option<SessionId>,
}

// set of players online for *this* node in particular.
pub struct OnlinePlayerTracker {
    players: HashMap<u128, OnlinePlayer>,
    bus_sender: EventBusSender,
}

impl OnlinePlayerTracker {
    pub fn new(sender: EventBusSender) -> Self {
        Self {
            players: HashMap::new(),
            bus_sender: sender,
        }
    }

    pub fn proxy_connect(
        &mut self,
        uuid: u128,
        name: String,
        proxy_id: SessionId,
    ) -> Result<&OnlinePlayer, ()> {
        let entry = self.players.entry(uuid).or_insert(OnlinePlayer {
            uuid,
            name,
            shard: None,
            proxy: Some(proxy_id),
        });

        if entry.proxy.is_some() {
            return Err(());
        }

        Ok(entry)
    }

    // TODO: give error codes here
    pub fn proxy_disconnect(&mut self, uuid: u128, proxy_id: SessionId) -> Result<(), ()> {
        let Some(player) = self.players.get_mut(&uuid) else {
            return Err(());
        };

        let Some(id) = player.proxy.take() else {
            return Err(());
        };

        if id != proxy_id {
            return Err(());
        }

        Ok(())
    }
}
