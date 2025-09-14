package com.playmonumenta.redissync.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

// A data structure used to efficiently complete many strings, and/or map them to values.
public class Trie<V> implements Cloneable {
	private final Map<Character, Trie<V>> mChildren;
	private @Nullable V mValue;
	private final int mDepth;

	private Trie(int depth) {
		mChildren = new ConcurrentHashMap<>();
		mValue = null;
		mDepth = depth;
	}

	public Trie() {
		mChildren = new ConcurrentHashMap<>();
		mValue = null;
		mDepth = 0;
	}

	public Trie(Map<String, ? extends V> m) {
		mChildren = new ConcurrentHashMap<>();
		mValue = null;
		mDepth = 0;
		putAll(m);
	}

	public void clear() {
		mChildren.clear();
		mValue = null;
	}

	@SuppressWarnings("MethodDoesntCallSuperMethod")
	@Override
	public Trie<V> clone() {
		Trie<V> result = new Trie<>(mDepth);
		result.mValue = mValue;
		for (Map.Entry<Character, Trie<V>> entry : mChildren.entrySet()) {
			Character c = entry.getKey();
			result.mChildren.put(c, entry.getValue().clone());
		}
		return result;
	}

	public boolean containsKey(String key) {
		if (key == null || key.length() == mDepth) {
			return mValue != null;
		}
		Character c = key.charAt(mDepth);
		Trie<V> child = mChildren.get(c);
		if (child == null) {
			return false;
		}
		return child.containsKey(key);
	}

	public @Nullable String closestKey(String key) {
		if (key == null) {
			key = "";
		}
		String result = null;
		if (key.length() > mDepth) {
			Character c = key.charAt(mDepth);
			Trie<V> child = mChildren.get(c);
			if (child != null) {
				String childClosestKey = child.closestKey(key);
				if (childClosestKey != null) {
					result = c + child.closestKey(key);
				}
			}
		}
		if (result == null && mValue != null) {
			result = "";
		}
		return result;
	}

	public @Nullable V get(String key) {
		if (key == null || key.length() == mDepth) {
			return mValue;
		}

		Character c = key.charAt(mDepth);
		Trie<V> child = mChildren.get(c);
		if (child != null) {
			return child.get(key);
		}

		return null;
	}

	public boolean isEmpty() {
		return mValue == null && mChildren.isEmpty();
	}

	/* NOTE: Not a set view of the map */
	public Set<String> keySet() {
		Set<String> result = new HashSet<>();
		if (mValue != null) {
			result.add("");
		}

		for (Map.Entry<Character, Trie<V>> entry : mChildren.entrySet()) {
			String c = String.valueOf(entry.getKey());
			Trie<V> child = entry.getValue();
			for (String subKey : child.keySet()) {
				result.add(c + subKey);
			}
		}

		return result;
	}

	public @Nullable V put(String key, V value) {
		if (value == null) {
			remove(key);
			return null;
		}

		if (key == null || key.length() == mDepth) {
			mValue = value;
			return mValue;
		}

		Character c = key.charAt(mDepth);
		Trie<V> child = mChildren.computeIfAbsent(c, k -> new Trie<>(mDepth + 1));
		return child.put(key, value);
	}

	public void putAll(Map<? extends String, ? extends V> m) {
		for (Map.Entry<? extends String, ? extends V> entry : m.entrySet()) {
			String key = entry.getKey();
			V value = entry.getValue();
			put(key, value);
		}
	}

	public @Nullable V remove(String key) {
		if (key == null || key.length() == mDepth) {
			mValue = null;
			return null;
		}

		Character c = key.charAt(mDepth);
		Trie<V> child = mChildren.get(c);
		if (child == null) {
			return null;
		}
		V value = child.remove(key);
		if (child.isEmpty()) {
			mChildren.remove(c);
		}
		return value;
	}

	public int size() {
		int result = (mValue != null) ? 1 : 0;

		for (Trie<V> child : mChildren.values()) {
			result += child.size();
		}

		return result;
	}

	public List<String> suggestions(String start, int limit) {
		if (limit <= 0) {
			return new ArrayList<>();
		}

		if (start == null) {
			start = "";
		}

		if (start.length() > mDepth) {
			Character c = start.charAt(mDepth);
			Trie<V> child = mChildren.get(c);
			if (child == null) {
				return new ArrayList<>();
			}
			return child.suggestions(start, limit);
		}

		List<String> result = new ArrayList<>();

		if (mValue != null) {
			result.add(start);
		}

		for (Map.Entry<Character, Trie<V>> entry : mChildren.entrySet()) {
			if (result.size() >= limit) {
				return result;
			}

			Character c = entry.getKey();
			Trie<V> child = entry.getValue();
			result.addAll(child.suggestions(start + c, limit - result.size()));
		}

		return result;
	}
}
