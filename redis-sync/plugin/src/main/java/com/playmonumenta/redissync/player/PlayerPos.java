package com.playmonumenta.redissync.player;

import org.bukkit.Location;

public record PlayerPos(double x, double y, double z, double pitch, double yaw) {
	public PlayerPos(Location location) {
		this(location.x(), location.y(), location.z(), location.getPitch(), location.getYaw());
	}
}
