package com.playmonumenta.redissync.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.Nullable;

public class ContentData {
	private final String mId;
	private @Nullable NamespacedKey mMcfunctionOnArrival;
	private @Nullable OptionalLocation mReturnLocation;
	private @Nullable OptionalLocation mArrivalLocation;
	private JsonObject mExtra;

	public ContentData(String id) {
		this(id, null, null, null, new JsonObject());
	}

	public ContentData(
		String id, @Nullable NamespacedKey mcfunctionOnArrival,
		@Nullable OptionalLocation returnLocation, @Nullable OptionalLocation arrivalLocation, JsonObject extra
	) {
		mId = id;
		mMcfunctionOnArrival = mcfunctionOnArrival;
		mReturnLocation = returnLocation;
		mArrivalLocation = arrivalLocation;
		mExtra = extra;
	}

	public ContentData(@Nullable JsonObject object) {
		if (object == null) {
			mId = "";
			mMcfunctionOnArrival = null;
			mReturnLocation = null;
			mArrivalLocation = null;
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

		if (object.get("returnLocation") instanceof JsonObject returnLocationObject) {
			mReturnLocation = OptionalLocation.fromJson(returnLocationObject);
		} else {
			mReturnLocation = null;
		}

		if (object.get("arrivalLocation") instanceof JsonObject arrivalLocationObject) {
			mArrivalLocation = OptionalLocation.fromJson(arrivalLocationObject);
		} else {
			mArrivalLocation = null;
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

	public @Nullable OptionalLocation getReturnLocation() {
		return mReturnLocation;
	}

	public void setReturnLocation(@Nullable OptionalLocation returnLocation) {
		mReturnLocation = returnLocation;
	}

	public @Nullable OptionalLocation getArrivalLocation() {
		return mArrivalLocation;
	}

	public void setArrivalLocation(@Nullable OptionalLocation arrivalLocation) {
		mArrivalLocation = arrivalLocation;
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

		if (mReturnLocation != null) {
			object.add("returnLocation", mReturnLocation.toJson());
		}

		if (mArrivalLocation != null) {
			object.add("arrivalLocation", mArrivalLocation.toJson());
		}

		if (!mExtra.isEmpty()) {
			object.add("extra", mExtra);
		}

		return object;
	}
}
