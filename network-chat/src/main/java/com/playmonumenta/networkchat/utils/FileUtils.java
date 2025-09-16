package com.playmonumenta.networkchat.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public class FileUtils {
	public static String readFile(String fileName) throws Exception {
		return Files.readString(Path.of(fileName));
	}

	/**
	 * Returns a list of all files in the directory that are both regular files
	 * AND end with the specified string
	 */
	public static List<File> getFilesInDirectory(String folderPath,
	                                                  String endsWith) throws IOException {
		ArrayList<File> matchedFiles = new ArrayList<>();

		try (Stream<Path> stream = Files.walk(Paths.get(folderPath), 100, FileVisitOption.FOLLOW_LINKS)) {
			stream.forEach(path -> {
				if (path.toString().toLowerCase(Locale.ROOT).endsWith(endsWith)) {
					// Note - this will pass directories that end with .json back to the caller too
					matchedFiles.add(path.toFile());
				}
			});
		}

		return matchedFiles;
	}

	public static List<File> getFilesInDirectory(CommandSender sender,
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
