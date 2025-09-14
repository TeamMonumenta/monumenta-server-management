package com.playmonumenta.networkchat.channel.property;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MMLog;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// Settings for a given channel, same structure for channel default and player preference
public class ChannelSettings {
	public enum FlagKey {
		IS_LISTENING("is_listening"),
		MESSAGES_PLAY_SOUND("messages_play_sound");

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

	public static class CSound {
		public final Sound mSound;
		public float mVolume;
		public float mPitch;

		public CSound(Sound sound, float volume, float pitch) {
			mSound = sound;
			mVolume = volume;
			mPitch = pitch;
		}

		public void playSound(Player player) {
			player.playSound(player.getLocation(), mSound, mVolume, mPitch);
		}

		public JsonObject toJson() {
			JsonObject object = new JsonObject();
			object.addProperty("mSound", mSound.toString());
			object.addProperty("mVolume", mVolume);
			object.addProperty("mPitch", mPitch);

			return object;
		}

		public static CSound fromJson(JsonObject object) throws Exception {
			Sound sound = Sound.valueOf(object.get("mSound").getAsString());
			float volume = object.get("mVolume").getAsFloat();
			float pitch = object.get("mPitch").getAsFloat();
			return new CSound(sound, volume, pitch);
		}
	}

	private final Map<FlagKey, Boolean> mFlags = new HashMap<>();
	private final List<CSound> mSounds = new ArrayList<>();

	public static ChannelSettings fromJson(JsonObject object) {
		ChannelSettings settings = new ChannelSettings();
		if (object != null) {
			for (Map.Entry<String, JsonElement> settingsEntry : object.entrySet()) {
				String key = settingsEntry.getKey();
				JsonElement valueJson = settingsEntry.getValue();

				FlagKey flagKey = FlagKey.of(key);
				if (flagKey != null && valueJson.isJsonPrimitive()) {
					JsonPrimitive valueJsonPrimitive = valueJson.getAsJsonPrimitive();
					if (valueJsonPrimitive != null && valueJsonPrimitive.isBoolean()) {
						settings.mFlags.put(flagKey, valueJsonPrimitive.getAsBoolean());
					}
				}
			}

			JsonArray cSoundsArray = object.getAsJsonArray("SoundsList");
			if (cSoundsArray != null && cSoundsArray.size() > 0) {
				for (JsonElement element : cSoundsArray) {
					try {
						settings.mSounds.add(CSound.fromJson(element.getAsJsonObject()));
					} catch (Exception e) {
						MMLog.warning("Caught an exception while converting SoundsList to object. Reason: " + e.getMessage());
					}
				}
			}
		}
		return settings;
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

		if (!mSounds.isEmpty()) {
			JsonArray cSoundsArray = new JsonArray();
			for (CSound sound : mSounds) {
				cSoundsArray.add(sound.toJson());
			}
			object.add("SoundsList", cSoundsArray);
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

	public @Nullable Boolean isListening() {
		return getFlag(FlagKey.IS_LISTENING.getKey());
	}

	public void isListening(Boolean value) {
		setFlag(FlagKey.IS_LISTENING.getKey(), value);
	}

	public @Nullable Boolean messagesPlaySound() {
		return getFlag(FlagKey.MESSAGES_PLAY_SOUND.getKey());
	}

	public void messagesPlaySound(Boolean value) {
		setFlag(FlagKey.MESSAGES_PLAY_SOUND.getKey(), value);
	}

	public int commandFlag(CommandSender sender, String setting) throws WrapperCommandSyntaxException {
		if (FlagKey.of(setting) != null) {
			Boolean value = getFlag(setting);
			if (value == null) {
				sender.sendMessage(Component.empty()
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" is set to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.DEFAULT.getValue(), NamedTextColor.DARK_GRAY, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 0;
			} else if (value) {
				sender.sendMessage(Component.empty()
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" is set to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.TRUE.getValue(), NamedTextColor.GREEN, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 1;
			} else {
				sender.sendMessage(Component.empty()
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" is set to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.FALSE.getValue(), NamedTextColor.RED, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return -1;
			}
		}

		throw CommandUtils.fail(sender, "No such setting: " + setting);
	}

	public int commandFlag(CommandSender sender, String setting, String value) throws WrapperCommandSyntaxException {
		if (FlagKey.of(setting) != null) {
			if (FlagValue.FALSE.getValue().equals(value)) {
				setFlag(setting, false);
				sender.sendMessage(Component.empty()
				    .append(Component.text("Set ", NamedTextColor.GRAY))
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.FALSE.getValue(), NamedTextColor.RED, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return -1;
			} else if (FlagValue.TRUE.getValue().equals(value)) {
				setFlag(setting, true);
				sender.sendMessage(Component.empty()
				    .append(Component.text("Set ", NamedTextColor.GRAY))
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
				    .append(Component.text(" to ", NamedTextColor.GRAY))
				    .append(Component.text(FlagValue.TRUE.getValue(), NamedTextColor.GREEN, TextDecoration.BOLD))
				    .append(Component.text(".", NamedTextColor.GRAY)));
				return 1;
			} else if (FlagValue.DEFAULT.getValue().equals(value)) {
				setFlag(setting, null);
				sender.sendMessage(Component.empty()
				    .append(Component.text("Set ", NamedTextColor.GRAY))
				    .append(Component.text(setting, NamedTextColor.AQUA, TextDecoration.BOLD))
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

		throw CommandUtils.fail(sender, "No such setting: " + setting);
	}

	public void playSounds(Player player) {
		for (CSound sound : mSounds) {
			sound.playSound(player);
		}
	}

	public void addSound(Sound sound, float volume, float pitch) {
		mSounds.add(new CSound(sound, volume, pitch));
	}

	public void clearSound() {
		mSounds.clear();
	}

	public boolean soundEmpty() {
		return mSounds.isEmpty();
	}
}
