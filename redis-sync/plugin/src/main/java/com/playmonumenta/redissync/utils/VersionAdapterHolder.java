package com.playmonumenta.redissync.utils;

import java.util.logging.Logger;
import org.bukkit.plugin.Plugin;

public class VersionAdapterHolder<T> {
	private T mAdapter;
	private final Class<T> mClass;
	private final Plugin mPlugin;

	public VersionAdapterHolder(Class<T> clazz, Plugin plugin) {
		this.mClass = clazz;
		this.mPlugin = plugin;
	}

	public T get() {
		if (mAdapter == null) {
			// TODO: this is not support for >=1.20.4
			String packageName = mPlugin.getServer().getClass().getPackage().getName();
			String version = packageName.substring(packageName.lastIndexOf('.') + 1);

			try {
				final var implClass = Class.forName("%s_%s".formatted(mClass.getName(), version));
				mAdapter = mClass.cast(implClass.getConstructor(Logger.class).newInstance(mPlugin.getLogger()));
			} catch (final Exception e) {
				throw new UnsupportedOperationException("Server version %s is not supported!".formatted(version), e);
			}
		}

		return mAdapter;
	}
}
