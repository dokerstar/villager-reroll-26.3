package com.joplayx.client.util;

import com.joplayx.client.Texts;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Hotbar / inventory management utilities.
 *
 * Slot layout (Inventory): 0-8 hotbar, 9-35 main inventory, offhand via
 * player.getOffhandItem(). In the player's InventoryMenu, main inventory slots
 * keep the same index (9-35), which is what SWAP clicks need.
 */
public class HotbarUtil {

	private static final Identifier LECTERN_ID = Identifier.withDefaultNamespace("lectern");

	/** Where a lectern was found, so error messages can say exactly what the mod saw. */
	public enum LecternLocation { HOTBAR, OFFHAND, INVENTORY, NONE }

	/**
	 * Matches by item instance first, then by registry id. The id fallback covers
	 * servers that rewrite item stacks (protocol translation plugins and the like),
	 * where the decoded stack can end up pointing at a different Item instance.
	 */
	public static boolean isLectern(ItemStack stack) {
		if (stack.isEmpty()) return false;
		if (stack.is(Items.LECTERN)) return true;
		return LECTERN_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
	}

	public static LecternLocation findLectern(Player player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
			if (isLectern(inv.getItem(i))) return LecternLocation.HOTBAR;
		}
		if (isLectern(player.getOffhandItem())) return LecternLocation.OFFHAND;
		for (int i = Inventory.SELECTION_SIZE; i < Inventory.INVENTORY_SIZE; i++) {
			if (isLectern(inv.getItem(i))) return LecternLocation.INVENTORY;
		}
		return LecternLocation.NONE;
	}

	public static int countLecterns(Player player) {
		Inventory inv = player.getInventory();
		int count = 0;
		for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
			ItemStack stack = inv.getItem(i);
			if (isLectern(stack)) count += stack.getCount();
		}
		ItemStack off = player.getOffhandItem();
		if (isLectern(off)) count += off.getCount();
		return count;
	}

	/**
	 * Gets a lectern ready to place and returns the hand to place it with, or null
	 * if the player has none. Hotbar is preferred (selects that slot), then the
	 * offhand, then a lectern deeper in the inventory is swapped into the hotbar.
	 */
	public static InteractionHand prepareLectern(Minecraft mc, Player player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
			if (isLectern(inv.getItem(i))) {
				inv.setSelectedSlot(i);
				return InteractionHand.MAIN_HAND;
			}
		}
		if (isLectern(player.getOffhandItem())) return InteractionHand.OFF_HAND;

		for (int i = Inventory.SELECTION_SIZE; i < Inventory.INVENTORY_SIZE; i++) {
			if (!isLectern(inv.getItem(i))) continue;
			if (mc.gameMode == null) return null;
			int hotbarSlot = pickHotbarSlotForSwap(inv);
			mc.gameMode.handleContainerInput(
					player.inventoryMenu.containerId, i, hotbarSlot, ContainerInput.SWAP, player);
			inv.setSelectedSlot(hotbarSlot);
			return InteractionHand.MAIN_HAND;
		}
		return null;
	}

	/** An empty hotbar slot if there is one, otherwise any slot that isn't holding an axe. */
	private static int pickHotbarSlotForSwap(Inventory inv) {
		for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
			if (inv.getItem(i).isEmpty()) return i;
		}
		for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
			if (!inv.getItem(i).is(ItemTags.AXES)) return i;
		}
		return inv.getSelectedSlot();
	}

	public static boolean hasItemInHotbar(Player player, Item item) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
			if (inv.getItem(i).is(item)) return true;
		}
		return false;
	}

	/** Selects the first axe in the hotbar. Returns false if there is none (lectern gets broken by hand). */
	public static boolean selectBestAxe(Player player) {
		Inventory inv = player.getInventory();
		for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
			if (inv.getItem(i).is(ItemTags.AXES)) {
				inv.setSelectedSlot(i);
				return true;
			}
		}
		return false;
	}

	public static boolean isSelectedItemLowDurability(Player player, int threshold) {
		ItemStack selected = player.getMainHandItem();
		if (selected.isEmpty() || !selected.isDamageableItem()) return false;
		return (selected.getMaxDamage() - selected.getDamageValue()) <= threshold;
	}

	public static Component locationName(LecternLocation location) {
		return Texts.tr("location.villager-reroll." + location.name().toLowerCase(java.util.Locale.ROOT));
	}

	/** One line per non-empty hotbar slot plus the offhand, for /reroll debug. */
	public static Component describeHotbar(Player player) {
		Inventory inv = player.getInventory();
		Component lines = Component.empty();
		boolean any = false;
		for (int i = 0; i < Inventory.SELECTION_SIZE; i++) {
			Component line = slotLine(
					i == inv.getSelectedSlot()
							? Texts.tr("command.villager-reroll.slot_selected", i)
							: Texts.tr("command.villager-reroll.slot", i),
					inv.getItem(i));
			if (line == null) continue;
			if (any) lines = lines.copy().append("\n");
			lines = lines.copy().append(line);
			any = true;
		}
		Component offhand = slotLine(Texts.tr("command.villager-reroll.offhand"), player.getOffhandItem());
		if (offhand != null) {
			if (any) lines = lines.copy().append("\n");
			lines = lines.copy().append(offhand);
			any = true;
		}
		return any ? lines : Texts.tr("command.villager-reroll.debug_empty");
	}

	private static Component slotLine(Component label, ItemStack stack) {
		if (stack.isEmpty()) return null;
		String key = isLectern(stack)
				? "command.villager-reroll.debug_slot_lectern"
				: "command.villager-reroll.debug_slot";
		return Texts.tr(key, label, BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount());
	}
}
