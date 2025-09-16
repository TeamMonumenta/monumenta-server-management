package com.playmonumenta.structures.utils;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import org.slf4j.Logger;

public class Services {
	public interface ServiceProvider<T> {
		int priority();

		T createService();
	}

	public static <T, U extends ServiceProvider<T>> Optional<T> loadService(Logger logger, Class<U> service) {
		return ServiceLoader.load(service)
			.stream()
			.map(provider -> {
				try {
					return provider.get();
				} catch (ServiceConfigurationError e) {
					logger.warn("while loading service: ", e);
					return null;
				}
			})
			.filter(Objects::nonNull)
			.max(Comparator.comparingInt(ServiceProvider::priority))
			.map(ServiceProvider::createService);
	}
}
