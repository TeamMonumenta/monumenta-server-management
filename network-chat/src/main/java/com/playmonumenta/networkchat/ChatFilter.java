package com.playmonumenta.networkchat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.channel.ChannelWhisper;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.FileUtils;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.networkchat.utils.ReplacerWithEscape;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.format.NamedTextColor;
import org.apache.commons.text.StringEscapeUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.CommandBlock;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;

// A collection of regex to filter chat
public class ChatFilter {
	public static class ChatFilterResult {
		private boolean mFoundMatch = false;
		private boolean mFoundBadWord = false;
		private boolean mFoundException = false;
		private @Nullable Message mMessage;
		private Component mOriginalComponent;
		private Component mComponent;

		public ChatFilterResult(Message message) {
			mMessage = message;
			mOriginalComponent = message.getMessage();
			mComponent = mOriginalComponent;
		}

		public ChatFilterResult(Component component) {
			mMessage = null;
			mOriginalComponent = component;
			mComponent = component;
		}

		public ChatFilterResult getCleanCopy() {
			ChatFilterResult filterResult = new ChatFilterResult(mComponent);
			filterResult.mOriginalComponent = mOriginalComponent;
			if (mMessage != null) {
				filterResult.mMessage = mMessage;
			}
			return filterResult;
		}

		public void copyResults(ChatFilterResult other) {
			mFoundMatch |= other.mFoundMatch;
			mFoundBadWord |= other.mFoundBadWord;
			mFoundException |= other.mFoundException;
			mComponent = other.mComponent;
		}

		public boolean foundMatch() {
			return mFoundMatch;
		}

		public void foundMatch(boolean value) {
			mFoundMatch = value;
		}

		public boolean foundBadWord() {
			return mFoundBadWord;
		}

		public void foundBadWord(boolean value) {
			mFoundBadWord = value;
		}

		public boolean foundException() {
			return mFoundException;
		}

		public void foundException(boolean value) {
			mFoundException = value;
		}

		public Component originalComponent() {
			return mOriginalComponent;
		}

		public Component component() {
			return mComponent;
		}

		public void component(Component component) {
			mComponent = component;
		}
	}

	public static class ChatFilterPattern {
		private final String mId;
		private final boolean mIsLiteral;
		private final String mPatternString;
		private final Pattern mPattern;
		private final boolean mIsBadWord;
		private String mReplacementMiniMessage;
		private @Nullable String mCommand = null;

		public ChatFilterPattern(CommandSender sender,
		                         String id,
		                         boolean isLiteral,
		                         String regex,
		                         boolean isBadWord) throws WrapperCommandSyntaxException {
			mId = id;
			mIsLiteral = isLiteral;
			mPatternString = regex;
			mIsBadWord = isBadWord;
			Pattern pattern;
			try {
				int flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.MULTILINE;
				if (mIsLiteral) {
					flags |= Pattern.LITERAL;
				}
				pattern = Pattern.compile(mPatternString, flags);
			} catch (PatternSyntaxException e) {
				sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
				throw CommandUtils.fail(sender, "Could not load chat filter " + mId);
			}
			mPattern = pattern;
			if (mIsBadWord) {
				mReplacementMiniMessage = "<red>" + mId + "</red>";
			} else {
				mReplacementMiniMessage = "<bold>$0</bold>";
			}
		}

		public static ChatFilterPattern fromJson(CommandSender sender, JsonObject object) throws Exception {
			String id = object.get("mId").getAsString();
			boolean isLiteral = object.get("mIsLiteral").getAsBoolean();
			String regex = object.get("mPatternString").getAsString();
			boolean isBadWord = object.get("mIsBadWord").getAsBoolean();
			String replacementMiniMessage = object.get("mReplacementMiniMessage").getAsString();
			@Nullable String command = null;
			if (object.has("mCommand")) {
				command = object.get("mCommand").getAsString();
			}

			ChatFilterPattern pattern = new ChatFilterPattern(sender, id, isLiteral, regex, isBadWord);
			pattern.mReplacementMiniMessage = replacementMiniMessage;
			pattern.mCommand = command;
			return pattern;
		}

		public JsonObject toJson() {
			JsonObject object = new JsonObject();
			object.addProperty("mId", mId);
			object.addProperty("mIsLiteral", mIsLiteral);
			object.addProperty("mPatternString", mPatternString);
			object.addProperty("mIsBadWord", mIsBadWord);
			object.addProperty("mReplacementMiniMessage", mReplacementMiniMessage);
			if (mCommand != null) {
				object.addProperty("mCommand", mCommand);
			}
			return object;
		}

		public String id() {
			return mId;
		}

		public String patternString() {
			return mPatternString;
		}

