package com.playmonumenta.common;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

public class MMLog {
	private static final class CustomLogger extends Logger {
		private final Logger mLogger;
		private Level mLevel;

		private CustomLogger(Logger logger, Level level) {
			super(logger.getName(), logger.getResourceBundleName());
			mLogger = logger;
			mLevel = level;
		}

		@Override
		public void setLevel(Level level) {
			mLevel = level;
		}

		@Override
		public Level getLevel() {
			return mLevel;
		}

		@Override
		public void finest(Supplier<String> msg) {
			if (mLevel.equals(Level.FINEST)) {
				mLogger.info(msg);
			}
		}

		@Override
		public void finest(String msg) {
			if (mLevel.equals(Level.FINEST)) {
				mLogger.info(msg);
			}
		}

		public void finest(String msg, Throwable thrown) {
			if (mLevel.equals(Level.FINEST)) {
				mLogger.log(Level.INFO, msg, thrown);
			}
		}

		@Override
		public void finer(Supplier<String> msg) {
			if (mLevel.equals(Level.FINER) || mLevel.equals(Level.FINEST)) {
				mLogger.info(msg);
			}
		}

		@Override
		public void finer(String msg) {
			if (mLevel.equals(Level.FINER) || mLevel.equals(Level.FINEST)) {
				mLogger.info(msg);
			}
		}

		public void finer(String msg, Throwable thrown) {
			if (mLevel.equals(Level.FINER) || mLevel.equals(Level.FINEST)) {
				mLogger.log(Level.INFO, msg, thrown);
			}
		}

		@Override
		public void fine(Supplier<String> msg) {
			if (mLevel.equals(Level.FINE) || mLevel.equals(Level.FINER) || mLevel.equals(Level.FINEST)) {
				mLogger.info(msg);
			}
		}

		@Override
		public void fine(String msg) {
			if (mLevel.equals(Level.FINE) || mLevel.equals(Level.FINER) || mLevel.equals(Level.FINEST)) {
				mLogger.info(msg);
			}
		}

		public void fine(String msg, Throwable thrown) {
			if (mLevel.equals(Level.FINE) || mLevel.equals(Level.FINER) || mLevel.equals(Level.FINEST)) {
				mLogger.log(Level.INFO, msg, thrown);
			}
		}

		@Override
		public void info(Supplier<String> msg) {
			mLogger.info(msg);
		}

		@Override
		public void info(String msg) {
			mLogger.info(msg);
		}

		public void info(String msg, Throwable thrown) {
			mLogger.log(Level.INFO, msg, thrown);
		}

		@Override
		public void warning(Supplier<String> msg) {
			mLogger.warning(msg);
		}

		@Override
		public void warning(String msg) {
			mLogger.warning(msg);
		}

		@Override
		public void severe(Supplier<String> msg) {
			mLogger.severe(msg);
		}

		@Override
		public void severe(String msg) {
			mLogger.severe(msg);
		}

		@Override
		public void log(Level level, String msg, Throwable thrown) {
			if (level.intValue() >= Level.INFO.intValue()) {
				mLogger.log(level, msg, thrown);
			} else if (level.intValue() >= mLevel.intValue()) {
				mLogger.log(Level.INFO, msg, thrown);
			}
		}
	}

	private final CustomLogger mCustomLogger;

	public MMLog(JavaPlugin plugin, String commandName) {
		mCustomLogger = new CustomLogger(plugin.getLogger(), Level.INFO);
		new CommandAPICommand(commandName)
			.withSubcommand(new CommandAPICommand("changeloglevel")
				.withPermission(CommandPermission.fromString(commandName + ".changeloglevel"))
				.withSubcommand(new CommandAPICommand("INFO")
					.executes((sender, args) -> {
						mCustomLogger.setLevel(Level.INFO);
					}))
				.withSubcommand(new CommandAPICommand("FINE")
					.executes((sender, args) -> {
						mCustomLogger.setLevel(Level.FINE);
					}))
				.withSubcommand(new CommandAPICommand("FINER")
					.executes((sender, args) -> {
						mCustomLogger.setLevel(Level.FINER);
					}))
				.withSubcommand(new CommandAPICommand("FINEST")
					.executes((sender, args) -> {
						mCustomLogger.setLevel(Level.FINEST);
					}))
			).register();
	}

	public Logger asLogger() {
		return mCustomLogger;
	}

	public void setLevel(Level level) {
		mCustomLogger.setLevel(level);
	}

	public boolean isLevelEnabled(Level level) {
		return level.intValue() >= mCustomLogger.getLevel().intValue();
	}

	public void finest(Supplier<String> msg) {
		mCustomLogger.finest(msg);
	}

	public void finest(String msg) {
		mCustomLogger.finest(msg);
	}

	public void finest(String msg, Throwable throwable) {
		mCustomLogger.finest(msg, throwable);
	}

	public void finer(Supplier<String> msg) {
		mCustomLogger.finer(msg);
	}

	public void finer(String msg) {
		mCustomLogger.finer(msg);
	}

	public void finer(String msg, Throwable throwable) {
		mCustomLogger.finer(msg, throwable);
	}

	public void fine(Supplier<String> msg) {
		mCustomLogger.fine(msg);
	}

	public void fine(String msg) {
		mCustomLogger.fine(msg);
	}

	public void fine(String msg, Throwable throwable) {
		mCustomLogger.fine(msg, throwable);
	}

	public void info(String msg) {
		mCustomLogger.info(msg);
	}

	public void info(Supplier<String> msg) {
		mCustomLogger.info(msg);
	}

	public void info(String msg, Throwable throwable) {
		mCustomLogger.info(msg, throwable);
	}

	public void warning(Supplier<String> msg) {
		mCustomLogger.warning(msg);
	}

	public void warning(String msg) {
		mCustomLogger.warning(msg);
	}

	public void warning(String msg, Throwable throwable) {
		mCustomLogger.log(Level.WARNING, msg, throwable);
	}

	public void severe(Supplier<String> msg) {
		mCustomLogger.severe(msg);
	}

	public void severe(String msg) {
		mCustomLogger.severe(msg);
	}

	public void severe(String msg, Throwable throwable) {
		mCustomLogger.log(Level.SEVERE, msg, throwable);
	}
}
