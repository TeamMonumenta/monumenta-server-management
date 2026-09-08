package com.playmonumenta.redissync.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

public class ContentData {
	private final String mId;
	private @Nullable NamespacedKey mMcfunctionOnArrival;
	private JsonObject mExtra;

	public ContentData(String id) {
		this(id, null);
	}

	public ContentData(String id, @Nullable NamespacedKey mcfunctionOnArrival) {
		this(id, mcfunctionOnArrival, new JsonObject());
	}

	public ContentData(String id, @Nullable NamespacedKey mcfunctionOnArrival, JsonObject extra) {
		mId = id;
		mMcfunctionOnArrival = mcfunctionOnArrival;
		mExtra = extra;
	}

	public ContentData(@Nullable JsonObject object) {
		if (object == null) {
			mId = "";
			mMcfunctionOnArrival = null;
			mExtra = new JsonObject();
			return;
		}

		if (object.get("id") instanceof JsonPrimitive idPrimitive && idPrimitive.isString()) {
			mId = idPrimitive.getAsString();
		} else {
			mId = "";
		}

		if (
			object.get("mcfunctionOnArrival") instanceof JsonPrimitive arrivalFunctionPrimitive &&
			arrivalFunctionPrimitive.isString()
		) {
			mMcfunctionOnArrival = NamespacedKey.fromString(arrivalFunctionPrimitive.getAsString());
		} else {
			mMcfunctionOnArrival = null;
		}

		if (object.get("extra") instanceof JsonObject extra) {
			mExtra = extra;
		} else {
			mExtra = new JsonObject();
		}
	}

	public String getId() {
		return mId;
	}

	public @Nullable NamespacedKey getMcfunctionOnArrival() {
		return mMcfunctionOnArrival;
	}

	public void setMcfunctionOnArrival(@Nullable NamespacedKey mcfunctionOnArrival) {
		mMcfunctionOnArrival = mcfunctionOnArrival;
	}

	public JsonObject getExtra() {
		return mExtra;
	}

	public void setExtra(@Nullable JsonObject extra) {
		if (extra == null) {
			extra = new JsonObject();
		}
		mExtra = extra;
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();

		object.addProperty("id", mId);

		if (mMcfunctionOnArrival != null) {
			object.addProperty("mcfunctionOnArrival", mMcfunctionOnArrival.toString());
		}

		if (!mExtra.isEmpty()) {
			object.add("extra", mExtra);
		}

		return object;
	}
}