		public boolean isBadWord() {
			return mIsBadWord;
		}

		public String replacementMessage() {
			return mReplacementMiniMessage;
		}

		public ChatFilterPattern replacementMessage(String replacementMiniMessage) {
			mReplacementMiniMessage = replacementMiniMessage;
			return this;
		}

		public @Nullable String command() {
			return mCommand;
		}

		public ChatFilterPattern command(@Nullable String command) {
			mCommand = command;
			return this;
		}

		public void run(CommandSender sender, final ChatFilterResult filterResult) {
			CommandSender callee = CommandUtils.getCallee(sender);
			ReplacerWithEscape replacer = new ReplacerWithEscape(sender, mReplacementMiniMessage);
			final ChatFilterResult localResult = filterResult.getCleanCopy();
			TextReplacementConfig replacementConfig = TextReplacementConfig.builder()
				.match(mPattern)
				.replacement((MatchResult match, TextComponent.Builder textBuilder) -> {
					localResult.foundMatch(true);
					if (mIsBadWord) {
						localResult.foundBadWord(true);
					}
					String content = textBuilder.content();
					String finalContent = content;
					MMLog.finer(() -> "    <- " + finalContent);
					content = mPattern.matcher(content).replaceAll(replacer);
					String finalContent1 = content;
					MMLog.finer(() -> "    -- " + finalContent1);
					Component replacementResult = MessagingUtils.getSenderFmtMinimessage().deserialize(content);
					MMLog.finer(() -> "    -> " + MessagingUtils.getSenderFmtMinimessage().serialize(replacementResult));
					return replacementResult;
				})
				.build(); // deprecation warning is an upstream issue, ignore until fixed upstream

			try {
				MMLog.finer(() -> "  ..." + MessagingUtils.getSenderFmtMinimessage().serialize(localResult.component()));
				localResult.component(localResult.component().replaceText(replacementConfig));
				MMLog.finer(() -> "  ..." + MessagingUtils.getSenderFmtMinimessage().serialize(localResult.component()));

				String plainText = MessagingUtils.plainText(localResult.component());
				String plainReplacement = mPattern.matcher(plainText).replaceAll(replacer);
				if (!plainText.equals(plainReplacement)) {
					localResult.foundMatch(true);
					if (mIsBadWord) {
						localResult.foundBadWord(true);
					}
					localResult.component(MessagingUtils.getSenderFmtMinimessage().deserialize(plainReplacement));
				}
			} catch (Exception ex) {
				if (!filterResult.foundException()) {
					localResult.component(Component.empty()
						.append(Component.text("Error occurred processing the following: ", NamedTextColor.RED))
						.append(filterResult.component()));
				}
				CommandSender consoleSender = Bukkit.getConsoleSender();
				MMLog.warning("An exception occurred processing chat filter "
					+ mId + " on the following message:");
				consoleSender.sendMessage(filterResult.originalComponent());
				MessagingUtils.sendStackTrace(consoleSender, ex);

				// Prevent message from transmitting as a precaution
				localResult.foundException(true);
				filterResult.copyResults(localResult);
				return;
			}

			if (localResult.foundMatch()) {
				if (mCommand != null) {
					String command = mCommand;

					Message message = filterResult.mMessage;
					String channelName = "CHANNEL_NAME_NOT_FOUND";
					boolean isWhisperChannel = false;
					if (message != null) {
						Channel channel = message.getChannel();
						if (channel != null) {
							isWhisperChannel = channel instanceof ChannelWhisper;
							channelName = channel.getFriendlyName();
						}
					}
					command = command.replace("<channel_name>", channelName);

					String senderType;
					String senderUuid = "NotAnEntity";
					command = command.replace("@S", sender.getName());
					if (callee instanceof Entity entity) {
						senderType = entity.getType().key().toString();
						senderUuid = entity.getUniqueId().toString().toLowerCase(Locale.ENGLISH);
					} else if (callee instanceof CommandBlock commandBlock) {
						senderType = commandBlock.getType().key().toString();
					} else {
						senderType = callee.getClass().getName();
					}
					command = command.replace("@T", senderType);
					command = command.replace("@U", senderUuid);
					String originalMessage = MessagingUtils.plainText(localResult.originalComponent());
					String replacedMessage = MessagingUtils.plainText(localResult.component());
					command = command.replace("@OE", StringEscapeUtils.escapeJson(originalMessage));
					command = command.replace("@ME", StringEscapeUtils.escapeJson(replacedMessage));
					command = command.replace("@O", originalMessage);
					command = command.replace("@M", replacedMessage);
					final String finishedCommand = command;
					if (isWhisperChannel && command.contains("auditlogsevereplayer")) {
						/*
						 * Monumenta has chat filter rules configured to automatically elevate messages that match certain patterns to moderators for review.
						 * This happens by running /auditlogsevereplayer with message context, which is provided by a different plugin.
						 * Messages in whisper channels should not be automatically forwarded for review, though other filter actions still apply to them.
						 * The command is instead logged to the console so it can be retrieved if necessary.
						 * TODO: It would be nice to have better infrastructure to make this possible via config, rather than hardcoding it here.
						 */
						MMLog.info(() -> "Skipping automated audit logging for whisper channel. Command would have been '" + finishedCommand + "'");
					} else {
						Bukkit.getScheduler().runTask(NetworkChatPlugin.getInstance(),
							() -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), finishedCommand));
					}
				}
			}
			filterResult.copyResults(localResult);

