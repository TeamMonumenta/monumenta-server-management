package com.playmonumenta.redissync.adapters;

import ca.spottedleaf.dataconverter.minecraft.MCDataConverter;
import ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry;
import com.google.gson.JsonObject;
import com.playmonumenta.papermixins.paperapi.v1.RedisSyncIO;
import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadableNBT;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.world.scores.Scoreboard;
import org.apache.logging.log4j.Logger;
import org.bukkit.craftbukkit.scoreboard.CraftScoreboard;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public class VersionAdapter_26_1_2 implements VersionAdapter {
	/**
	 * Creates the version adapter.
	 *
	 * @param logger The logger to use
	 */
	@SuppressWarnings("unused")
	public VersionAdapter_26_1_2(Logger logger) {
	}

	@Override
	public JsonObject getPlayerScoresAsJson(String playerName, org.bukkit.scoreboard.Scoreboard scoreboard) {
		return RedisSyncIO.getInstance().getPlayerScoresAsJson(playerName, scoreboard);
	}

	@Override
	public void resetPlayerScores(String playerName, org.bukkit.scoreboard.Scoreboard scoreboard) {
		Scoreboard nmsScoreboard = ((CraftScoreboard) scoreboard).getHandle();
		nmsScoreboard.resetAllPlayerScores(() -> playerName);
	}

	private static final Set<String> SHARD_FIELDS = Set.of(
		"respawn",
		"abilities",
		"FallFlying",
		"fall_distance",
		"OnGround",
		"Dimension",
		"world",
		"WorldUUIDMost",
		"WorldUUIDLeast",
		"Pos",
		"Motion",
		"Rotation",
		"Paper.Origin",
		"entered_nether_pos"
	);

	@Override
	public Object retrieveSaveData(byte[] data, ReadableNBT shardData) throws IOException {
		CompoundTag nbt = NbtIo.readCompressed(new ByteArrayInputStream(data), NbtAccounter.unlimitedHeap());
		NBT.wrapNMSTag(nbt).mergeCompound(shardData);
		return nbt;
	}

	@Override
	public VersionAdapter.SaveData extractSaveData(Object nbtObj, @Nullable VersionAdapter.ReturnParams returnParams) throws IOException {
		CompoundTag nbt = (CompoundTag) nbtObj;

		CompoundTag shard = new CompoundTag();
		for (String key : SHARD_FIELDS) {
			Tag tag = nbt.get(key);
			if (tag != null) {
				shard.put(key, tag);
				nbt.remove(key);
			}
		}

		if (returnParams != null && returnParams.mReturnLoc != null) {
			ListTag pos = new ListTag();
			pos.add(DoubleTag.valueOf(returnParams.mReturnLoc.getX()));
			pos.add(DoubleTag.valueOf(returnParams.mReturnLoc.getY()));
			pos.add(DoubleTag.valueOf(returnParams.mReturnLoc.getZ()));
			shard.put("Pos", pos);
		}

		if (returnParams != null && returnParams.mReturnPitch != null && returnParams.mReturnYaw != null) {
			ListTag rotation = new ListTag();
			rotation.add(FloatTag.valueOf(returnParams.mReturnYaw));
			rotation.add(FloatTag.valueOf(returnParams.mReturnPitch));
			shard.put("Rotation", rotation);
		}

		ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
		NbtIo.writeCompressed(nbt, outBytes);
		return new VersionAdapter.SaveData(outBytes.toByteArray(), NBT.wrapNMSTag(shard));
	}

	@Override
	public void savePlayer(Player player) {
		RedisSyncIO.getInstance().savePlayer(player);
	}

	@Override
	public Object upgradePlayerData(Object nbtCompoundTag) {
		CompoundTag nbt = (CompoundTag) nbtCompoundTag;
		int i = nbt.getIntOr("DataVersion", -1);
		nbt = MCDataConverter.convertTag(MCTypeRegistry.PLAYER, nbt, i,
			SharedConstants.getCurrentVersion().dataVersion().version());
		return nbt;
	}

	@Override
	public String upgradePlayerAdvancements(String advancementsStr) throws Exception {
		return RedisSyncIO.getInstance().upgradePlayerAdvancements(advancementsStr);
	}
}
