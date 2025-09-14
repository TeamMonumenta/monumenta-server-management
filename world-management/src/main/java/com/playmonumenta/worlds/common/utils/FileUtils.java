package com.playmonumenta.worlds.common.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.stream.Stream;

public class FileUtils {
	public static void deleteRecursively(Path path) throws IOException {
		if (Files.isSymbolicLink(path)) {
			Files.delete(path);
			return;
		}
		if (Files.isDirectory(path)) {
			try (Stream<Path> subPathStream = Files.list(path)) {
				Iterator<Path> it = subPathStream.iterator();
				while (it.hasNext()) {
					deleteRecursively(it.next());
				}
			}
			Files.delete(path);
			return;
		}
		Files.delete(path);
	}
}
