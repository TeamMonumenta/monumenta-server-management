package com.playmonumenta.networkchat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

// Permissions to listen or talk in a channel
public class MessageVisibility {
	public enum VisibilityKey {
		JOIN("join"),
		DEATH("death");

		final String mKey;

		VisibilityKey(String s) {
			mKey = s;
		}

		public static @Nullable VisibilityKey of(String s) {
			try {
				return valueOf(s.toUpperCase());
			} catch (Exception e) {
				return null;
			}
		}

		public String getKey() {
			return mKey;
		}
	}

	public enum VisibilityValue {
		DEFAULT("default"),
		ALWAYS("always"),
		LOCAL("local"),
		SELF("self"),
		NEVER("never");

		final String mValue;

		VisibilityValue(String s) {
			mValue = s;
		}

		public static VisibilityValue of(String s) {
			try {
				return valueOf(s.toUpperCase());
			} catch (Exception e) {
				return DEFAULT;
			}
		}

		public String getValue() {
			return mValue;
		}
	}

	private final Map<VisibilityKey, VisibilityValue> mVisibilities = new HashMap<>();

	public static MessageVisibility fromJson(JsonObject object) {
		MessageVisibility settings = new MessageVisibility();
		if (object != null) {
			for (Map.Entry<String, JsonElement> settingsEntry : object.entrySet()) {
				String key = settingsEntry.getKey();
				JsonElement valueJson = settingsEntry.getValue();

				VisibilityKey visibilityKey = VisibilityKey.of(key);
				if (visibilityKey != null && valueJson.isJsonPrimitive()) {
					JsonPrimitive valueJsonPrimitive = valueJson.getAsJsonPrimitive();
					if (valueJsonPrimitive != null && valueJsonPrimitive.isString()) {
						VisibilityValue visibilityValue = VisibilityValue.of(valueJsonPrimitive.getAsString());
						if (visibilityValue != null) {
							settings.mVisibilities.put(visibilityKey, visibilityValue);
						}
					}
				}
			}
		}
		return settings;
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		for (Map.Entry<VisibilityKey, VisibilityValue> entry : mVisibilities.entrySet()) {
			String key = entry.getKey().getKey();
			String value = entry.getValue().getValue();
			object.addProperty(key, value);
		}
		return object;
	}

	public boolean isDefault() {
		for (Map.Entry<VisibilityKey, VisibilityValue> entry : mVisibilities.entrySet()) {
			if (entry.getValue() != VisibilityValue.DEFAULT) {
				return false;
			}
		}
		return true;
	}

	public static String[] getVisibilityKeys() {
		return Stream.of(VisibilityKey.values()).map(VisibilityKey::getKey).toArray(String[]::new);
	}

	public static String[] getVisibilityValues() {
		return Stream.of(VisibilityValue.values()).map(VisibilityValue::getValue).toArray(String[]::new);
	}

	public VisibilityValue getVisibility(VisibilityKey key) {
		VisibilityValue result = mVisibilities.get(key);
		if (result == null) {
			return VisibilityValue.DEFAULT;
		}
		return result;
	}

	public void setVisibility(VisibilityKey key, @Nullable VisibilityValue value) {
		if (key != null) {
			if (value == null || value == VisibilityValue.DEFAULT) {
				mVisibilities.remove(key);
			} else {
				mVisibilities.put(key, value);
			}
		}
	}

	public VisibilityValue joinVisibility() {
		return getVisibility(VisibilityKey.JOIN);
	}

	public VisibilityValue deathVisibility() {
		return getVisibility(VisibilityKey.DEATH);
	}

	public int commandVisibility(CommandSender sender, String category) throws WrapperCommandSyntaxException {
		VisibilityKey visibilityKey = VisibilityKey.of(category);
		if (visibilityKey != null) {
			VisibilityValue value = getVisibility(visibilityKey);
			sender.sendMessage(Component.empty()
			    .append(Component.text(category, NamedTextColor.AQUA, TextDecoration.BOLD))
			    .append(Component.text(" is set to ", NamedTextColor.GRAY))
			    .append(Component.text(value.getValue(), NamedTextColor.AQUA, TextDecoration.BOLD))
			    .append(Component.text(".", NamedTextColor.GRAY)));
			return value.ordinal();
		}

		throw CommandUtils.fail(sender, "No such visibility category: " + category);
	}

	public int commandVisibility(CommandSender sender, String category, String value) throws WrapperCommandSyntaxException {
		VisibilityKey visibilityKey = VisibilityKey.of(category);
		if (visibilityKey != null) {
			VisibilityValue visibilityValue = VisibilityValue.of(value);
			setVisibility(visibilityKey, visibilityValue);
			sender.sendMessage(Component.empty()
			    .append(Component.text("Set ", NamedTextColor.GRAY))
			    .append(Component.text(category, NamedTextColor.AQUA, TextDecoration.BOLD))
			    .append(Component.text(" to ", NamedTextColor.GRAY))
			    .append(Component.text(value, NamedTextColor.AQUA, TextDecoration.BOLD))
			    .append(Component.text(".", NamedTextColor.GRAY)));
			return visibilityValue.ordinal();
		}

		throw CommandUtils.fail(sender, "No such visibility category: " + category);
	}
}
