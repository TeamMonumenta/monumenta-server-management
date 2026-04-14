package com.playmonumenta.common;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.Level;

/**
 * Velocity-side helper for registering the {@code /changeloglevelvelocity} command.
 *
 * <p>This class is intentionally separate from {@link MMLog} so that loading {@link MMLog} on
 * Paper does not trigger resolution of Velocity API classes, which are absent on that platform.
 *
 * <p>Call from the Velocity plugin entry point after constructing {@link MMLog}:
 * <pre>{@code
 * MMLog log = new MMLog("MyPlugin");
 * MMLogVelocity.registerCommand(log, mServer.getCommandManager(), this);
 * }</pre>
 */
public final class MMLogVelocity {
	// All MMLog instances registered so far across all plugins on this proxy.
	// Each registerCommand call adds one and rebuilds the shared command tree.
	private static final List<MMLog> REGISTERED_LOGS = new ArrayList<>();

	private MMLogVelocity() {
	}

	/**
	 * Registers (or extends) {@code /changeloglevelvelocity <name> TRACE|DEBUG|INFO|WARN|ERROR}
	 * via Brigadier, where {@code <name>} is {@link MMLog#getName()}.
	 *
	 * <p>Velocity does not allow two plugins to register the same top-level command name — the
	 * second registration silently replaces the first. To support multiple plugins sharing the root,
	 * this method accumulates all {@link MMLog} instances in a static list and rebuilds the entire
	 * command tree on every call, unregistering the previous version first. Each label subcommand
	 * carries its own permission check ({@code <name>.changeloglevel}), so label nodes from
	 * different plugins are independently gated.
	 *
	 * <p>Uses a distinct command name ({@code changeloglevelvelocity}) so it never conflicts with
	 * the Paper command. Using the logger name directly ensures the command argument and any
	 * {@code log4j2.xml} {@code <Logger name="...">} entries refer to the same logger.
	 *
	 * @param log            the {@link MMLog} instance whose level will be changed
	 * @param commandManager the Velocity proxy's command manager
	 * @param plugin         the Velocity plugin instance registering the command
	 */
	public static void registerCommand(MMLog log, CommandManager commandManager, Object plugin) {
		REGISTERED_LOGS.add(log);

		commandManager.unregister("changeloglevelvelocity");

		LiteralArgumentBuilder<CommandSource> root =
			BrigadierCommand.literalArgumentBuilder("changeloglevelvelocity");
		for (MMLog registeredLog : REGISTERED_LOGS) {
			root.then(buildLabelNode(registeredLog));
		}

		LiteralCommandNode<CommandSource> rootNode = root.build();
		BrigadierCommand cmd = new BrigadierCommand(rootNode);
		commandManager.register(commandManager.metaBuilder(cmd).plugin(plugin).build(), cmd);
	}

	private static LiteralArgumentBuilder<CommandSource> buildLabelNode(MMLog log) {
		String label = log.getName();
		String permission = label.toLowerCase(java.util.Locale.ROOT) + ".changeloglevel";
		return BrigadierCommand.literalArgumentBuilder(label)
			.requires(source -> source.hasPermission(permission))
			.then(BrigadierCommand.literalArgumentBuilder("TRACE")
				.executes(ctx -> {
					log.setLevel(Level.TRACE);
					ctx.getSource().sendMessage(Component.text("Log level for " + label + " set to TRACE"));
					return 1;
				}))
			.then(BrigadierCommand.literalArgumentBuilder("DEBUG")
				.executes(ctx -> {
					log.setLevel(Level.DEBUG);
					ctx.getSource().sendMessage(Component.text("Log level for " + label + " set to DEBUG"));
					return 1;
				}))
			.then(BrigadierCommand.literalArgumentBuilder("INFO")
				.executes(ctx -> {
					log.setLevel(Level.INFO);
					ctx.getSource().sendMessage(Component.text("Log level for " + label + " set to INFO"));
					return 1;
				}))
			.then(BrigadierCommand.literalArgumentBuilder("WARN")
				.executes(ctx -> {
					log.setLevel(Level.WARN);
					ctx.getSource().sendMessage(Component.text("Log level for " + label + " set to WARN"));
					return 1;
				}))
			.then(BrigadierCommand.literalArgumentBuilder("ERROR")
				.executes(ctx -> {
					log.setLevel(Level.ERROR);
					ctx.getSource().sendMessage(Component.text("Log level for " + label + " set to ERROR"));
					return 1;
				}));
	}
}
