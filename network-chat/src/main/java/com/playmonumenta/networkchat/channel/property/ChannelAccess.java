package com.playmonumenta.networkchat.channel.property;

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

// Access to listen or talk in a channel
public class ChannelAccess {
	public enum FlagKey {
		MAY_CHAT("may_chat"),
		MAY_LISTEN("may_listen");

		final String mKey;

		FlagKey(String s) {
			mKey = s;
		}

		public static @Nullable FlagKey of(String s) {
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

	public enum FlagValue {
		DEFAULT("default"),
		FALSE("false"),
		TRUE("true");

		final String mValue;

		FlagValue(String s) {
			mValue = s;
		}

		public static @Nullable FlagValue of(String s) {
			try {
				return valueOf(s.toUpperCase());
			} catch (Exception e) {
				return null;
			}
		}

		public String getValue() {
			return mValue;
		}
	}

	private final Map<FlagKey, Boolean> mFlags = new HashMap<>();

	public static ChannelAccess fromJson(JsonObject object) {
		ChannelAccess perms = new ChannelAccess();
		if (object != null) {
			for (Map.Entry<String, JsonElement> permsEntry : object.entrySet()) {
				String key = permsEntry.getKey();
				JsonElement valueJson = permsEntry.getValue();

				FlagKey flagKey = FlagKey.of(key);
				if (flagKey != null && valueJson.isJsonPrimitive()) {
					JsonPrimitive valueJsonPrimitive = valueJson.getAsJsonPrimitive();
					if (valueJsonPrimitive != null && valueJsonPrimitive.isBoolean()) {
						perms.mFlags.put(flagKey, valueJsonPrimitive.getAsBoolean());
					}
				}
			}
		}
		return perms;
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		for (Map.Entry<FlagKey, Boolean> entry : mFlags.entrySet()) {
			String keyStr = entry.getKey().getKey();
			Boolean value = entry.getValue();
			if (value != null) {
				object.addProperty(keyStr, value);
			}
		}
		return object;
	}

	public boolean isDefault() {
		for (Map.Entry<FlagKey, Boolean> entry : mFlags.entrySet()) {
			if (entry.getValue() != null) {
				return false;
			}
		}
		return true;
	}

	public static String[] getFlagKeys() {
		return Stream.of(FlagKey.values()).map(FlagKey::getKey).toArray(String[]::new);
	}

	public static String[] getFlagValues() {
		return Stream.of(FlagValue.values()).map(FlagValue::getValue).toArray(String[]::new);
	}

	public @Nullable Boolean getFlag(String key) {
		return mFlags.get(FlagKey.of(key));
	}

	public void setFlag(String key, @Nullable Boolean value) {
		FlagKey flagKey = FlagKey.of(key);
		if (flagKey != null) {
			if (value == null) {
				mFlags.remove(flagKey);
			} else {
				mFlags.put(flagKey, value);
			}
		}
	}

	public @Nullable Boolean mayChat() {
		return getFlag(FlagKey.MAY_CHAT.getKey());
	}

	public void mayChat(Boolean value) {
		setFlag(FlagKey.MAY_CHAT.getKey(), value);
	}

	public @Nullable Boolean mayListen() {
		return getFlag(FlagKey.MAY_LISTEN.getKey());
	}

	public void mayListen(Boolean value) {
		setFlag(FlagKey.MAY_LISTEN.getKey(), value);
	}

	public int commandFlag(CommandSender sender, String accessKey) throws WrapperCommandSyntaxException {
		if (FlagKey.of(accessKey) != null) {
			Boolean value = getFlag(accessKey);
			if (value == null) {
				sender.sendMessage(Component.empty()
				    .append(Component.text(accessKey, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" is set to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.DEFAULT.getValue(), NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 0;
			} else if (value) {
				sender.sendMessage(Component.empty()
				    .append(Component.text(accessKey, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" is set to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.TRUE.getValue(), NamedTextColor.GREEN, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 1;
			} else {
				sender.sendMessage(Component.empty()
				    .append(Component.text(accessKey, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" is set to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.FALSE.getValue(), NamedTextColor.RED, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return -1;
			}
		}

		throw CommandUtils.fail(sender, "No such access key: " + accessKey);
	}

	public int commandFlag(CommandSender sender, String accessKey, String value) throws WrapperCommandSyntaxException {
		if (FlagKey.of(accessKey) != null) {
			if (FlagValue.FALSE.getValue().equals(value)) {
				setFlag(accessKey, false);
				sender.sendMessage(Component.empty()
				    .append(Component.text("Set ", NamedTextColor.GRAY))
				    .append(Component.text(accessKey, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.FALSE.getValue(), NamedTextColor.RED, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return -1;
			} else if (FlagValue.TRUE.getValue().equals(value)) {
				setFlag(accessKey, true);
				sender.sendMessage(Component.empty()
				    .append(Component.text("Set ", NamedTextColor.GRAY))
				    .append(Component.text(accessKey, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.TRUE.getValue(), NamedTextColor.GREEN, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 1;
			} else if (FlagValue.DEFAULT.getValue().equals(value)) {
				setFlag(accessKey, null);
				sender.sendMessage(Component.empty()
				    .append(Component.text("Set ", NamedTextColor.GRAY))
				    .append(Component.text(accessKey, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.DEFAULT.getValue(), NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 0;
			} else {
				sender.sendMessage(Component.empty()
				    .append(Component.text("Invalid value ", NamedTextColor.RED))
				    .append(Component.text(value, NamedTextColor.RED, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.RED)));
				return 0;
			}
		}

		throw CommandUtils.fail(sender, "No such access key: " + accessKey);
	}
}
