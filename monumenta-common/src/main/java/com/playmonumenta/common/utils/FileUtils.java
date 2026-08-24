package com.playmonumenta.common.utils;

import com.google.common.base.Ascii;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class FileUtils {
	public static void writeFile(Path fileName, String contents) throws IOException {
		// Do not attempt to catch exceptions here - let them propagate to the caller
		Files.createDirectories(fileName.getParent());
		Files.writeString(fileName, contents);
	}

	/**
	 * Returns a list of all files in the directory that are both regular files
	 * AND end with the specified string
	 */
	public static List<Path> getFilesInDirectory(Path folderPath,
												 String endsWith) throws IOException {
		ArrayList<Path> matchedFiles = new ArrayList<>();

		try (Stream<Path> stream = Files.walk(folderPath, 100, FileVisitOption.FOLLOW_LINKS)) {
			stream.forEach(path -> {
				if (Ascii.toLowerCase(path.toString()).endsWith(endsWith) && !path.toFile().isDirectory()) {
					// Note - this will pass directories that end with .json back to the caller too
					matchedFiles.add(path);
				}
			});
		}

		return matchedFiles;
	}

	public static void writeJson(String fileName, JsonElement json) throws IOException {
		writeJson(fileName, json, true);
	}

	public static void writeJson(String fileName, JsonElement json, boolean escapeHtmlCharacters) throws IOException {
		// Do not attempt to catch exceptions here - let them propagate to the caller
		Path file = Path.of(fileName);

		if (!Files.exists(file)) {
			Files.createDirectories(file.getParent());
		}

		Gson gson;
		try (var writer = Files.newBufferedWriter(file)) {
			GsonBuilder gsonBuilder = new GsonBuilder();
			if (!escapeHtmlCharacters) {
				gsonBuilder.disableHtmlEscaping();
			}
			gson = gsonBuilder.create();
			gson.toJson(json, writer);
		}
	}

	public static void writeJsonSafely(String fileName, JsonObject json, boolean escapeHtmlCharacters) throws IOException {
		String tempFileName = fileName + ".tmp";
		writeJson(tempFileName, json, escapeHtmlCharacters);

		File file = new File(fileName);
		File tempFile = new File(tempFileName);

		if (file.isFile()) {
			if (!file.delete()) {
				MMLog.warning("Failed to delete " + fileName + " before replacing it");
			}
		}

		if (!tempFile.renameTo(file)) {
			MMLog.warning("Failed to rename " + tempFileName + " to " + fileName);
		}
	}

	public static JsonObject readJson(String fileName) throws Exception {
		// Do not attempt to catch exceptions here - let them propagate to the caller

		Gson gson = new Gson();

		Reader reader = Files.newBufferedReader(Paths.get(fileName));

		return gson.fromJson(reader, JsonObject.class);

	}
}
