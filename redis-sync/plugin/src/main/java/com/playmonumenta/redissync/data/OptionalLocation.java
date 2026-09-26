package com.playmonumenta.redissync.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

public class OptionalLocation {
	private double mX;
	private double mY;
	private double mZ;
	private float mYaw;
	private float mPitch;

	public OptionalLocation(double x, double y, double z) {
		this(x, y, z, 0f, 0f);
	}

	public OptionalLocation(double x, double y, double z, float yaw, float pitch) {
		mX = x;
		mY = y;
		mZ = z;
		mYaw = yaw;
		mPitch = pitch;
	}

	public OptionalLocation(Vector position) {
		this(position, 0f, 0f);
	}

	public OptionalLocation(Vector position, float yaw, float pitch) {
		this(position.getX(), position.getY(), position.getZ(), yaw, pitch);
	}

	public OptionalLocation(Location location) {
		this(location.x(), location.y(), location.z(), location.getYaw(), location.getPitch());
	}

	public static @Nullable OptionalLocation fromJson(JsonObject object) {
		try {
			return new OptionalLocation(
				getCoordDouble(object, "x"),
				getCoordDouble(object, "y"),
				getCoordDouble(object, "z"),
				getCoordFloat(object, "yaw"),
				getCoordFloat(object, "pitch")
			);
		} catch (Exception ignored) {
			return null;
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
		return mX;
	}

	public void x(double value) {
		mX = value;
	}

	public double y() {
		return mY;
	}

	public void y(double value) {
		mY = value;
	}

	public double z() {
		return mZ;
	}

	public void z(double value) {
		mZ = value;
	}

	public Vector positionBukkit() {
		return new Vector(mX, mY, mZ);
	}

	public void positionBukkit(Vector value) {
		mX = value.getX();
		mY = value.getY();
		mZ = value.getZ();
	}

	public Location locationBukkit(@Nullable World world) {
		return new Location(world, mX, mY, mZ, mYaw, mPitch);
	}

	public void locationBukkit(Location value) {
		mX = value.x();
		mY = value.y();
		mZ = value.z();
		mYaw = value.getYaw();
		mPitch = value.getPitch();
	}

	public float yaw() {
		return mYaw;
	}

	public void yaw(float yaw) {
		mYaw = yaw;
	}

	public float pitch() {
		return mPitch;
	}

	public void pitch(float pitch) {
		mPitch = pitch;
	}

	public void rotation(float yaw, float pitch) {
		mYaw = yaw;
		mPitch = pitch;
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();

		object.addProperty("x", mX);
		object.addProperty("y", mY);
		object.addProperty("z", mZ);
		object.addProperty("yaw", mYaw);
		object.addProperty("pitch", mPitch);

		return object;
	}
}
