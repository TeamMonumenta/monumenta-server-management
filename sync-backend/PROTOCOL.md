# New XFer Protocol

This is an informal specification of the new transfer protocol for multi-shard, multi-proxy, multi-region synchronization.

The main component of this protocol is the synchronizers, which are daemon processes. One such synchronizer is designated the root.

## Components

There are two "Leaf" components defined:
- Proxy (velocity proxy instances)
- Shards (minecraft server instances)

## Protocol

### Common Leaf <-> Sync

Packets:
- Syncbound Keep Alive
- Leafbound Ping
- Syncbound Pong

### Proxy <-> Regional

Packets:
- Syncbound Player Join
- Proxybound Player Join Ack
- Syncbound Player Leave
- Proxybound Player Change Shard

### Shard <-> Regional

Packets:
- Syncbound Player Join Request
- Shardbound Player Join Response
- Syncbound Player Data Save Packet
- Syncbound Player Reload Request
- Syncbound Player Transfer Request
- Syncbound Player Leave

### How Do I Do A Thing?

#### Proxy Login Flow

- Send (proxy) syncbound player join
- Wait for proxybound player join ack
- Send player to shard

#### Shard Login Flow

- Send (shard) syncbound player join request
- Wait for shardbound player join response


