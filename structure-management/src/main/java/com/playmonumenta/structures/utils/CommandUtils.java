package com.playmonumenta.structures.utils;

import com.playmonumenta.scriptedquests.utils.MMLog;
import com.playmonumenta.structures.StructuresPlugin;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.TextArgument;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public class CommandUtils {
	private static final String BASE_FOLDER_NAME = "structures";
	private static final String SCHEMATIC_EXTENSION = ".schematic";

	private static List<String> STRUCTURE_PATH_SUGGESTIONS = new ArrayList<>();

	public static String getSchematicPath(Plugin plugin, String baseName) {
		return Paths.get(plugin.getDataFolder().toString(), BASE_FOLDER_NAME, baseName + SCHEMATIC_EXTENSION).toString();
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

	public static Argument<String> getStructurePathArgument() {
		return new TextArgument("path").includeSuggestions(ArgumentSuggestions.stringCollection(info -> STRUCTURE_PATH_SUGGESTIONS));
	}

	public static void reloadStructurePathSuggestions() {
		Path base = Paths.get(StructuresPlugin.getInstance().getDataFolder().toString(), BASE_FOLDER_NAME);
		STRUCTURE_PATH_SUGGESTIONS =  getAllPaths(base, base.toString().length() + 1, SCHEMATIC_EXTENSION.length()); // + 1 for the slash
	}

	private static List<String> getAllPaths(Path base, int startCutoff, int endCutoff) {
		List<String> paths = new ArrayList<>();

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(base)) {
			for (Path path : stream) {
				if (Files.isDirectory(path)) {
					paths.addAll(getAllPaths(path, startCutoff, endCutoff));
				} else if (Files.isRegularFile(path)) {
					String s = path.toString();
					if (s.endsWith(SCHEMATIC_EXTENSION)) {
						paths.add("\"" + s.substring(startCutoff, s.length() - endCutoff) + "\"");
					}
				}
			}
		} catch (IOException ex) {
			MMLog.warning("Encountered IOException finding path suggestions: ");
			ex.printStackTrace();
		}

		return paths;
	}
}
