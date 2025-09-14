package com.playmonumenta.networkchat.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public class FileUtils {
	public static String readFile(String fileName) throws Exception {
		// Do not attempt to catch exceptions here - let them propagate to the caller
		File file;

		if (fileName == null || fileName.isEmpty()) {
			throw new Exception("Filename is null or empty");
		}

		file = new File(fileName);
		if (!file.exists()) {
			throw new FileNotFoundException("File '" + fileName + "' does not exist");
		}

		InputStreamReader reader = null;
		final int bufferSize = 1024;
		final char[] buffer = new char[bufferSize];
		final StringBuilder content = new StringBuilder();

		try {
			reader = new InputStreamReader(new FileInputStream(fileName), StandardCharsets.UTF_8);
			while (true) {
				int rsz = reader.read(buffer, 0, buffer.length);
				if (rsz < 0) {
					break;
				}
				content.append(buffer, 0, rsz);
			}
		} finally {
			if (reader != null) {
				reader.close();
			}
		}

		return content.toString();
	}

	/**
	 * Returns a list of all files in the directory that are both regular files
	 * AND end with the specified string
	 */
	public static ArrayList<File> getFilesInDirectory(String folderPath,
	                                                  String endsWith) throws IOException {
		ArrayList<File> matchedFiles = new ArrayList<>();

		try (Stream<Path> stream = Files.walk(Paths.get(folderPath), 100, FileVisitOption.FOLLOW_LINKS)) {
			stream.forEach(path -> {
				if (path.toString().toLowerCase().endsWith(endsWith)) {
					// Note - this will pass directories that end with .json back to the caller too
					matchedFiles.add(path.toFile());
				}
			});
		}

		return matchedFiles;
	}

	public static ArrayList<File> getFilesInDirectory(CommandSender sender,
	                                                  String folderPath,
	                                                  String endsWith,
	                                                  String exceptionMessage) {
		try {
			return getFilesInDirectory(folderPath, endsWith);
		} catch (IOException e) {
			sender.sendMessage(Component.text(exceptionMessage, NamedTextColor.RED));
			return new ArrayList<>();
		}
	}
}
