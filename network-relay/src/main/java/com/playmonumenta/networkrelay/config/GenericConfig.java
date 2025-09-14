package com.playmonumenta.networkrelay.config;

import com.playmonumenta.networkrelay.util.YamlConfig;
import java.io.File;
import java.util.Map;
import java.util.logging.Logger;

public class GenericConfig extends CommonConfig {
	public GenericConfig(Logger logger, File configFile, Class<?> networkRelayClass, String resourcePath) {
		Map<String, Object> config = YamlConfig.loadWithFallback(logger, configFile, networkRelayClass, resourcePath);
		loadCommon(logger, config);
	}
}
