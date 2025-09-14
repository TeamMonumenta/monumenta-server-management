package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.RBoardAPI;
import com.playmonumenta.redissync.utils.ScoreboardUtils;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.FunctionArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.ObjectiveArgument;
import dev.jorel.commandapi.arguments.TextArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.executors.CommandExecutor;
import dev.jorel.commandapi.wrappers.FunctionWrapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Objective;

public class RboardCommand {
	static final String COMMAND = "rboard";
	static final CommandPermission PERMS = CommandPermission.fromString("monumenta.command.rboard");

	@FunctionalInterface
	public interface RboardAction {
		void run(CommandSender sender, CommandArguments args, String rboardName, String scoreboardName) throws Exception;
	}

	private static final EntitySelectorArgument.ManyPlayers playersArg = new EntitySelectorArgument.ManyPlayers("players");
	private static final TextArgument fakePlayerArg = new TextArgument("name");

	private static ObjectiveArgument getObjectiveArgument(int n) {
		return new ObjectiveArgument("objective" + n);
	}

	private static IntegerArgument getValueArgument(int n) {
		return new IntegerArgument("value" + n);
	}

	@SuppressWarnings("unchecked")
	private static void regWrapper(List<Argument<?>> arguments, RboardAction exec) {
		CommandExecutor cmdExec = (sender, args) -> {
			try {
				Collection<Player> players = args.getByArgument(playersArg);
				if (players != null) {
					for (Player player: players) {
						exec.run(sender, args, player.getUniqueId().toString(), player.getName());
					}
				} else {
					String name = args.getByArgument(fakePlayerArg);
					if (name == null || !name.startsWith("$")) {
						throw CommandAPI.failWithString("Fakeplayer names must start with a $");
					} else {
						exec.run(sender, args, name, name);
					}
				}
			} catch (WrapperCommandSyntaxException ex) {
				throw ex;
			} catch (Exception ex) {
				throw CommandAPI.failWithString(ex.getMessage());
			}
		};

		/* Replace the players argument with a simple string for fakeplayers */
		List<Argument<?>> fakePlayerArguments = new ArrayList<>(40);
		for (Argument<?> arg : arguments) {
			if (arg.equals(playersArg)) {
				fakePlayerArguments.add(fakePlayerArg);
			} else {
				fakePlayerArguments.add(arg);
			}
		}

		/* First register a variant with fakeplayers as a string argument, replacing the "players" arg
		 * This ordering is apparently important
		 */
		new CommandAPICommand(COMMAND)
			.withArguments(fakePlayerArguments)
			.withPermission(PERMS)
			.executes(cmdExec)
			.register();

		/* Second one of these registers as-is, with 'players' being a collection of players */
		new CommandAPICommand(COMMAND)
			.withArguments(arguments)
			.withPermission(PERMS)
			.executes(cmdExec)
			.register();
	}

