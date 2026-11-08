package com.joplayx.client;

import com.joplayx.VillagerReroll;
import com.joplayx.client.command.RerollCommands;
import com.joplayx.client.config.EnchantTargetListScreen;
import com.joplayx.client.config.RerollerConfig;
import com.joplayx.client.hud.RerollerHud;
import com.joplayx.client.state.RerollController;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.function.Supplier;

public class VillagerRerollClient implements ClientModInitializer {

	public static KeyMapping startStopKey;
	public static KeyMapping emergencyStopKey;
	public static KeyMapping setPositionKey;
	public static KeyMapping selectVillagerKey;
	public static KeyMapping openConfigKey;

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
			Identifier.fromNamespaceAndPath(VillagerReroll.MOD_ID, "villager_reroller")
	);

	public static final RerollController CONTROLLER = new RerollController();

	// Screens requested from a chat command must open on the next tick, after chat closes itself
	private static Supplier<Screen> pendingScreen = null;

	@Override
	public void onInitializeClient() {
		RerollerConfig.load();

		// J — Start / Stop
		startStopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.villager-reroll.start_stop",
				InputConstants.Type.KEYBOARD,
				InputConstants.KEY_J,
				CATEGORY
		));

		// K — Emergency Stop
		emergencyStopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.villager-reroll.emergency_stop",
				InputConstants.Type.KEYBOARD,
				InputConstants.KEY_K,
				CATEGORY
		));

		// L — Set lectern position from the block under the crosshair
		setPositionKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.villager-reroll.set_position",
				InputConstants.Type.KEYBOARD,
				InputConstants.KEY_L,
				CATEGORY
		));

		// Unbound by default so it can't collide with other mods' keys
		selectVillagerKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.villager-reroll.select_villager",
				InputConstants.Type.KEYBOARD,
				InputConstants.UNKNOWN.getValue(),
				CATEGORY
		));

		openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.villager-reroll.open_config",
				InputConstants.Type.KEYBOARD,
				InputConstants.UNKNOWN.getValue(),
				CATEGORY
		));

		ClientCommandRegistrationCallback.EVENT.register(RerollCommands::register);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (pendingScreen != null) {
				Supplier<Screen> screen = pendingScreen;
				pendingScreen = null;
				client.gui.setScreen(screen.get());
			}

			if (client.player == null) return;

			while (startStopKey.consumeClick()) {
				CONTROLLER.toggleStartStop(client);
			}

			while (emergencyStopKey.consumeClick()) {
				CONTROLLER.emergencyStop(client);
			}

			while (setPositionKey.consumeClick()) {
				client.player.sendSystemMessage(setLecternFromCrosshair(client));
			}

			while (selectVillagerKey.consumeClick()) {
				client.player.sendSystemMessage(selectVillagerFromCrosshair(client));
			}

			while (openConfigKey.consumeClick()) {
				client.gui.setScreen(new EnchantTargetListScreen(null));
			}

			CONTROLLER.tick(client);
		});

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(VillagerReroll.MOD_ID, "reroller_hud"),
				RerollerHud::extractRenderState
		);

		VillagerReroll.LOGGER.info("[VillagerReroll] Client initialized.");
	}

	public static void openScreenNextTick(Supplier<Screen> screen) {
		pendingScreen = screen;
	}

	/**
	 * Sets the lectern spot from the crosshair. Looking at an existing lectern uses
	 * that block; looking at anything else uses the space in front of the clicked
	 * face (normally the air block on top of the floor), since that's where a
	 * lectern can actually be placed.
	 */
	public static Component setLecternFromCrosshair(Minecraft client) {
		if (client.level == null || !(client.hitResult instanceof BlockHitResult blockHit)
				|| blockHit.getType() != HitResult.Type.BLOCK) {
			return Texts.prefixed("message.villager-reroll.look_floor");
		}
		BlockPos hitPos = blockHit.getBlockPos();
		BlockPos pos = client.level.getBlockState(hitPos).is(Blocks.LECTERN)
				? hitPos
				: hitPos.relative(blockHit.getDirection());
		RerollerConfig.get().setLecternPos(pos);
		RerollerConfig.save();
		return Texts.prefixed("message.villager-reroll.pos_set", pos.getX(), pos.getY(), pos.getZ());
	}

	public static Component selectVillagerFromCrosshair(Minecraft client) {
		if (client.hitResult instanceof EntityHitResult entityHit
				&& entityHit.getEntity() instanceof Villager villager) {
			CONTROLLER.selectVillager(villager);
			return Texts.prefixed("message.villager-reroll.villager_selected", villager.getDisplayName());
		}
		return Texts.prefixed("message.villager-reroll.look_villager");
	}
}
