package com.playmonumenta.structures.utils;

import java.io.File;
import java.nio.file.Paths;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.bukkit.plugin.Plugin;

public class CommandUtils {
	private static final String BASE_FOLDER_NAME = "structures";

	public static String getSchematicPath(Plugin plugin, String baseName) {
		return Paths.get(plugin.getDataFolder().toString(), BASE_FOLDER_NAME, baseName + ".schematic").toString();
	}

	public static File getAndValidateSchematicPath(Plugin plugin, @Nullable String baseName, boolean failIfNotExist) throws Exception {
		final Pattern invalidPathPattern = Pattern.compile("[^-/_a-zA-Z0-9]");

		if (baseName == null || baseName.isEmpty()) {
			throw new Exception("Path is null or empty");
		}

		if (invalidPathPattern.matcher(baseName).find()) {
			throw new Exception("Path contains illegal characters");
		}

		if (baseName.contains("..")) {
			throw new Exception("Path cannot contain '..'");
		}

		final String fileName = getSchematicPath(plugin, baseName);
		File file = new File(fileName);
		if (failIfNotExist && !file.exists()) {
			throw new Exception("Path '" + baseName + "' does not exist (full path '" + fileName + "')");
		}
		return file;
	}
}