	@SuppressWarnings("DataFlowIssue")
	public static void register(Plugin plugin) {
		List<Argument<?>> arguments = new ArrayList<>(40);

		ObjectiveArgument objectiveArg = new ObjectiveArgument("objective");
		IntegerArgument valueArg = new IntegerArgument("value");
		ObjectiveArgument objectiveToAddArg = new ObjectiveArgument("objectiveToAdd");
		FunctionArgument functionArg = new FunctionArgument("function");

		/* ******************* Set ******************* */
		RboardAction action = (sender, args, rboardName, scoreboardName) -> {
			Map<String, String> values = new LinkedHashMap<>();
			for (int i = 0; i < args.count() - 1; i += 2) {
				int n = i / 2;
				values.put(args.getByArgument(getObjectiveArgument(n)).toString(), Integer.toString(args.getByArgument(getValueArgument(n))));
			}
			RBoardAPI.set(rboardName, values);
		};

		arguments.add(new LiteralArgument("set"));
		arguments.add(playersArg);
		for (int i = 0; i < 6; i++) {
			arguments.add(getObjectiveArgument(i));
			arguments.add(getValueArgument(i));
			regWrapper(arguments, action);
		}

		/* ******************* Store ******************* */
		action = (sender, args, rboardName, scoreboardName) -> {
			Map<String, String> values = new LinkedHashMap<>();
			for (int i = 0; i < args.count() - 1; i += 1) {
				Objective objective = args.getByArgument(getObjectiveArgument(i));
				values.put(objective.getName(), Integer.toString(ScoreboardUtils.getScoreboardValue(scoreboardName, objective)));
			}
			RBoardAPI.set(rboardName, values);
		};

		arguments.clear();
		arguments.add(new LiteralArgument("store"));
		arguments.add(playersArg);
		for (int i = 0; i < 6; i++) {
			arguments.add(getObjectiveArgument(i));
			regWrapper(arguments, action);
		}

		/* ******************* Add ******************* */
		arguments.clear();
		arguments.add(new LiteralArgument("add"));
		arguments.add(playersArg);
		arguments.add(objectiveArg);
		arguments.add(valueArg);
		regWrapper(arguments, (sender, args, rboardName, scoreboardName) ->
			RBoardAPI.add(rboardName, args.getByArgument(objectiveArg).getName(), args.getByArgument(valueArg)));

		/* ******************* AddScore ******************* */
		arguments.clear();
		arguments.add(new LiteralArgument("addscore"));
		arguments.add(playersArg);
		arguments.add(objectiveArg);
		arguments.add(new ObjectiveArgument("objectiveToAdd"));
		regWrapper(arguments, (sender, args, rboardName, scoreboardName) ->
			RBoardAPI.add(rboardName, args.getByArgument(objectiveArg).getName(), ScoreboardUtils.getScoreboardValue(scoreboardName, args.getByArgument(objectiveToAddArg))));

		/* ******************* Reset ******************* */
		action = (sender, args, rboardName, scoreboardName) -> {
			String[] values = new String[args.count()];
			for (int i = 0; i < args.count() - 1; i += 1) {
				values[i] = args.getByArgument(getObjectiveArgument(i)).getName();
			}
			RBoardAPI.reset(rboardName, values);
		};

		arguments.clear();
		arguments.add(new LiteralArgument("reset"));
		arguments.add(playersArg);
		for (int i = 0; i < 6; i++) {
			arguments.add(getObjectiveArgument(i));
			regWrapper(arguments, action);
		}

		/* ******************* ResetAll ******************* */
		action = (sender, args, rboardName, scoreboardName) ->
			RBoardAPI.resetAll(rboardName);

		arguments.clear();
		arguments.add(new LiteralArgument("resetall"));
		arguments.add(playersArg);
		regWrapper(arguments, action);

		/* ******************* GetAll ******************* */
		action = (sender, args, rboardName, scoreboardName)
			-> MonumentaRedisSyncAPI.runOnMainThreadWhenComplete(plugin,
			RBoardAPI.getAll(rboardName),
			(Map<String, String> data, Throwable except) -> {
				if (except != null) {
					plugin.getLogger().log(Level.SEVERE, "rboard getall failed:" + except.getMessage(), except);
				} else {
					List<Component> entryComponents = new ArrayList<>();
					for (Map.Entry<String, String> entry : data.entrySet()) {
						entryComponents.add(
							Component.text(entry.getKey(), NamedTextColor.GOLD)
								.append(Component.text("=", NamedTextColor.WHITE))
								.append(Component.text(entry.getValue(), NamedTextColor.GREEN))
						);
					}
					sender.sendMessage(
						Component.text("[", NamedTextColor.WHITE)
							.append(Component.join(JoinConfiguration.spaces(), entryComponents))
							.append(Component.text("]"))
					);
				}
			});

		arguments.clear();
		arguments.add(new LiteralArgument("getall"));
		arguments.add(playersArg);
		regWrapper(arguments, action);

		/* ******************* Get ******************* */
		action = (sender, args, rboardName, scoreboardName) -> {
			String[] objects = new String[args.count() - 2];
			for (int j = 0; j < args.count() - 2; j += 1) {
				objects[j] = args.getByArgument(getObjectiveArgument(j)).getName();
			}
			MonumentaRedisSyncAPI.runOnMainThreadWhenComplete(plugin,
				RBoardAPI.get(rboardName, objects),
				(Map<String, String> data, Throwable except) -> {
					if (except != null) {
						plugin.getLogger().severe("rboard get failed:" + except.getMessage());
						except.printStackTrace();
					} else {
						for (Map.Entry<String, String> entry : data.entrySet()) {
							ScoreboardUtils.setScoreboardValue(scoreboardName, entry.getKey(), Integer.parseInt(entry.getValue()));
						}
						for (FunctionWrapper func : args.getByArgument(functionArg)) {
							func.run();
						}
					}
				});
		};

		arguments.clear();
		arguments.add(new LiteralArgument("get"));
		arguments.add(playersArg);
		arguments.add(functionArg);
		for (int i = 0; i < 15; i++) {
			arguments.add(getObjectiveArgument(i));
			regWrapper(arguments, action);
		}

		/* ******************* AddAndGet ******************* */
		action = (sender, args, rboardName, scoreboardName) -> {
			Objective objective = args.getByArgument(objectiveArg);
			MonumentaRedisSyncAPI.runOnMainThreadWhenComplete(plugin,
				RBoardAPI.add(rboardName, objective.getName(), args.getByArgument(valueArg)),
				(Long data, Throwable except) -> {
					if (except != null) {
						plugin.getLogger().severe("rboard addandget failed:" + except.getMessage());
						except.printStackTrace();
					} else {
						ScoreboardUtils.setScoreboardValue(scoreboardName, objective, data.intValue());
						for (FunctionWrapper func : args.getByArgument(functionArg)) {
							func.run();
						}
					}
				});
		};

		arguments.clear();
		arguments.add(new LiteralArgument("addandget"));
		arguments.add(playersArg);
		arguments.add(functionArg);
		arguments.add(objectiveArg);
		arguments.add(valueArg);
		regWrapper(arguments, action);

		/* ******************* GetAndReset ******************* */
		action = (sender, args, rboardName, scoreboardName) -> {
			String[] objects = new String[args.count() - 2];
			for (int j = 0; j < args.count() - 2; j += 1) {
				objects[j] = args.getByArgument(getObjectiveArgument(j)).getName();
			}
			MonumentaRedisSyncAPI.runOnMainThreadWhenComplete(plugin,
				RBoardAPI.getAndReset(rboardName, objects),
				(Map<String, String> data, Throwable except) -> {
					if (except != null) {
						plugin.getLogger().severe("rboard getandreset failed:" + except.getMessage());
						except.printStackTrace();
					} else {
						for (Map.Entry<String, String> entry : data.entrySet()) {
							ScoreboardUtils.setScoreboardValue(scoreboardName, entry.getKey(), Integer.parseInt(entry.getValue()));
						}
						for (FunctionWrapper func : args.getByArgument(functionArg)) {
							func.run();
						}
					}
				});
		};

		arguments.clear();
		arguments.add(new LiteralArgument("getandreset"));
		arguments.add(playersArg);
		arguments.add(functionArg);
		for (int i = 0; i < 6; i++) {
			arguments.add(getObjectiveArgument(i));
			regWrapper(arguments, action);
		}
	}
}
