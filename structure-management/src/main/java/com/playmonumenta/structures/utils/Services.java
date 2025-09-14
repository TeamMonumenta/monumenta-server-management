package com.playmonumenta.structures.utils;

import java.util.Comparator;
import java.util.NoSuchElementException;
import java.util.ServiceLoader;

public class Services {
	public interface ServiceProvider<T> {
		int priority();

		T createService();
	}

	public static <T, U extends ServiceProvider<T>> T loadService(Class<U> service) {
		return ServiceLoader.load(service)
			.stream()
			.map(ServiceLoader.Provider::get)
			.max(Comparator.comparingInt(ServiceProvider::priority))
			.orElseThrow(() -> new NoSuchElementException("could not locate service implementation for: " + service))
			.createService();
	}
}
