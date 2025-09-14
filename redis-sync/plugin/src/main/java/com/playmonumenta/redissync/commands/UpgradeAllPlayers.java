package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSync;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI.RedisPlayerData;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

public class UpgradeAllPlayers {
	public static void register(MonumentaRedisSync plugin) {
		new CommandAPICommand("monumenta")
			.withSubcommand(new CommandAPICommand("redissync")
				.withSubcommand(new CommandAPICommand("upgradeallplayers")
					.executesPlayer((player, args) -> {
						player.sendMessage("This command is only available from the console");
					})
					.executesConsole((console, args) -> {
						try {
							run(plugin);
						} catch (Exception ex) {
							throw CommandAPI.failWithString(ex.getMessage());
						}
					})
				)).register();
	}

	private static void updatePlayer(MonumentaRedisSync mrs, UUID uuid) {
		Bukkit.getServer().sendMessage(Component.text("Upgrading: " + uuid.toString()));
		try {
			RedisPlayerData data = MonumentaRedisSyncAPI.getOfflinePlayerData(uuid).get();

			if (data == null) {
				return;
			}

			Object newData = mrs.getVersionAdapter().upgradePlayerData(data.getNbtTagCompoundData());
			if (newData == null) {
				Bukkit.getServer().sendMessage(Component.text("Failed to upgrade player data: " + uuid).color(NamedTextColor.RED));
				return;
			}
			data.setNbtTagCompoundData(newData);

			String newAdvancements = mrs.getVersionAdapter().upgradePlayerAdvancements(data.getAdvancements());
			if (newAdvancements == null) {
				Bukkit.getServer().sendMessage(Component.text("Failed to upgrade player advancements: " + uuid).color(NamedTextColor.RED));
				return;
			}
			data.setAdvancements(newAdvancements);

			data.setHistory("VERSION_UPGRADE|" + System.currentTimeMillis() + "|" + uuid);

			/* Save and then wait for save to complete and check results */
			if (!MonumentaRedisSyncAPI.saveOfflinePlayerData(data).get()) {
				Bukkit.getServer().sendMessage(Component.text("Failed to save upgraded player: " + uuid).color(NamedTextColor.RED));
			}
		} catch (Exception ex) {
			Bukkit.getServer().sendMessage(Component.text("Failed to upgrade player: " + uuid + " : " + ex.getMessage()).color(NamedTextColor.RED));
			ex.printStackTrace();
		}
	}

	private static void run(MonumentaRedisSync mrs) {
		Bukkit.getServer().sendMessage(Component.text("WARNING: Player data upgrade has started for offline players"));
		Bukkit.getServer().sendMessage(Component.text("The server will lag significantly until this is complete"));

		try {
			Set<UUID> players = MonumentaRedisSyncAPI.getAllPlayerUUIDs().get();
			Iterator<UUID> iter = players.iterator();

			new BukkitRunnable() {
				@Override
				public void run() {
					long startTime = System.currentTimeMillis();

					Bukkit.getServer().sendMessage(Component.text("  Players left to process: " + players.size()));

					/* Only block here for up to 1 second at a time */
					while (System.currentTimeMillis() < startTime + 1000) {
						if (!iter.hasNext()) {
							Bukkit.getServer().sendMessage(Component.text("Upgrade complete"));
							this.cancel();
							return;
						}

						UUID uuid = iter.next();
						iter.remove();

						updatePlayer(mrs, uuid);
					}
				}
			}.runTaskTimer(mrs, 0, 1);
		} catch (Exception ex) {
			Bukkit.getServer().sendMessage(Component.text("Upgrade failed: " + ex.getMessage()).color(NamedTextColor.RED));
			ex.printStackTrace();
		}
	}
}
