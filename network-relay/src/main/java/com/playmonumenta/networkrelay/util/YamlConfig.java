package com.playmonumenta.networkrelay.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public class YamlConfig {
	public static Map<String, Object> loadWithFallback(Logger logger,
	                                                   File configFile,
	                                                   Class<?> networkRelayClass,
	                                                   String resourcePath) {
		try (InputStream defaultConfig = networkRelayClass.getResourceAsStream(resourcePath)) {
			return loadWithFallback(logger, configFile, defaultConfig);
		} catch (IOException e) {
			logger.warning("Could not load default config from NetworkRelay plugin");
			return loadWithFallback(logger, configFile, null);
		}
	}

	public static Map<String, Object> loadWithFallback(Logger logger,
	                                                   File configFile,
	                                                   @Nullable InputStream defaultConfig) {
		/* Create the config file & directories if it does not exist */
		if (!configFile.exists()) {
			try {
				// Create parent directories if they do not exist
				//noinspection ResultOfMethodCallIgnored
				configFile.getParentFile().mkdirs();

				// Copy the default config file
				Files.copy(Objects.requireNonNull(defaultConfig),
					configFile.toPath(),
					StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException | NullPointerException unused) {
				logger.severe("Failed to create configuration file");
				return new HashMap<>();
			}
		}

		Map<?, ?> internalYaml;
		try (FileInputStream stream = new FileInputStream(configFile)) {
			try (BufferedReader input = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
				StringBuilder builder = new StringBuilder();
				String line;
				while ((line = input.readLine()) != null) {
					builder.append(line);
					builder.append('\n');
				}
				LoaderOptions loaderOptions = new LoaderOptions();
				loaderOptions.setMaxAliasesForCollections(Integer.MAX_VALUE);
				Yaml yaml = new Yaml(new SafeConstructor(loaderOptions));

				internalYaml = yaml.load(builder.toString());
			}
		} catch (FileNotFoundException fileNotFoundException) {
			logger.log(Level.SEVERE, "Configuration file not found");
			return new HashMap<>();
		} catch (IOException e) {
			logger.log(Level.SEVERE, "Configuration file could not be read");
			return new HashMap<>();
		}

		Map<String, Object> result = new HashMap<>();
		for (Map.Entry<?, ?> entry : internalYaml.entrySet()) {
			String key = entry.getKey().toString();
			Object value = entry.getValue();
			result.put(key, value);
		}
		return result;
	}
}
