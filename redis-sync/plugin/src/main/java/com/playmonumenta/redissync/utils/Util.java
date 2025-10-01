package com.playmonumenta.redissync.utils;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

public class Util {
	public static <K, V> ImmutableMap<K, V> extend(ImmutableMap<K, V> map, K key, V value) {
		final var hm = new Object2ObjectOpenHashMap<K, V>(map.size() + 1);
		hm.putAll(map);
		hm.put(key, value);
		return ImmutableMap.<K, V>builder().putAll(hm).build();
	}
}
