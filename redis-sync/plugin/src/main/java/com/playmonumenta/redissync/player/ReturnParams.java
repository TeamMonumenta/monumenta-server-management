package com.playmonumenta.redissync.player;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

public class ReturnParams {
	private @Nullable Double mYaw;
	private @Nullable Double mPitch;
	private @Nullable Vector mPos;

	private ReturnParams() {
	}

	public static ReturnParams create() {
		return new ReturnParams();
	}

	public static ReturnParams from(Location location) {
		return create().pos(location.toVector()).rot(location.getYaw(), location.getPitch());
	}

	public ReturnParams rot(float yaw, float pitch) {
		yaw(yaw);
		pitch(pitch);
		return this;
	}

	public ReturnParams yaw(double yaw) {
		mYaw = yaw;
		return this;
	}

	public ReturnParams pitch(double pitch) {
		mPitch = pitch;
		return this;
	}

	public ReturnParams pos(Vector vec) {
		mPos = vec.clone();
		return this;
	}

	public ReturnParams usePlayerRot() {
		usePlayerYaw();
		usePlayerPitch();
		return this;
	}

	public ReturnParams usePlayerYaw() {
		mYaw = null;
		return this;
	}

	public ReturnParams usePlayerPitch() {
		mPitch = null;
		return this;
	}

	public ReturnParams usePlayerPos() {
		mPos = null;
		return this;
	}

	PlayerPos applyTo(Player player) {
		return new PlayerPos(
			mPos != null ? mPos.getX() : player.getLocation().x(),
			mPos != null ? mPos.getY() : player.getLocation().y(),
			mPos != null ? mPos.getZ() : player.getLocation().z(),
			mPitch != null ? mPitch : player.getLocation().getPitch(),
			mYaw != null ? mYaw : player.getLocation().getYaw()
		);
	}
}
