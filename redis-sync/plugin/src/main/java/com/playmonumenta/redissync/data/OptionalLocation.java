package com.playmonumenta.redissync.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector3d;

public class OptionalLocation {
	private Vector3d mPosition;
	private @Nullable Vector2f mRotation;

	public OptionalLocation(Vector3d position) {
		this(position, null);
	}

	public OptionalLocation(Vector3d position, @Nullable Vector2f rotation) {
		mPosition = position;
		mRotation = rotation;
	}

	public OptionalLocation(Vector position) {
		this(position, null, null);
	}

	public OptionalLocation(Vector position, @Nullable Float yaw, @Nullable Float pitch) {
		mPosition = new Vector3d(position.getX(), position.getY(), position.getZ());

		if (yaw == null || pitch == null) {
			mRotation = null;
		} else {
			mRotation = new Vector2f(yaw, pitch);
		}
	}

	public OptionalLocation(Location location) {
		mPosition = new Vector3d(location.x(), location.y(), location.z());
		mRotation = new Vector2f(location.getYaw(), location.getPitch());
	}

	public static @Nullable OptionalLocation OptionalLocation(JsonObject object) {
		Vector3d pos = new Vector3d();

		if (object.get("pos") instanceof JsonArray posArray && posArray.size() == 3) {
			for (int i = 0; i < 3; i++) {
				JsonElement posElement = posArray.get(i);
				if (posElement instanceof JsonPrimitive posPrim && posPrim.isNumber()) {
					double coord = posPrim.getAsDouble();
					switch (i) {
						case 0: pos.x = coord;
						case 1: pos.y = coord;
						case 2: pos.z = coord;
					}
				} else {
					return null;
				}
			}
		} else {
			return null;
		}

		if (object.get("rot") instanceof JsonArray rotArray && rotArray.size() == 2) {
			Vector2f rot = new Vector2f();
			for (int i = 0; i < 2; i++) {
				JsonElement rotElement = posArray.get(i);
				if (rotElement instanceof JsonPrimitive rotPrim && rotPrim.isNumber()) {
					float coord = rotPrim.getAsFloat();
					switch (i) {
						case 0: rot.x = coord;
						case 1: rot.y = coord;
					}
				} else {
					return new OptionalLocation(pos, null);
				}
			}
			return new OptionalLocation(pos, rot);
		} else {
			return new OptionalLocation(pos, null);
		}
	}

	public double x() {
		return mPosition.x;
	}

	public void x(double value) {
		mPosition.x = value;
	}

	public double y() {
		return mPosition.y;
	}

	public void y(double value) {
		mPosition.y = value;
	}

	public double z() {
		return mPosition.z;
	}

	public void z(double value) {
		mPosition.z = value;
	}

	public Vector3d positionJoml() {
		return mPosition;
	}

	public Vector positionBukkit() {
		return new Vector(mPosition.x, mPosition.y, mPosition.z);
	}

	public Location locationBukkit(@Nullable World world) {
		if (mRotation == null) {
			return new Location(world, mPosition.x, mPosition.y, mPosition.z);
		}
		return new Location(world, mPosition.x, mPosition.y, mPosition.z, mRotation.x, mRotation.y);
	}

	public @Nullable Float yaw() {
		if (mRotation == null) {
			return null;
		}
		return mRotation.x;
	}

	public @Nullable Float pitch() {
		if (mRotation == null) {
			return null;
		}
		return mRotation.y;
	}

	public @Nullable Vector2f rotationJoml() {
		return mRotation;
	}

	public void rotationJoml(@Nullable Vector2f rotation) {
		mRotation = rotation;
	}

	public void rotation(@Nullable Float yaw, @Nullable Float pitch) {
		if (yaw == null || pitch == null) {
			mRotation = null;
		} else {
			mRotation = new Vector2f(yaw, pitch);
		}
	}
}
