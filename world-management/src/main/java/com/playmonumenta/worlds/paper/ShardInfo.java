package com.playmonumenta.worlds.paper;

import com.playmonumenta.worlds.common.MMLog;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public class ShardInfo {
	private final String mInstanceObjective;
	private final String mBaseWorldName;
	private final @Nullable String mJoinInstanceCommand;
	private final @Nullable String mRejoinInstanceCommand;
	private final @Nullable String mRespawnInstanceCommand;
	private final int mPregeneratedInstances;
	private final @Nullable String mVariantObjective;
	private final Map<Integer, String> mVariantTemplates = new HashMap<>();

	protected ShardInfo(WorldManagementPlugin plugin, ConfigurationSection config) {
		mInstanceObjective = config.getString("instance-objective", "Instance");
		plugin.printConfig("    instance-objective", mInstanceObjective);

		mBaseWorldName = config.getString("base-world-name", "instance");
		plugin.printConfig("    base-world-name", mBaseWorldName);

		mJoinInstanceCommand = config.getString("join-instance-command", null);
		plugin.printConfig("    join-instance-command", mJoinInstanceCommand);

		mRejoinInstanceCommand = config.getString("rejoin-instance-command", null);
		plugin.printConfig("    rejoin-instance-command", mRejoinInstanceCommand);

		mRespawnInstanceCommand = config.getString("respawn-instance-command", null);
		plugin.printConfig("    respawn-instance-command", mRespawnInstanceCommand);

		mPregeneratedInstances = config.getInt("pregenerated-instances", 0);
		plugin.printConfig("    pregenerated-instances", mPregeneratedInstances);

		mVariantObjective = config.getString("variant-objective", null);
		plugin.printConfig("    variant-objective", mVariantObjective);

		ConfigurationSection variantConfig = config.getConfigurationSection("variants");
		if (variantConfig == null) {
			plugin.printConfig("    variants", null);
		} else {
			plugin.printConfigHeader("    variants");

			for (String templateName : variantConfig.getKeys(false)) {
				int variantId = variantConfig.getInt(templateName, -1);
				mVariantTemplates.put(variantId, templateName);
			}

			for (Map.Entry<Integer, String> entry : mVariantTemplates.entrySet()) {
				plugin.printConfig("      " + entry.getValue(), entry.getKey());
			}
		}
	}

	public String getInstanceObjective() {
		return mInstanceObjective;
	}

	public String getBaseWorldName() {
		return mBaseWorldName;
	}

	public @Nullable String getJoinInstanceCommand() {
		return mJoinInstanceCommand;
	}

	public @Nullable String getRejoinInstanceCommand() {
		return mRejoinInstanceCommand;
	}

	public @Nullable String getRespawnInstanceCommand() {
		return mRespawnInstanceCommand;
	}

	public int getPregeneratedInstances() {
		return mPregeneratedInstances;
	}

	public Set<String> getVariantTemplates() {
		return new HashSet<>(mVariantTemplates.values());
	}

	public @Nullable String getVariant(Player player) throws IndexOutOfBoundsException {
		int score;
		if (mVariantObjective == null) {
			score = 0;
		} else {
			score = ScoreboardUtils.getScoreboardValue(player, mVariantObjective).orElse(0);
		}
		String variantTemplate = mVariantTemplates.get(score);
		if (variantTemplate == null && score != 0) {
			MMLog.warning("No world for score " + score + ", attempting to use default score");
			score = 0;
			variantTemplate = mVariantTemplates.get(0);
		}
		if (variantTemplate == null) {
			MMLog.severe("No template world for score " + score);
		}
		return variantTemplate;
	}
}
