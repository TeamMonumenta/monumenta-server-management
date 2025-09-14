package com.playmonumenta.redissync.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.redissync.MonumentaRedisSync;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

public class ScoreboardUtils {
	@Deprecated
	/* Use the method in the VersionAdapter instead */
	public static JsonObject getAsJsonObject(Player player) {
		JsonObject data = new JsonObject();

		for (Objective objective : Bukkit.getScoreboardManager().getMainScoreboard().getObjectives()) {
			Score score = objective.getScore(player.getName());
			if (score.isScoreSet()) {
				data.addProperty(objective.getName(), score.getScore());
			}
		}

		return data;
	}

	public static void loadFromJsonObject(Player player, JsonObject data) {
		Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

		for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
			String name = entry.getKey();
			int scoreVal = entry.getValue().getAsInt();

			Objective objective = scoreboard.getObjective(name);
			if (objective == null) {
				objective = scoreboard.registerNewObjective(name, Criteria.DUMMY, Component.text(name));
			}

			if (!objective.isModifiable()) {
				final Criteria criteriaName = objective.getTrackedCriteria();
				// TODO: remove debug logging :3
				MonumentaRedisSync.getInstance().getLogger().warning(() -> "Objective " + name + " is not modifiable. Criteria: " + criteriaName.getName());
				continue;
			}

			Score score = objective.getScore(player.getName());
			score.setScore(scoreVal);
		}
	}

	public static int getScoreboardValue(String name, String scoreboardValue) {
		Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(scoreboardValue);
		if (objective != null) {
			return getScoreboardValue(name, objective);
		}

		return 0;
	}

	public static int getScoreboardValue(String name, Objective objective) {
		return objective.getScore(name).getScore();
	}

	public static void setScoreboardValue(String name, String scoreboardValue, int value) {
		Objective objective = Bukkit.getScoreboardManager().getMainScoreboard().getObjective(scoreboardValue);
		if (objective != null) {
			setScoreboardValue(name, objective, value);
		}
	}

	public static void setScoreboardValue(String name, Objective objective, int value) {
		Score score = objective.getScore(name);
		score.setScore(value);
	}
}
