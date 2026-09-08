package com.playmonumenta.redissync.data;

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

	public static @Nullable OptionalLocation fromJson(JsonObject object) {
		Vector3d pos;
		try {
			pos = new Vector3d(
				getCoordDouble(object, "x"),
				getCoordDouble(object, "y"),
				getCoordDouble(object, "z")
			);
		} catch (Exception ignored) {
			return null;
		}

		try {
			return new OptionalLocation(pos, new Vector2f(
				getCoordFloat(object, "yaw"),
				getCoordFloat(object, "pitch")
			));
		} catch (Exception ignored) {
			return new OptionalLocation(pos);
		}
	}

	private static double getCoordDouble(JsonObject object, String key) throws Exception {
		if (object.get(key) instanceof JsonPrimitive coordPrimitive && coordPrimitive.isNumber()) {
			return coordPrimitive.getAsDouble();
		}
		throw new Exception("Expected " + key + " to be a double");
	}

	private static float getCoordFloat(JsonObject object, String key) throws Exception {
		if (object.get(key) instanceof JsonPrimitive coordPrimitive && coordPrimitive.isNumber()) {
			return coordPrimitive.getAsFloat();
		}
		throw new Exception("Expected " + key + " to be a float");
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

	public void positionJoml(Vector3d value) {
		mPosition = value;
	}

	public Vector positionBukkit() {
		return new Vector(mPosition.x, mPosition.y, mPosition.z);
	}

	public void positionBukkit(Vector value) {
		mPosition = new Vector3d(value.getX(), value.getY(), value.getZ());
	}

	public Location locationBukkit(@Nullable World world) {
		if (mRotation == null) {
			return new Location(world, mPosition.x, mPosition.y, mPosition.z);
		}
		return new Location(world, mPosition.x, mPosition.y, mPosition.z, mRotation.x, mRotation.y);
	}

	public void locationBukkit(Location value) {
		mPosition = new Vector3d(value.getX(), value.getY(), value.getZ());
		mRotation = new Vector2f(value.getYaw(), value.getPitch());
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

	public JsonObject toJson() {
		JsonObject object = new JsonObject();

		object.addProperty("x", mPosition.x);
		object.addProperty("y", mPosition.y);
		object.addProperty("z", mPosition.z);

		if (mRotation != null) {
			object.addProperty("yaw", mRotation.x);
			object.addProperty("pitch", mRotation.y);
		}

		return object;
	}
}
