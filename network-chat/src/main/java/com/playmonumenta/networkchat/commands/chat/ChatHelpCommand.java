package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.FileUtils;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

public class ChatHelpCommand {
	static class HelpTreeNode extends TreeMap<String, HelpTreeNode> {
		public @Nullable String mResourcePath = null;
		public @Nullable String mExternalPath = null;
	}

	public static void register(NetworkChatPlugin plugin, final @Nullable ZipFile zip) {
		if (NetworkChatProperties.getReplaceHelpCommand()) {
			CommandAPI.unregister("help", true);
		}

		// Start loading entries for help command
		final HelpTreeNode helpTree = new HelpTreeNode();

		// Load from plugin jar (null if unable to load and exception has been logged)
		if (zip != null) {
			for (Enumeration<? extends ZipEntry> zipEntries = zip.entries(); zipEntries.hasMoreElements(); ) {
				ZipEntry entry = zipEntries.nextElement();
				String resourceName = entry.getName();
				if (!resourceName.startsWith("help/") || resourceName.endsWith("/")) {
					continue;
				}
				HelpTreeNode node = helpTree;
				for (String argString : resourceName.substring(5).split("/")) {
					if (argString.endsWith(".txt")) {
						argString = argString.substring(0, argString.length() - 4);
					}
					@Nullable HelpTreeNode testNode = node.get(argString);
					if (testNode == null) {
						testNode = new HelpTreeNode();
						node.put(argString, testNode);
					}
					node = testNode;
				}
				node.mResourcePath = resourceName;
			}
		}

		// Load from plugin data folder
		String folderLocation = plugin.getDataFolder() + File.separator + "help";
		ArrayList<File> listOfFiles;
		try {
			File directory = new File(folderLocation);
			if (!directory.exists()) {
				if (directory.mkdirs()) {
					MMLog.info("Created plugin help directory.");
				}
			}

			listOfFiles = FileUtils.getFilesInDirectory(folderLocation, ".txt");
		} catch (IOException e) {
			MMLog.severe("Caught exception trying to load help files from plugin folder.");
			return;
		}
		Collections.sort(listOfFiles);
		for (File file : listOfFiles) {
			String filePath = file.toString();
			String subPath = filePath.substring(folderLocation.length() + 1, filePath.length() - 4);

			HelpTreeNode node = helpTree;
			for (String argString : subPath.split("/")) {
				@Nullable HelpTreeNode testNode = node.get(argString);
				if (testNode == null) {
					testNode = new HelpTreeNode();
					node.put(argString, testNode);
				}
				node = testNode;
			}
			node.mExternalPath = filePath;
		}

		// Register all discovered help entries
		registerHelpCommandNode(zip, new ArrayList<>(), helpTree);
	}

	private static void registerHelpCommandNode(final @Nullable ZipFile zip, final List<String> argStrings, final HelpTreeNode node) {
		List<Argument<?>> arguments = new ArrayList<>();
		for (String arg : argStrings) {
			arguments.add(new LiteralArgument(arg));
		}
		CommandAPICommand helpCommand = new CommandAPICommand("help")
			.withArguments(arguments)
			.executesNative((sender, args) -> {
				if (CommandUtils.checkSudoCommandDisallowed(sender)) {
					throw CommandUtils.fail(sender, "You may not show other players help info.");
				}
				CommandSender callee = CommandUtils.getCallee(sender);

				// Title
				StringBuilder titleBuilder = new StringBuilder("/").append(ChatCommand.COMMAND).append(" help");
				for (String arg : argStrings) {
					titleBuilder.append(' ').append(arg);
				}
				titleBuilder.append(':');
				callee.sendMessage(Component.text(titleBuilder.toString(), NamedTextColor.GOLD, TextDecoration.BOLD));

				// Node's help info (if any)
				@Nullable BufferedReader helpFile = null;
				if (node.mExternalPath != null) {
					try {
						helpFile = new BufferedReader(new InputStreamReader(new FileInputStream(node.mExternalPath), StandardCharsets.UTF_8));
					} catch (IOException ex) {
						callee.sendMessage(Component.text("Unable to load server-specific help file. Attempting to load default help file...", NamedTextColor.RED));
					}
				}
				if (helpFile == null && node.mResourcePath != null && zip != null) {
					ZipEntry zipEntry = zip.getEntry(node.mResourcePath);
					try {
						helpFile = new BufferedReader(new InputStreamReader(zip.getInputStream(zipEntry), StandardCharsets.UTF_8));
					} catch (IOException ex) {
						throw CommandUtils.fail(callee, "Unable to load help file. This shard may need to restart.");
					}
				}
				if (helpFile != null) {
					try {
						for (@Nullable String line; (line = helpFile.readLine()) != null; ) {
							callee.sendMessage(Component.empty()
								.color(NamedTextColor.GREEN)
								.append(MessagingUtils
									.getSenderFmtMinimessage()
									.deserialize(line.replace("\\n", "\n"))));
						}
					} catch (IOException ex) {
						throw CommandUtils.fail(callee, "Failed to read all lines from help file. This shard may need to restart.");
					}
				}

				// Back link (if applicable)
				if (!argStrings.isEmpty()) {
					StringBuilder parentCmdBuilder = new StringBuilder("/")
						.append(ChatCommand.COMMAND)
						.append(" help");
					for (String arg : argStrings.subList(0, argStrings.size() - 1)) {
						parentCmdBuilder.append(' ').append(arg);
					}
					callee.sendMessage(Component.text("[Back]", NamedTextColor.LIGHT_PURPLE)
						.clickEvent(ClickEvent.runCommand(parentCmdBuilder.toString())));
				}

				// Child links
				for (String childArg : node.keySet()) {
					StringBuilder childCmdBuilder = new StringBuilder("/")
						.append(ChatCommand.COMMAND)
						.append(" help");
					for (String arg : argStrings) {
						childCmdBuilder.append(' ').append(arg);
					}
					childCmdBuilder.append(' ').append(childArg);
					callee.sendMessage(Component.text("[" + childArg + "]", NamedTextColor.LIGHT_PURPLE)
						.clickEvent(ClickEvent.runCommand(childCmdBuilder.toString())));
				}
				return 1;
			});
		if (NetworkChatProperties.getReplaceHelpCommand()) {
			helpCommand.register();
		}
		ChatCommand.getBaseCommand()
			.withSubcommand(helpCommand)
			.register();

		for (Map.Entry<String, HelpTreeNode> entry : node.entrySet()) {
			String arg = entry.getKey();
			HelpTreeNode child = entry.getValue();
			List<String> nextArgStrings = new ArrayList<>(argStrings);
			nextArgStrings.add(arg);
			registerHelpCommandNode(zip, nextArgStrings, child);
		}
	}
}
