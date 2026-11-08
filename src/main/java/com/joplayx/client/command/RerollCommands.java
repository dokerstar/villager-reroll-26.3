package com.joplayx.client.command;

import com.joplayx.client.Texts;
import com.joplayx.client.VillagerRerollClient;
import com.joplayx.client.config.EnchantTargetListScreen;
import com.joplayx.client.config.RerollerConfig;
import com.joplayx.client.config.RerollerConfig.Config;
import com.joplayx.client.config.RerollerConfig.EnchantTarget;
import com.joplayx.client.config.RerollerConfig.HudCorner;
import com.joplayx.client.config.RerollerConfigScreen;
import com.joplayx.client.state.RerollState;
import com.joplayx.client.util.HotbarUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * /reroll client commands. Registered through Fabric's client command API, so they
 * are handled entirely on this client and never sent to the server.
 */
public final class RerollCommands {

	private RerollCommands() {}

	public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext buildContext) {
		dispatcher.register(literal("reroll")
				.executes(RerollCommands::help)
				.then(literal("help").executes(RerollCommands::help))

				.then(literal("start").executes(ctx -> {
					VillagerRerollClient.CONTROLLER.start(ctx.getSource().getClient());
					return 1;
				}))
				.then(literal("stop").executes(ctx -> {
					VillagerRerollClient.CONTROLLER.stopByPlayer(ctx.getSource().getClient());
					return 1;
				}))

				.then(literal("pos")
						.executes(ctx -> say(ctx, VillagerRerollClient.setLecternFromCrosshair(ctx.getSource().getClient())))
						.then(literal("clear").executes(ctx -> {
							RerollerConfig.get().clearLecternPos();
							RerollerConfig.save();
							return say(ctx, Texts.prefixed("command.villager-reroll.pos_cleared"));
						}))
						.then(argument("x", IntegerArgumentType.integer())
								.then(argument("y", IntegerArgumentType.integer())
										.then(argument("z", IntegerArgumentType.integer())
												.executes(RerollCommands::setPos)))))

				.then(literal("villager")
						.executes(ctx -> say(ctx, VillagerRerollClient.selectVillagerFromCrosshair(ctx.getSource().getClient())))
						.then(literal("clear").executes(ctx -> {
							VillagerRerollClient.CONTROLLER.clearSelectedVillager();
							return say(ctx, Texts.prefixed("command.villager-reroll.villager_cleared"));
						})))

				.then(literal("add")
						.then(argument("enchant", IdentifierArgument.id())
								.suggests(ENCHANT_SUGGESTIONS)
								.executes(ctx -> addTarget(ctx, 1, 64))
								.then(argument("minLevel", IntegerArgumentType.integer(1, 5))
										.executes(ctx -> addTarget(ctx, IntegerArgumentType.getInteger(ctx, "minLevel"), 64))
										.then(argument("maxCost", IntegerArgumentType.integer(1, 64))
												.executes(ctx -> addTarget(ctx,
														IntegerArgumentType.getInteger(ctx, "minLevel"),
														IntegerArgumentType.getInteger(ctx, "maxCost")))))))
				.then(literal("remove")
						.then(argument("enchant", IdentifierArgument.id())
								.suggests(TARGET_SUGGESTIONS)
								.executes(RerollCommands::removeTarget)))
				.then(literal("list").executes(RerollCommands::listTargets))
				.then(literal("clear").executes(ctx -> {
					RerollerConfig.get().targets.clear();
					RerollerConfig.save();
					return say(ctx, Texts.prefixed("command.villager-reroll.targets_cleared"));
				}))

				.then(literal("hud")
						.then(literal("on").executes(ctx -> setHud(ctx, true)))
						.then(literal("off").executes(ctx -> setHud(ctx, false)))
						.then(literal("compact")
								.then(argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
									RerollerConfig.get().hudCompact = BoolArgumentType.getBool(ctx, "enabled");
									RerollerConfig.save();
									return say(ctx, Texts.prefixed("command.villager-reroll.hud_compact", onOff(RerollerConfig.get().hudCompact)));
								})))
						.then(literal("scale")
								.then(argument("scale", FloatArgumentType.floatArg(
										RerollerConfig.MIN_HUD_SCALE, RerollerConfig.MAX_HUD_SCALE)).executes(ctx -> {
									RerollerConfig.get().hudScale = FloatArgumentType.getFloat(ctx, "scale");
									RerollerConfig.save();
									return say(ctx, Texts.prefixed("command.villager-reroll.hud_scale", RerollerConfig.get().hudScale));
								})))
						.then(literal("pos")
								.then(argument("corner", com.mojang.brigadier.arguments.StringArgumentType.word())
										.suggests(CORNER_SUGGESTIONS)
										.executes(RerollCommands::setCorner))))

				.then(literal("delay")
						.then(literal("close")
								.then(argument("ticks", IntegerArgumentType.integer(5, 100)).executes(ctx -> {
									RerollerConfig.get().closeDelayTicks = IntegerArgumentType.getInteger(ctx, "ticks");
									RerollerConfig.save();
									return say(ctx, Texts.prefixed("command.villager-reroll.close_delay", RerollerConfig.get().closeDelayTicks));
								})))
						.then(literal("retry")
								.then(argument("ticks", IntegerArgumentType.integer(10, 200)).executes(ctx -> {
									RerollerConfig.get().retryDelayTicks = IntegerArgumentType.getInteger(ctx, "ticks");
									RerollerConfig.save();
									return say(ctx, Texts.prefixed("command.villager-reroll.retry_delay", RerollerConfig.get().retryDelayTicks));
								}))))

				.then(literal("radius")
						.then(argument("blocks", IntegerArgumentType.integer(1, 16)).executes(ctx -> {
							RerollerConfig.get().searchRadius = IntegerArgumentType.getInteger(ctx, "blocks");
							RerollerConfig.save();
							return say(ctx, Texts.prefixed("command.villager-reroll.radius", RerollerConfig.get().searchRadius));
						})))

				.then(literal("highlight")
						.then(argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
							RerollerConfig.get().highlightVillager = BoolArgumentType.getBool(ctx, "enabled");
							RerollerConfig.save();
							return say(ctx, Texts.prefixed("command.villager-reroll.highlight", onOff(RerollerConfig.get().highlightVillager)));
						})))

				.then(literal("pickup")
						.then(argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
							RerollerConfig.get().autoPickup = BoolArgumentType.getBool(ctx, "enabled");
							RerollerConfig.save();
							return say(ctx, Texts.prefixed("command.villager-reroll.pickup", onOff(RerollerConfig.get().autoPickup)));
						})))

				.then(literal("settings").executes(RerollCommands::showSettings))

				.then(literal("config")
						.executes(ctx -> {
							VillagerRerollClient.openScreenNextTick(() -> new EnchantTargetListScreen(null));
							return 1;
						})
						.then(literal("more").executes(ctx -> {
							VillagerRerollClient.openScreenNextTick(() -> RerollerConfigScreen.create(null));
							return 1;
						})))

				.then(literal("debug").executes(RerollCommands::debug))
		);
	}

	private static final SuggestionProvider<FabricClientCommandSource> ENCHANT_SUGGESTIONS = (ctx, builder) -> {
		Minecraft mc = ctx.getSource().getClient();
		if (mc.level != null) {
			return SharedSuggestionProvider.suggestResource(
					mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
							.listElements().map(ref -> ref.key().identifier()),
					builder);
		}
		return SharedSuggestionProvider.suggestResource(
				EnchantTargetListScreen.FALLBACK_ENCHANTMENT_IDS.stream().map(Identifier::parse), builder);
	};

	private static final SuggestionProvider<FabricClientCommandSource> TARGET_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggestResource(
					RerollerConfig.get().targets.stream()
							.map(t -> Identifier.tryParse(t.enchantmentId))
							.filter(id -> id != null),
					builder);

	private static final SuggestionProvider<FabricClientCommandSource> CORNER_SUGGESTIONS = (ctx, builder) ->
			SharedSuggestionProvider.suggest(
					Arrays.stream(HudCorner.values()).map(c -> c.name().toLowerCase(Locale.ROOT)), builder);

	private static int help(CommandContext<FabricClientCommandSource> ctx) {
		return say(ctx, Texts.prefixed("command.villager-reroll.help"));
	}

	private static int setPos(CommandContext<FabricClientCommandSource> ctx) {
		Config cfg = RerollerConfig.get();
		cfg.lecternX = IntegerArgumentType.getInteger(ctx, "x");
		cfg.lecternY = IntegerArgumentType.getInteger(ctx, "y");
		cfg.lecternZ = IntegerArgumentType.getInteger(ctx, "z");
		RerollerConfig.save();
		return say(ctx, Texts.prefixed("command.villager-reroll.pos_set", cfg.lecternPosString()));
	}

	private static int addTarget(CommandContext<FabricClientCommandSource> ctx, int minLevel, int maxCost) {
		Identifier id = ctx.getArgument("enchant", Identifier.class);
		Minecraft mc = ctx.getSource().getClient();
		if (mc.level != null && mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
				.listElements().noneMatch(ref -> ref.key().identifier().equals(id))) {
			ctx.getSource().sendError(Texts.prefixed("command.villager-reroll.unknown_enchant", id));
			return 0;
		}
		String idString = id.toString();
		List<EnchantTarget> targets = RerollerConfig.get().targets;
		EnchantTarget existing = targets.stream()
				.filter(t -> t.enchantmentId.equals(idString))
				.findFirst().orElse(null);
		if (existing != null) {
			existing.minLevel = minLevel;
			existing.maxEmeraldCost = maxCost;
		} else {
			targets.add(new EnchantTarget(idString, minLevel, maxCost));
		}
		RerollerConfig.save();
		return say(ctx, Texts.prefixed(
				existing != null ? "command.villager-reroll.updated" : "command.villager-reroll.added",
				idString, minLevel, maxCost));
	}

	private static int removeTarget(CommandContext<FabricClientCommandSource> ctx) {
		String id = ctx.getArgument("enchant", Identifier.class).toString();
		boolean removed = RerollerConfig.get().targets.removeIf(t -> t.enchantmentId.equals(id));
		if (!removed) {
			ctx.getSource().sendError(Texts.prefixed("command.villager-reroll.not_in_list", id));
			return 0;
		}
		RerollerConfig.save();
		return say(ctx, Texts.prefixed("command.villager-reroll.removed", id));
	}

	private static int listTargets(CommandContext<FabricClientCommandSource> ctx) {
		List<EnchantTarget> targets = RerollerConfig.get().targets;
		if (targets.isEmpty()) return say(ctx, Texts.prefixed("command.villager-reroll.list_empty"));
		MutableComponent message = Texts.tr("message.villager-reroll.prefix")
				.append(Texts.tr("command.villager-reroll.list_header"));
		for (EnchantTarget target : targets) {
			message.append("\n").append(Texts.tr(
					"command.villager-reroll.list_line",
					target.enchantmentId, target.minLevel, target.maxEmeraldCost));
		}
		return say(ctx, message);
	}

	private static int setHud(CommandContext<FabricClientCommandSource> ctx, boolean enabled) {
		RerollerConfig.get().hudEnabled = enabled;
		RerollerConfig.save();
		return say(ctx, Texts.prefixed("command.villager-reroll.hud", onOff(enabled)));
	}

	private static int setCorner(CommandContext<FabricClientCommandSource> ctx) {
		String raw = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "corner");
		HudCorner corner;
		try {
			corner = HudCorner.valueOf(raw.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendError(Texts.prefixed("command.villager-reroll.unknown_corner", raw));
			return 0;
		}
		RerollerConfig.get().hudCorner = corner;
		RerollerConfig.save();
		return say(ctx, Texts.prefixed("command.villager-reroll.hud_pos", cornerName(corner), raw.toLowerCase(Locale.ROOT)));
	}

	private static int showSettings(CommandContext<FabricClientCommandSource> ctx) {
		Config cfg = RerollerConfig.get();
		Component on = onOff(true);
		Component off = onOff(false);
		MutableComponent message = Texts.tr("message.villager-reroll.prefix")
				.append(Texts.tr("command.villager-reroll.settings_header"));
		message.append("\n").append(Texts.tr("command.villager-reroll.settings_lectern",
				cfg.lecternPosString(), Texts.tr("message.villager-reroll.pos_unset")));
		message.append("\n").append(Texts.tr("command.villager-reroll.settings_targets", cfg.targets.size()));
		message.append("\n").append(Texts.tr("command.villager-reroll.settings_close", cfg.closeDelayTicks));
		message.append("\n").append(Texts.tr("command.villager-reroll.settings_retry", cfg.retryDelayTicks));
		message.append("\n").append(Texts.tr("command.villager-reroll.settings_radius", cfg.searchRadius));
		message.append("\n").append(Texts.tr("command.villager-reroll.settings_outline", onOff(cfg.highlightVillager), on));
		message.append("\n").append(Texts.tr("command.villager-reroll.settings_pickup", onOff(cfg.autoPickup), on));
		message.append("\n").append(Texts.tr("command.villager-reroll.settings_hud", onOff(cfg.hudEnabled), on));
		message.append("\n").append(Texts.tr("command.villager-reroll.settings_scale", cfg.hudScale));
		message.append("\n").append(Texts.tr("command.villager-reroll.settings_hud_pos",
				cornerName(cfg.hudCorner), cornerName(HudCorner.MIDDLE_LEFT)));
		message.append("\n").append(Texts.tr("command.villager-reroll.settings_compact", onOff(cfg.hudCompact), off));
		return say(ctx, message);
	}

	private static int debug(CommandContext<FabricClientCommandSource> ctx) {
		Minecraft mc = ctx.getSource().getClient();
		var player = ctx.getSource().getPlayer();
		Villager selected = VillagerRerollClient.CONTROLLER.getSelectedVillager(mc.level);
		RerollState state = VillagerRerollClient.CONTROLLER.getState();
		Component mode = mc.gameMode != null
				? mc.gameMode.getPlayerMode().getLongDisplayName()
				: Texts.tr("message.villager-reroll.unknown");
		Component villager = selected != null
				? Texts.tr("command.villager-reroll.debug_villager",
				selected.getDisplayName(), selected.blockPosition().toShortString())
				: Texts.tr("command.villager-reroll.debug_villager_none");
		MutableComponent message = Texts.tr("message.villager-reroll.prefix")
				.append(Texts.tr("command.villager-reroll.debug_header"));
		message.append("\n").append(Texts.tr("command.villager-reroll.debug_mode", mode));
		message.append("\n").append(Texts.tr("command.villager-reroll.debug_state",
				Texts.tr("state.villager-reroll." + state.name().toLowerCase(Locale.ROOT)),
				VillagerRerollClient.CONTROLLER.getStatusMessage()));
		message.append("\n").append(Texts.tr("command.villager-reroll.debug_lectern",
				HotbarUtil.locationName(HotbarUtil.findLectern(player)),
				HotbarUtil.countLecterns(player)));
		message.append("\n").append(villager);
		message.append("\n").append(Texts.tr("command.villager-reroll.debug_hotbar"));
		message.append("\n").append(HotbarUtil.describeHotbar(player));
		return say(ctx, message);
	}

	private static int say(CommandContext<FabricClientCommandSource> ctx, Component message) {
		ctx.getSource().sendFeedback(message);
		return 1;
	}

	private static Component onOff(boolean value) {
		return Texts.tr(value ? "message.villager-reroll.on" : "message.villager-reroll.off");
	}

	private static Component cornerName(HudCorner corner) {
		return Texts.tr("config.villager-reroll.corner." + corner.name().toLowerCase(Locale.ROOT));
	}
}