			MMLog.finer(() -> "- " + mId + ":");
			MMLog.finer(() -> MessagingUtils.getSenderFmtMinimessage().serialize(filterResult.component()));
		}
	}

	private final Map<String, ChatFilterPattern> mFilters = new HashMap<>();

	public static ChatFilter globalFilter(CommandSender sender) {
		ChatFilter filter = new ChatFilter();

		String folderLocation = NetworkChatPlugin.getInstance().getDataFolder() + File.separator + "global_filters";
		File directory = new File(folderLocation);
		if (!directory.isDirectory()) {
			sender.sendMessage(Component.text("global_filters folder does not exist; using default", NamedTextColor.YELLOW));
		} else {
			Gson gson = new Gson();

			ArrayList<File> files = FileUtils.getFilesInDirectory(sender,
				folderLocation,
				".json",
				"Unable to find global filters due to IO error:");
			if (files.isEmpty()) {
				sender.sendMessage(Component.text("global_filters folder has no filter files; using default", NamedTextColor.YELLOW));
			}
			Collections.sort(files);

			for (File file : files) {
				String content;
				try {
					content = FileUtils.readFile(file.getPath());
				} catch (Exception e) {
					sender.sendMessage(Component.text("Failed to load global filter file '" + file.getPath() + "'", NamedTextColor.RED));
					MessagingUtils.sendStackTrace(sender, e);
					continue;
				}

				JsonObject patternObject = gson.fromJson(content, JsonObject.class);
				if (patternObject == null) {
					sender.sendMessage(Component.text("Failed to parse global filter file '" + file.getPath() + "' as JSON object", NamedTextColor.RED));
					continue;
				}

				try {
					ChatFilterPattern pattern = ChatFilterPattern.fromJson(sender, patternObject);
					filter.mFilters.put(pattern.id(), pattern);
				} catch (Exception e) {
					sender.sendMessage(Component.text("Failed to load chat filter pattern: ", NamedTextColor.RED));
					MessagingUtils.sendStackTrace(sender, e);
				}
			}
		}

		if (filter.mFilters.isEmpty()) {
			fallbackGlobalFilter(sender, filter);
		}

		sender.sendMessage(Component.text("Loaded " + filter.mFilters.size() + " filter pattern(s)", NamedTextColor.GREEN));

		return filter;
	}

	private static ChatFilter fallbackGlobalFilter(CommandSender sender, ChatFilter filter) {
		try {
			filter.addFilter(sender,
					"LOG4J_EXPLOIT",
					false,
					"\\{jndi:([^}]+)\\}",
					true)
				.command("auditlogsevereplayer @S \"@T @S attempted a Log4J exploit\"")
				.replacementMessage("<red>Log4J exploit attempt: $1</red>");
		} catch (WrapperCommandSyntaxException e) {
			MessagingUtils.sendStackTrace(sender, e);
		}

		try {
			filter.addFilter(sender,
					"N_WORD",
					false,
					"(^|[^a-z0-9])(n[ .,_-]?[i1][ .,_-]?g[ .,_-]?(?:g[ .,_-]?)+(?:a|[e3][ .,_-]?r)(?:[ .,_-]?[s$5])*)([^a-z0-9]|$)",
					true)
				.command("auditlogsevereplayer @S \"@T @S said the N word in <channel_name>: @OE\"")
				.replacementMessage("$1<red>$2</red>$3");
		} catch (WrapperCommandSyntaxException e) {
			MessagingUtils.sendStackTrace(sender, e);
		}

		try {
			filter.addFilter(sender,
					"F_HOMOPHOBIC",
					false,
					"(^|[^a-z0-9])(f[ .,_-]?[a4][ .,_-]?g[ .,_-]?(?:g[ .,_-]?(?:[o0][ .,_-]?t)?)?(?:[ .,_-]?[s$5])*)([^a-z0-9]|$)",
					true)
				.command("auditlogsevereplayer @S \"@T @S said the homophobic F slur in <channel_name>: @OE\"")
				.replacementMessage("$1<red>$2</red>$3");
		} catch (WrapperCommandSyntaxException e) {
			MessagingUtils.sendStackTrace(sender, e);
		}

		try {
			filter.addFilter(sender,
					"URL",
					false,
					"(?<=^|[^\\\\])https?://[!#-&(-;=?-\\[\\]-z|~]+",
					false)
				.replacementMessage("<blue><u><click:open_url:\"$0\">$0</click></u></blue>");
		} catch (WrapperCommandSyntaxException e) {
			MessagingUtils.sendStackTrace(sender, e);
		}

		try {
			filter.addFilter(sender,
					"Spoiler",
					false,
					"(?<=^|[^\\\\])\\|\\|([^|]*[^|\\s\\\\][^|\\\\]*)\\|\\|",
					false)
				.replacementMessage("<b><hover:show_text:\"$\\1\">SPOILER</hover></b>");
		} catch (WrapperCommandSyntaxException e) {
			MessagingUtils.sendStackTrace(sender, e);
		}

		try {
			filter.addFilter(sender,
					"CodeBlock",
					false,
					"(?<=^|[^\\\\])`([^`]*[^`\\s\\\\][^`\\\\]*)`",
					false)
				.replacementMessage("<font:uniform><hover:show_text:\"Click to copy\nShift+click to insert\n$\\1\"><click:copy_to_clipboard:\"$\\1\"><insert:\"$\\1\">$1</insert></click></hover></font>");
		} catch (WrapperCommandSyntaxException e) {
			MessagingUtils.sendStackTrace(sender, e);
		}

		return filter;
	}

	public static ChatFilter fromJson(CommandSender sender, JsonObject object) {
		ChatFilter filter = new ChatFilter();
		if (object != null) {
			@Nullable JsonArray filters = object.getAsJsonArray("filters");
			if (filters != null) {
				for (JsonElement patternElement : filters) {
					try {
						JsonObject patternObject = patternElement.getAsJsonObject();
						ChatFilterPattern pattern = ChatFilterPattern.fromJson(sender, patternObject);
						filter.mFilters.put(pattern.id(), pattern);
					} catch (Exception e) {
						MMLog.warning("Failed to load chat filter pattern: " + e.getMessage());
					}
				}
			}
		}
		return filter;
	}

	public JsonObject toJson() {
		JsonObject object = new JsonObject();
		if (!mFilters.isEmpty()) {
			JsonArray filters = new JsonArray();
			for (ChatFilterPattern pattern : mFilters.values()) {
				filters.add(pattern.toJson());
			}
			object.add("filters", filters);
		}
		return object;
	}

	public ChatFilter badWordFiltersOnly() {
		ChatFilter result = new ChatFilter();
		for (Map.Entry<String, ChatFilterPattern> filterEntry : mFilters.entrySet()) {
			ChatFilterPattern filterPattern = filterEntry.getValue();
			if (!filterPattern.isBadWord()) {
				continue;
			}
			result.mFilters.put(filterEntry.getKey(), filterPattern);
		}
		return result;
	}

	public ChatFilterPattern addFilter(CommandSender sender,
	                      String id,
	                      boolean isLiteral,
	                      String regex,
	                      boolean isBadWord) throws WrapperCommandSyntaxException {
		ChatFilterPattern filterPattern = new ChatFilterPattern(sender, id, isLiteral, regex, isBadWord);
		mFilters.put(id, filterPattern);
		return filterPattern;
	}

	public void removeFilter(String id) {
		mFilters.remove(id);
	}

	public Map<String, ChatFilterPattern> getFilters() {
		return new HashMap<>(mFilters);
	}

	public @Nullable ChatFilterPattern getFilter(String id) {
		return mFilters.get(id);
	}

	public void run(CommandSender sender, ChatFilterResult filterResult) {
		MMLog.finer("Start:");
		MMLog.finer(() -> MessagingUtils.getSenderFmtMinimessage().serialize(filterResult.component()));
		for (ChatFilterPattern filterPattern : mFilters.values()) {
			filterPattern.run(sender, filterResult);
		}
	}

	public ChatFilterResult run(CommandSender sender, Component component) {
		ChatFilterResult filterResult = new ChatFilterResult(component);
		run(sender, filterResult);
		return filterResult;
	}

	public ChatFilterResult run(CommandSender sender, String plainText) {
		Component component = Component.text(plainText);
		return run(sender, component);
	}

	public boolean hasBadWord(CommandSender sender, Component component) {
		ChatFilterResult filterResult = new ChatFilterResult(component);
		run(sender, filterResult);
		return filterResult.foundException() || filterResult.foundBadWord();
	}
}
