package justfatlard.loot_ender.lock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import justfatlard.loot_ender.LootEnderConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /lockpicking difficulty}: anyone can ask what theirs is; ops set the server's, and one
 * player's over it ({@code server} hands a player back to the server's).
 */
public final class LockCommands {
	private LockCommands() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		LiteralArgumentBuilder<CommandSourceStack> server = Commands.literal("server")
			.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()));
		RequiredArgumentBuilder<CommandSourceStack, EntitySelector> players =
			Commands.argument("players", EntityArgument.players());

		for (LockDifficulty level : LockDifficulty.values()) {
			server.then(Commands.literal(level.getSerializedName())
				.executes(context -> setServer(context.getSource(), level)));
			players.then(Commands.literal(level.getSerializedName())
				.executes(context -> setPlayers(context.getSource(), EntityArgument.getPlayers(context, "players"), level)));
		}
		players.then(Commands.literal("server")
			.executes(context -> setPlayers(context.getSource(), EntityArgument.getPlayers(context, "players"), null)));

		dispatcher.register(Commands.literal("lockpicking")
			.then(Commands.literal("difficulty")
				.executes(context -> report(context.getSource()))
				.then(server)
				.then(Commands.literal("player")
					.requires(source -> Commands.LEVEL_GAMEMASTERS.check(source.permissions()))
					.then(players))));
	}

	private static int report(CommandSourceStack source) {
		LockDifficulty everyone = LootEnderConfig.lockDifficulty();
		List<String> lines = new ArrayList<>();
		lines.add("Locks here are " + everyone.label);

		ServerPlayer self = source.getPlayer();
		if (self != null && LockDifficulty.chosen(self) != null) {
			lines.add("Yours are " + LockDifficulty.chosen(self).label + ", set for you");
		}

		if (Commands.LEVEL_GAMEMASTERS.check(source.permissions())) {
			for (ServerPlayer online : source.getServer().getPlayerList().getPlayers()) {
				LockDifficulty chosen = LockDifficulty.chosen(online);
				if (chosen != null && online != self) lines.add(online.getGameProfile().name() + ": " + chosen.label);
			}
		}

		source.sendSuccess(() -> Component.literal(String.join("\n", lines)), false);
		return 1;
	}

	private static int setServer(CommandSourceStack source, LockDifficulty level) {
		LootEnderConfig.setLockDifficulty(level);
		source.sendSuccess(() -> Component.literal("Locks are " + level.label + " for everyone without their own"), true);
		return 1;
	}

	/** Null hands them back to the server's. Takes effect on the next lock they open. */
	private static int setPlayers(CommandSourceStack source, Collection<ServerPlayer> targets, LockDifficulty level) {
		for (ServerPlayer target : targets) {
			LockDifficulty.choose(target, level);
			target.sendSystemMessage(Component.literal(level == null
				? "Your locks are back to the server's: " + LootEnderConfig.lockDifficulty().label
				: "Your locks are " + level.label + " now"));
		}
		String who = targets.size() == 1 ? targets.iterator().next().getGameProfile().name() : targets.size() + " players";
		source.sendSuccess(() -> Component.literal(level == null
			? who + " now picks at the server's difficulty, " + LootEnderConfig.lockDifficulty().label
			: who + " now picks at " + level.label), true);
		return targets.size();
	}
}
