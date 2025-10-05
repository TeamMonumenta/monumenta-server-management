package com.playmonumenta.limbo.protocol.impl;

import com.playmonumenta.limbo.Connection;
import com.playmonumenta.limbo.ConnectionImpl;
import com.playmonumenta.limbo.network.PacketReader;
import com.playmonumenta.limbo.network.PacketWriter;
import com.playmonumenta.limbo.protocol.ProtocolHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class Post1_20_5 implements ProtocolHandler {
    public record Data(int loginId, Map<String, List<String>> registries) {
        public Data loginId(int id) {
            return new Data(id, registries);
        }

        public Data registry(String name, String... values) {
            final var copy = registries.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                x -> (List<String>) new ArrayList<>(x.getValue()))
            );

            copy.computeIfAbsent(name, s -> new ArrayList<>()).addAll(List.of(values));
            return new Data(loginId, copy);
        }
    }

    private static final int CLIENTBOUND_CONFIG_FINISHED_ID = 3;
    private static final int CLIENTBOUND_CONFIG_KNOWN_PACKS_ID = 14;
    private static final int CLIENTBOUND_CONFIG_SYNC_REG_ID = 7;
    private static final int SERVERBOUND_CONFIG_KNOWN_PACKS_ID = 7;
    private static final int SERVERBOUND_CONFIG_ACK_ID = 3;

    private static final List<String> KNOWN_PACKS = List.of(
        "1.20.5", "1.20.6", "1.21", "1.21.1", "1.21.2",
        "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7",
        "1.21.8"
    );

    public static final Data V1_20_5_DATA = new Data(43, Map.of())
        .registry(
            "damage_type",
            "in_fire", "lightning_bolt", "on_fire", "lava", "hot_floor", "in_wall", "cramming", "drown", "starve",
            "cactus", "fall", "fly_into_wall", "out_of_world", "generic", "magic", "wither", "dragon_breath", "dry_out",
            "sweet_berry_bush", "freeze", "stalagmite", "outside_border", "generic_kill"
        )
        .registry("worldgen/biome", "plains");

    public static final Data V1_21_DATA = V1_20_5_DATA
        .registry("painting_variant", "alban")
        .registry("wolf_variant", "ashen")
        .registry("damage_type", "campfire")
        .registry("minecraft:worldgen/biome", "minecraft:snowy_taiga");

    private final Data data;

    protected void writeCommonRespawnData(PacketWriter writer) {
        writer.varInt(0)
            .str("overworld")
            .i64(0)
            .i8((byte) 3).i8((byte) -1)
            .flag(false).flag(false).flag(false)
            .varInt(0);
    }

    protected void writeLoginPacket(PacketWriter writer) {
        writer.varInt(data.loginId)
            .i32(0).flag(false)
            .varInt(1).str("overworld")
            .varInt(0).varInt(1).varInt(1)
            .flag(false).flag(true).flag(false);
        writeCommonRespawnData(writer);
        writer.flag(false);
    }

    protected final ConnectionImpl connection;

    public Post1_20_5(Data data, ConnectionImpl connection) {
        this.data = data;
        this.connection = connection;
    }

    @Override
    public final void onLoginComplete() {
        connection.write(writer -> {
            writer.varInt(CLIENTBOUND_CONFIG_KNOWN_PACKS_ID);
            writer.varInt(KNOWN_PACKS.size());
            for (final var s : KNOWN_PACKS) {
                writer.str("minecraft");
                writer.str("core");
                writer.str(s);
            }
        });

        connection.write(writer -> {
            writer.varInt(CLIENTBOUND_CONFIG_SYNC_REG_ID);
            writer.str("dimension_type");
            writer.varInt(1);
            writer.str("overworld");
            writer.flag(false);
        });

        data.registries().forEach((reg, entries) -> connection.write(writer -> {
            writer.varInt(CLIENTBOUND_CONFIG_SYNC_REG_ID);
            writer.str(reg);
            writer.varInt(entries.size());
            for (final var entry : entries) {
                writer.str(entry);
                writer.flag(false);
            }
        }));

        connection.phase(Connection.Phase.CONFIGURATION);
    }

    @Override
    public final void handleConfigurationPacket(int id, PacketReader reader) {
        if (id == SERVERBOUND_CONFIG_KNOWN_PACKS_ID) {
            connection.write(CLIENTBOUND_CONFIG_FINISHED_ID);
        }

        if (id == SERVERBOUND_CONFIG_ACK_ID) {
            connection.phase(Connection.Phase.PLAY);
            connection.write(this::writeLoginPacket);
        }
    }

    @Override
    public final void handlePlayPacket(int id, PacketReader reader) {

    }

    @Override
    public void sendKeepAlive() {

    }
}
