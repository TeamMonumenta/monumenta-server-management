package com.playmonumenta.redissync.adapters;

import ca.spottedleaf.dataconverter.minecraft.MCDataConverter;
import ca.spottedleaf.dataconverter.minecraft.datatypes.MCTypeRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.mixinapi.v1.RedisSyncIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.scores.Scoreboard;
import org.bukkit.craftbukkit.v1_20_R3.scoreboard.CraftScoreboard;
import org.bukkit.entity.Player;

public class VersionAdapter_v1_20_R3 implements VersionAdapter {
	/**
	 * Creates the version adapter.
	 *
	 * @param logger The logger to use
	 */
	@SuppressWarnings("unused")
	public VersionAdapter_v1_20_R3(Logger logger) {
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

	@Override
	public Object retrieveSaveData(byte[] data, JsonObject shardData) throws IOException {
		ByteArrayInputStream inBytes = new ByteArrayInputStream(data);
		CompoundTag nbt = NbtIo.readCompressed(inBytes, NbtAccounter.unlimitedHeap());

		applyInt(shardData, nbt, "SpawnX");
		applyInt(shardData, nbt, "SpawnY");
		applyInt(shardData, nbt, "SpawnZ");
		applyBool(shardData, nbt, "SpawnForced");
		applyFloat(shardData, nbt, "SpawnAngle");
		applyStr(shardData, nbt, "SpawnDimension");
		// flying is nested in the abilities structure
		if (shardData.has("flying")) {
			final CompoundTag abilities;
			if (nbt.contains("abilities")) {
				abilities = nbt.getCompound("abilities");
			} else {
				abilities = new CompoundTag();
				nbt.put("abilities", abilities);
			}
			abilities.putBoolean("flying", shardData.get("flying").getAsBoolean());
		}
		applyBool(shardData, nbt, "FallFlying");
		applyFloat(shardData, nbt, "FallDistance");
		applyBool(shardData, nbt, "OnGround");
		applyInt(shardData, nbt, "Dimension");
		applyStr(shardData, nbt, "world");
		applyLong(shardData, nbt, "WorldUUIDMost");
		applyLong(shardData, nbt, "WorldUUIDLeast");
		applyDoubleList(shardData, nbt, "Pos");
		applyDoubleList(shardData, nbt, "Motion");
		applyFloatList(shardData, nbt, "Rotation");
		applyDoubleList(shardData, nbt, "Paper.Origin");
		applyCompoundOfDoubles(shardData, nbt, "enteredNetherPosition");

		return nbt;
	}

	@Override
	public VersionAdapter.SaveData extractSaveData(Object nbtObj, @Nullable VersionAdapter.ReturnParams returnParams) throws IOException {
		CompoundTag nbt = (CompoundTag) nbtObj;

		JsonObject obj = new JsonObject();
		copyInt(obj, nbt, "SpawnX");
		copyInt(obj, nbt, "SpawnY");
		copyInt(obj, nbt, "SpawnZ");
		copyBool(obj, nbt, "SpawnForced");
		copyFloat(obj, nbt, "SpawnAngle");
		copyStr(obj, nbt, "SpawnDimension");
		// flying is nested in the abilities structure
		if (nbt.contains("abilities")) {
			CompoundTag abilities = nbt.getCompound("abilities");
			copyBool(obj, abilities, "flying");
		}
		copyBool(obj, nbt, "FallFlying");
		copyFloat(obj, nbt, "FallDistance");
		copyBool(obj, nbt, "OnGround");
		copyInt(obj, nbt, "Dimension");
		copyStr(obj, nbt, "world");
		copyLong(obj, nbt, "WorldUUIDMost");
		copyLong(obj, nbt, "WorldUUIDLeast");
		copyDoubleList(obj, nbt, "Pos");
		copyDoubleList(obj, nbt, "Motion");
		copyFloatList(obj, nbt, "Rotation");
		copyDoubleList(obj, nbt, "Paper.Origin");
		copyCompoundOfDoubles(obj, nbt, "enteredNetherPosition");

		if (returnParams != null && returnParams.mReturnLoc != null) {
			JsonArray arr = new JsonArray();
			arr.add(returnParams.mReturnLoc.getX());
			arr.add(returnParams.mReturnLoc.getY());
			arr.add(returnParams.mReturnLoc.getZ());
			obj.remove("Pos");
			obj.add("Pos", arr);
		}

		if (returnParams != null && returnParams.mReturnPitch != null && returnParams.mReturnYaw != null) {
			JsonArray arr = new JsonArray();
			arr.add(returnParams.mReturnYaw);
			arr.add(returnParams.mReturnPitch);
			obj.remove("Rotation");
			obj.add("Rotation", arr);
		}

		ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
		NbtIo.writeCompressed(nbt, outBytes);
		return new VersionAdapter.SaveData(outBytes.toByteArray(), obj.toString());
	}

	@Override
	public void savePlayer(Player player) {
		RedisSyncIO.getInstance().savePlayer(player);
	}

	@Override
	public Object upgradePlayerData(Object nbtCompoundTag) {
		CompoundTag nbt = (CompoundTag) nbtCompoundTag;
		int i = nbt.contains("DataVersion", 3) ? nbt.getInt("DataVersion") : -1;
		nbt = MCDataConverter.convertTag(MCTypeRegistry.PLAYER, nbt, i,
			SharedConstants.getCurrentVersion().getDataVersion().getVersion());
		return nbt;
	}

	@Override
	public String upgradePlayerAdvancements(String advancementsStr) throws Exception {
		return RedisSyncIO.getInstance().upgradePlayerAdvancements(advancementsStr);
	}

	protected ListTag toDoubleList(double... doubles) {
		ListTag nbtTagList = new ListTag();

		for (double d : doubles) {
			nbtTagList.add(DoubleTag.valueOf(d));
		}

		return nbtTagList;
	}

	private void applyStr(JsonObject obj, CompoundTag nbt, String key) {
		if (obj.has(key)) {
			nbt.putString(key, obj.get(key).getAsString());
		}
	}

	private void applyInt(JsonObject obj, CompoundTag nbt, String key) {
		if (obj.has(key)) {
			nbt.putInt(key, obj.get(key).getAsInt());
		}
	}

	private void applyLong(JsonObject obj, CompoundTag nbt, String key) {
		if (obj.has(key)) {
			nbt.putLong(key, obj.get(key).getAsLong());
		}
	}

	private void applyFloat(JsonObject obj, CompoundTag nbt, String key) {
		if (obj.has(key)) {
			nbt.putFloat(key, obj.get(key).getAsFloat());
		}
	}

	private void applyBool(JsonObject obj, CompoundTag nbt, String key) {
		if (obj.has(key)) {
			nbt.putBoolean(key, obj.get(key).getAsBoolean());
		}
	}

	private void applyFloatList(JsonObject obj, CompoundTag nbt, String key) {
		if (obj.has(key)) {
			JsonElement element = obj.get(key);
			if (element.isJsonArray()) {
				ListTag nbtTagList = new ListTag();
				for (JsonElement val : element.getAsJsonArray()) {
					nbtTagList.add(FloatTag.valueOf(val.getAsFloat()));
				}
				nbt.put(key, nbtTagList);
			}
		}
	}

	private void applyDoubleList(JsonObject obj, CompoundTag nbt, String key) {
		if (obj.has(key)) {
			JsonElement element = obj.get(key);
			if (element.isJsonArray()) {
				ListTag nbtTagList = new ListTag();
				for (JsonElement val : element.getAsJsonArray()) {
					nbtTagList.add(DoubleTag.valueOf(val.getAsDouble()));
				}
				nbt.put(key, nbtTagList);
			}
		}
	}

	private void applyCompoundOfDoubles(JsonObject obj, CompoundTag nbt, String key) {
		if (obj.has(key)) {
			JsonElement element = obj.get(key);
			if (element.isJsonObject()) {
				CompoundTag nbtComp = new CompoundTag();
				for (Map.Entry<String, JsonElement> subentry : element.getAsJsonObject().entrySet()) {
					nbtComp.putDouble(subentry.getKey(), subentry.getValue().getAsDouble());
				}
				nbt.put(key, nbtComp);
			}
		}
	}

	private void copyStr(JsonObject obj, CompoundTag nbt, String key) {
		if (nbt.contains(key)) {
			obj.addProperty(key, nbt.getString(key));
			nbt.remove(key);
		}
	}

	private void copyInt(JsonObject obj, CompoundTag nbt, String key) {
		if (nbt.contains(key)) {
			obj.addProperty(key, nbt.getInt(key));
			nbt.remove(key);
		}
	}

	private void copyLong(JsonObject obj, CompoundTag nbt, String key) {
		if (nbt.contains(key)) {
			obj.addProperty(key, nbt.getLong(key));
			nbt.remove(key);
		}
	}

	private void copyFloat(JsonObject obj, CompoundTag nbt, String key) {
		if (nbt.contains(key)) {
			obj.addProperty(key, nbt.getFloat(key));
			nbt.remove(key);
		}
	}

	private void copyBool(JsonObject obj, CompoundTag nbt, String key) {
		if (nbt.contains(key)) {
			obj.addProperty(key, nbt.getBoolean(key));
			nbt.remove(key);
		}
	}

	private void copyFloatList(JsonObject obj, CompoundTag nbt, String key) {
		if (nbt.contains(key)) {
			ListTag list = nbt.getList(key, 5);  // 5 = float list
			JsonArray arr = new JsonArray();
			for (int i = 0; i < list.size(); i++) {
				arr.add(list.getFloat(i));
			}
			obj.add(key, arr);
			nbt.remove(key);
		}
	}

	private void copyDoubleList(JsonObject obj, CompoundTag nbt, String key) {
		if (nbt.contains(key)) {
			ListTag list = nbt.getList(key, 6);  // 6 = double list
			JsonArray arr = new JsonArray();
			for (int i = 0; i < list.size(); i++) {
				arr.add(list.getDouble(i));
			}
			obj.add(key, arr);
			nbt.remove(key);
		}
	}

	private void copyCompoundOfDoubles(JsonObject obj, CompoundTag nbt, String key) {
		if (nbt.contains(key)) {
			CompoundTag compound = nbt.getCompound(key);
			JsonObject sObj = new JsonObject();
			for (String comp : compound.getAllKeys()) {
				sObj.addProperty(comp, compound.getDouble(comp));
			}
			obj.add(key, sObj);
			nbt.remove(key);
		}
	}
}
