package com.playmonumenta.common;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import net.kyori.adventure.text.Component;
import org.apache.logging.log4j.Level;

/**
 * Velocity-side helper for registering the {@code /changeLogLevelVelocity} command.
 *
 * <p>This class is intentionally separate from {@link MMLog} so that loading {@link MMLog} on
 * Paper does not trigger resolution of Velocity API classes, which are absent on that platform.
 *
 * <p>Call from the Velocity plugin entry point after constructing {@link MMLog}:
 * <pre>{@code
 * MMLog log = new MMLog("MyPlugin");
 * MMLogVelocity.registerCommand(log, mServer.getCommandManager(), this, "myPlugin");
 * }</pre>
 */
public final class MMLogVelocity {
	private MMLogVelocity() {
	}

	/**
	 * Registers {@code /changeLogLevelVelocity <label> TRACE|DEBUG|INFO|WARN|ERROR} via Brigadier.
	 *
	 * <p>Uses a distinct command name ({@code changeLogLevelVelocity}) so it never conflicts with
	 * the Paper command. The required permission is {@code <label>.changeloglevel}.
	 *
	 * @param log            the {@link MMLog} instance whose level will be changed
	 * @param commandManager the Velocity proxy's command manager
	 * @param plugin         the Velocity plugin instance registering the command
	 * @param label          identifier shown as the first argument (e.g. {@code "networkRelay"})
	 */
	public static void registerCommand(MMLog log, CommandManager commandManager, Object plugin, String label) {
		String permission = label + ".changeloglevel";
		LiteralArgumentBuilder<CommandSource> root = BrigadierCommand.literalArgumentBuilder("changeLogLevelVelocity")
			.requires(source -> source.hasPermission(permission))
			.then(BrigadierCommand.literalArgumentBuilder(label)
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
					})));

		LiteralCommandNode<CommandSource> rootNode = root.build();
		BrigadierCommand cmd = new BrigadierCommand(rootNode);
		commandManager.register(commandManager.metaBuilder(cmd).plugin(plugin).build(), cmd);
	}
}
