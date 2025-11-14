package com.playmonumenta.networkrelay.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

public class ArgUtils {
	private static final Pattern RE_ALLOWED_WITHOUT_QUOTES = Pattern.compile("[0-9A-Za-z_.+-]+");

	public static boolean requiresQuotes(@Nullable String arg) {
		if (arg == null) {
			return true;
		}
		return !RE_ALLOWED_WITHOUT_QUOTES.matcher(arg).matches();
	}

	public static @Nullable String quote(String arg) {
		if (arg == null) {
			return null;
		}
		return "\"" + arg.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	public static @Nullable String quoteIfNeeded(@Nullable String arg) {
		if (requiresQuotes(arg)) {
			return quote(arg);
		} else {
			return arg;
		}
	}

	public static Collection<String> quote(Collection<String> args) {
		Collection<String> possiblyQuotedArgs = new ArrayList<>();
		for (String arg : args) {
			possiblyQuotedArgs.add(quote(arg));
		}
		return possiblyQuotedArgs;
	}

	public static Collection<String> quoteIfNeeded(Collection<String> args) {
		Collection<String> possiblyQuotedArgs = new ArrayList<>();
		for (String arg : args) {
			possiblyQuotedArgs.add(quoteIfNeeded(arg));
		}
		return possiblyQuotedArgs;
	}

	public static Collection<String> quotedAndUnquoted(Collection<String> args) {
		Collection<String> possiblyQuotedArgs = new ArrayList<>();
		for (String arg : args) {
			quote(arg);
			if (!requiresQuotes(arg)) {
				possiblyQuotedArgs.add(arg);
			}
		}
		return possiblyQuotedArgs;
	}
}
