package com.playmonumenta.common.utils;

import com.google.gson.JsonObject;
import com.playmonumenta.common.MonumentaCommonPlugin;
import com.playmonumenta.common.event.RefreshTimeEvent;
import com.playmonumenta.common.managers.TimeWarpManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class DateUtils {
	// Offset server time to UTC-17 to change when the new day arrives.
	// getDaysSinceEpoch() uses its own perceived epoch,
	// so it should sync up nicely with changes in getDayOfWeek().
	public static String CONFIG_NAME = "utc_offset.json";
	public static final String TIMEZONE_OFFSET_PATH = "timezone_seconds_offset";
	public static final int DEFAULT_SECONDS_OFFSET = -17 * 60 * 60;
	public static ZoneId TIMEZONE = ZoneOffset.ofTotalSeconds(DEFAULT_SECONDS_OFFSET); // Fallback: This value should never be accessed
	public static final LocalDateTime TRUE_EPOCH = localDateTime(1970, 1, 1);

	public static void refreshTime() {
		File configFile = getConfigFile();
		try {
			JsonObject config = FileUtils.readJson(configFile.getAbsolutePath());
			set(config.get(TIMEZONE_OFFSET_PATH).getAsInt());
		} catch (Exception e) {
			MMLog.warning("Failed to load " + CONFIG_NAME + ": " + e.getMessage());
			set(DEFAULT_SECONDS_OFFSET);
		}
	}

	private static File getConfigFile() {
		return new File(MonumentaCommonPlugin.getInstance().getDataFolder(), CONFIG_NAME);
	}

	private static void set(int secondsOffset) {
		TIMEZONE = ZoneOffset.ofTotalSeconds(Math.clamp(secondsOffset, -18 * 60 * 60, 18 * 60 * 60));
		RefreshTimeEvent refreshTimeEvent = new RefreshTimeEvent();
		refreshTimeEvent.callEvent();
	}

	public static LocalDateTime trueUtcDateTime() {
		return LocalDateTime.now(ZoneId.of("UTC"));
	}

	public static LocalDateTime utcDateTime() {
		return trueUtcDateTime().plusSeconds(TimeWarpManager.get());
	}

	public static LocalDateTime trueLocalDateTime() {
		return LocalDateTime.now(TIMEZONE);
	}

	public static LocalDateTime localDateTime() {
		return trueLocalDateTime().plusSeconds(TimeWarpManager.get());
	}

	public static LocalDateTime localDateTime(long dailyVersion) {
		long dayOffset = dailyVersion - getDaysSinceEpoch(TRUE_EPOCH);
		return TRUE_EPOCH.plusDays(dayOffset);
	}

	public static LocalDateTime localDateTime(int year, int month, int dayOfMonth) {
		return LocalDateTime.of(year, month, dayOfMonth, 0, 0, 0);
	}

	public static LocalDateTime startOfNextDay() {
		return localDateTime(getDaysSinceEpoch() + 1);
	}

	public static LocalDateTime startOfNextWeek() {
		return localDateTime(getNextWeeklyVersionStartDate());
	}

	public static int getYear() {
		return LocalDateTime.now(TIMEZONE).getYear();
	}

	public static int getMonth() {
		return LocalDateTime.now(TIMEZONE).getMonthValue();
	}

	public static int getDayOfMonth() {
		return LocalDateTime.now(TIMEZONE).getDayOfMonth();
	}

	// 1 is Sunday, 7 is Saturday
	public static int getDayOfWeek() {
		return getDayOfWeek(localDateTime());
	}

	public static int getDayOfWeek(LocalDateTime localDateTime) {
		// .getValue() gives 1 for Monday, 7 for Sunday, so we cycle the numbers
		return Math.floorMod(localDateTime.getDayOfWeek().getValue(), 7) + 1;
	}

	/** Also known as <code>DailyVersion</code>.
	 * In our specified timezone, how many days we perceive it is since our 1 Jan 1970.
	 * Different timezones have different dates for the same point in time,
	 * so this simple comparison will yield different numbers of days for them. */
	public static long getDaysSinceEpoch() {
		return getDaysSinceEpoch(localDateTime());
	}

	/** In our specified timezone, how many days we perceive it is since our 1 Jan 1970.
	 * Different timezones have different dates for the same point in time,
	 * so this simple comparison will yield different numbers of days for them. */
	public static long getDaysSinceEpoch(LocalDateTime localDateTime) {
		return localDateTime.toLocalDate().toEpochDay();
	}

	/**
	 * Gets the time since the <bold>UTC Epoch.</bold>
	 * @return Number of time units that have passed, rounded down
	 */
	public static long getTimeSinceUTCEpoch(ChronoUnit chronoUnit) {
		return getTimeSinceUTCEpoch(LocalDateTime.now(), chronoUnit);
	}

	/**
	 * Gets the time since the <bold>UTC Epoch.</bold>
	 * @param localDateTime Reference end time to count towards
	 * @return Number of time units that have passed, rounded down
	 */
	public static long getTimeSinceUTCEpoch(LocalDateTime localDateTime, ChronoUnit chronoUnit) {
		return TRUE_EPOCH.until(localDateTime, chronoUnit);
	}

	public static long getMinutesSinceUTCEpoch() {
		return getTimeSinceUTCEpoch(ChronoUnit.MINUTES);
	}

	public static long getSecondsSinceUTCEpoch() {
		// Note: This method is intentionally UTC-only.
		return getTimeSinceUTCEpoch(ChronoUnit.SECONDS);
	}

	public static boolean getAmPm() {
		return getAmPm(localDateTime());
	}

	public static boolean getAmPm(LocalDateTime localDateTime) {
		return getHourOfDay(localDateTime) >= 12;
	}

	public static int getHourOfDay() {
		return getHourOfDay(localDateTime());
	}

	public static int getHourOfDay(LocalDateTime localDateTime) {
		return localDateTime.getHour();
	}

	public static int getHourOfTwelve() {
		return getHourOfTwelve(localDateTime());
	}

	public static int getHourOfTwelve(LocalDateTime localDateTime) {
		int hourOfTwelve = getHourOfDay(localDateTime) % 12;
		return (hourOfTwelve == 0) ? 12 : hourOfTwelve;
	}

	public static int getMinute() {
		return getMinute(localDateTime());
	}

	public static int getMinute(LocalDateTime localDateTime) {
		return localDateTime.getMinute();
	}

	public static int getSecond() {
		return getSecond(localDateTime());
	}

	public static int getSecond(LocalDateTime localDateTime) {
		return localDateTime.getSecond();
	}

	public static int getMs() {
		return getMs(localDateTime());
	}

	// Errorprone warns on getNano() without getSecond(), but this is ok here since we only care about ms component.
	@SuppressWarnings("JavaLocalDateTimeGetNano")
	public static int getMs(LocalDateTime localDateTime) {
		return localDateTime.getNano() / 1000000;
	}

	public static long getWeeklyVersion() {
		return getWeeklyVersion(localDateTime());
	}

	public static long getWeeklyVersion(LocalDateTime localDateTime) {
		return getWeeklyVersion(getDaysSinceEpoch(localDateTime));
	}

	public static long getWeeklyVersion(long dailyVersion) {
		return Math.floorDiv(dailyVersion + 6, 7);
	}

	public static long getDaysIntoWeeklyVersion() {
		return getDaysIntoWeeklyVersion(localDateTime());
	}

	public static long getDaysIntoWeeklyVersion(LocalDateTime localDateTime) {
		return Math.floorMod(localDateTime.toLocalDate().toEpochDay() - 1, 7) + 1L;
	}

	public static long getDaysLeftInWeeklyVersion() {
		return getDaysLeftInWeeklyVersion(localDateTime());
	}

	public static long getDaysLeftInWeeklyVersion(LocalDateTime localDateTime) {
		return 8 - getDaysIntoWeeklyVersion(localDateTime);
	}

	public static long getWeeklyVersionStartDate() {
		return getWeeklyVersionStartDate(localDateTime());
	}

	public static long getWeeklyVersionStartDate(LocalDateTime localDateTime) {
		return getDaysSinceEpoch(localDateTime) - getDaysIntoWeeklyVersion(localDateTime) + 1;
	}

	public static LocalDateTime getWeeklyVersionLocalStartDate() {
		return getWeeklyVersionLocalStartDate(localDateTime());
	}

	public static LocalDateTime getWeeklyVersionLocalStartDate(LocalDateTime localDateTime) {
		return localDateTime(getWeeklyVersionStartDate(localDateTime));
	}

	public static long getWeeklyVersionEndDate() {
		return getWeeklyVersionEndDate(localDateTime());
	}

	public static long getWeeklyVersionEndDate(LocalDateTime localDateTime) {
		return getWeeklyVersionStartDate(localDateTime) + 6;
	}

	public static long getNextWeeklyVersionStartDate() {
		return getNextWeeklyVersionStartDate(localDateTime());
	}

	public static long getNextWeeklyVersionStartDate(LocalDateTime localDateTime) {
		return getWeeklyVersionStartDate(localDateTime) + 7;
	}

	public static LocalDateTime getStartOfMonth() {
		return getStartOfMonth(localDateTime());
	}

	public static LocalDateTime getStartOfMonth(LocalDateTime localDateTime) {
		return LocalDateTime.of(localDateTime.getYear(), localDateTime.getMonth(), 1, 0, 0, 0);
	}

	public static String untilNewDay() {
		long seconds = untilNewDay(ChronoUnit.SECONDS);
		long minutes = seconds / 60L;
		seconds %= 60L;
		long hours = minutes / 60L;
		minutes %= 60L;
		return String.format("%02d:%02d:%02d", hours, minutes, seconds);
	}

	public static long untilNewDay(ChronoUnit unit) {
		return localDateTime().until(startOfNextDay(), unit);
	}

	public static String untilNewWeek() {
		long seconds = untilNewWeek(ChronoUnit.SECONDS);
		long minutes = seconds / 60L;
		seconds %= 60L;
		long hours = minutes / 60L;
		minutes %= 60L;
		if (hours < 24) {
			return String.format("%02d:%02d:%02d", hours, minutes, seconds);
		}
		long days = hours / 24L;
		hours %= 24;
		return String.format("%d days %02d hours", days, hours);
	}

	public static long untilNewWeek(ChronoUnit unit) {
		return localDateTime().until(startOfNextWeek(), unit);
	}


	// Ported directly from DateVersionCommand
	/** Prints information about the date and daily version. */
	public static void debugDate(CommandSender sender, LocalDateTime localDateTime) {
		sender.sendMessage(Component.text("Date version debug:", NamedTextColor.AQUA, TextDecoration.BOLD));
		sender.sendMessage(Component.text("Current Tick: ", NamedTextColor.AQUA)
			.append(Component.text(Bukkit.getCurrentTick(), NamedTextColor.GOLD)));
		sender.sendMessage(Component.text("Now UntilNextDay: ", NamedTextColor.AQUA)
			.append(Component.text(DateUtils.untilNewDay(), NamedTextColor.GOLD)));
		sender.sendMessage(Component.text("Now UntilNextWeek: ", NamedTextColor.AQUA)
			.append(Component.text(DateUtils.untilNewWeek(), NamedTextColor.GOLD)));
		sender.sendMessage(Component.empty());

		sender.sendMessage(Component.text("For ", NamedTextColor.AQUA)
			.append(Component.text(localDateTime.toString(), NamedTextColor.GOLD))
			.append(Component.text(":")));
		sender.sendMessage(Component.text("DailyVersion: ", NamedTextColor.AQUA)
			.append(Component.text(DateUtils.getDaysSinceEpoch(localDateTime), NamedTextColor.GOLD)));
		sender.sendMessage(Component.text("WeeklyVersion: ", NamedTextColor.AQUA)
			.append(Component.text(DateUtils.getWeeklyVersion(localDateTime), NamedTextColor.GOLD)));
		sender.sendMessage(Component.text("DaysIntoWeeklyVersion: ", NamedTextColor.AQUA)
			.append(Component.text(DateUtils.getDaysIntoWeeklyVersion(localDateTime), NamedTextColor.GOLD)));
		sender.sendMessage(Component.text("DaysLeftInWeeklyVersion: ", NamedTextColor.AQUA)
			.append(Component.text(DateUtils.getDaysLeftInWeeklyVersion(localDateTime), NamedTextColor.GOLD)));
		sender.sendMessage(Component.text("WeeklyVersionStartDate: ", NamedTextColor.AQUA)
			.append(Component.text(DateUtils.getWeeklyVersionStartDate(localDateTime), NamedTextColor.GOLD)));
		sender.sendMessage(Component.text("WeeklyVersionEndDate: ", NamedTextColor.AQUA)
			.append(Component.text(DateUtils.getWeeklyVersionEndDate(localDateTime), NamedTextColor.GOLD)));
		sender.sendMessage(Component.text("NextWeeklyVersionStartDate: ", NamedTextColor.AQUA)
			.append(Component.text(DateUtils.getNextWeeklyVersionStartDate(localDateTime), NamedTextColor.GOLD)));
	}
}
