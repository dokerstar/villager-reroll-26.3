package com.joplayx.client.hud;

import com.joplayx.client.Texts;
import com.joplayx.client.VillagerRerollClient;
import com.joplayx.client.config.RerollerConfig;
import com.joplayx.client.state.RerollController;
import com.joplayx.client.state.RerollState;
import com.joplayx.client.util.TradeUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD overlay for the Villager Trade Reroller.
 *
 * Implements HudElement by providing extractRenderState(GuiGraphicsExtractor, DeltaTracker).
 * Method name and signature confirmed from HudElement.java and HudTests.java in references.
 *
 * Shows one row per configured enchantment target, live-updated against the
 * last read villager so it's clear at a glance which of your targets (e.g.
 * mending + protection IV + unbreaking III) are currently satisfied together,
 * plus a "Last Offer" row showing the last enchanted book actually seen so
 * you can confirm the mod is reading trades even before anything matches.
 */
public class RerollerHud {

	private static final int PADDING = 6;
	private static final int LINE_HEIGHT = 10;
	private static final int X = 4; // left edge, same margin as before
	private static final int BORDER = 1;
	private static final int LABEL_VALUE_GAP = 8;
	private static final int SECTION_GAP = 3;

	private static final int COLOR_BG     = 0xCC0A0A0A; // dark translucent panel
	private static final int COLOR_BORDER = 0x80FFAA00; // subtle gold accent border
	private static final int COLOR_DIVIDER = 0x40FFFFFF; // faint separator line
	private static final int COLOR_GOLD   = 0xFFFFAA00;
	private static final int COLOR_WHITE  = CommonColors.WHITE;
	private static final int COLOR_GRAY   = 0xFFAAAAAA;
	private static final int COLOR_DIM    = 0xFF777777;
	private static final int COLOR_GREEN  = 0xFF55FF55;
	private static final int COLOR_RED    = 0xFFFF5555;
	private static final int COLOR_YELLOW = 0xFFFFFF55;

	public static void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		RerollerConfig.Config cfg = RerollerConfig.get();
		if (!cfg.hudEnabled) return;

		RerollController ctrl = VillagerRerollClient.CONTROLLER;
		RerollState state = ctrl.getState();
		if (state == RerollState.IDLE) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.hud.isHidden()) return;

		int statusColor = switch (state) {
			case FOUND -> COLOR_GREEN;
			case ERROR -> COLOR_RED;
			case WAIT_FOR_PROFESSION, WAIT_FOR_SCREEN,
				 WAIT_AFTER_CLOSE, WAIT_BEFORE_RETRY,
				 WAIT_BREAK_COMPLETE, WALK_TO_ITEM, WALK_BACK -> COLOR_YELLOW;
			default -> COLOR_WHITE;
		};

		if (cfg.hudCompact) {
			extractCompact(graphics, mc, cfg, ctrl, statusColor);
			return;
		}

		String startStopName = VillagerRerollClient.startStopKey.getTranslatedKeyMessage().getString();
		String emergencyStopName = VillagerRerollClient.emergencyStopKey.getTranslatedKeyMessage().getString();
		String setPositionName = VillagerRerollClient.setPositionKey.getTranslatedKeyMessage().getString();

		Component lecternPos = cfg.hasLecternPos()
				? Component.literal(cfg.lecternPosString())
				: Texts.tr("hud.villager-reroll.lectern_unset", setPositionName);

		Component header = Texts.tr("hud.villager-reroll.title");
		Component footer = Texts.tr("hud.villager-reroll.footer", startStopName, emergencyStopName, setPositionName);

		// Fixed rows first
		List<Component> labels = new ArrayList<>(List.of(
				Texts.tr("hud.villager-reroll.status"),
				Texts.tr("hud.villager-reroll.lectern"),
				Texts.tr("hud.villager-reroll.attempts")));
		List<Component> values = new ArrayList<>(List.of(
				Component.literal(ctrl.getStatusMessage()),
				lecternPos,
				Component.literal(String.valueOf(ctrl.getAttempts()))
		));
		List<Integer> valueColors = new ArrayList<>(List.of(
				statusColor,
				cfg.hasLecternPos() ? COLOR_GRAY : COLOR_RED,
				COLOR_WHITE
		));

		// One row per configured enchant target, matched against the last read
		// villager's trades so multiple targets show their live found/not-found state.
		List<TradeUtil.TargetStatus> lastStatuses = ctrl.getLastStatuses();
		if (cfg.targets.isEmpty()) {
			labels.add(Texts.tr("hud.villager-reroll.targets"));
			values.add(Texts.tr("hud.villager-reroll.targets_none"));
			valueColors.add(COLOR_RED);
		} else {
			for (RerollerConfig.EnchantTarget target : cfg.targets) {
				String shortId = target.enchantmentId.contains(":")
						? target.enchantmentId.substring(target.enchantmentId.indexOf(':') + 1)
						: target.enchantmentId;
				Component label = shortId.isEmpty()
						? Texts.tr("hud.villager-reroll.unset_enchant")
						: Component.literal(shortId);

				TradeUtil.TargetStatus match = lastStatuses.stream()
						.filter(s -> s.target() == target)
						.findFirst()
						.orElse(null);

				Component value;
				int color;
				if (match == null) {
					// Not checked yet this cycle - show the criteria plainly, no "checking..."
					// spam repeated on every row (the Status row already says what's happening).
					value = Texts.tr("hud.villager-reroll.criteria", target.minLevel, target.maxEmeraldCost);
					color = COLOR_GRAY;
				} else if (match.satisfied()) {
					value = Component.literal(match.description());
					color = COLOR_GREEN;
				} else {
					value = Component.literal(match.description());
					color = COLOR_RED;
				}

				labels.add(label);
				values.add(value);
				valueColors.add(color);
			}
		}

		labels.add(Texts.tr("hud.villager-reroll.last_offer"));
		values.add(ctrl.getLastOfferSeen().isEmpty()
				? Texts.tr("hud.villager-reroll.none_offer")
				: Component.literal(ctrl.getLastOfferSeen()));
		valueColors.add(COLOR_DIM);

		// --- Measure ---
		int labelWidth = 0;
		for (Component label : labels) labelWidth = Math.max(labelWidth, mc.font.width(label));
		int labelColW = labelWidth + LABEL_VALUE_GAP;

		int contentWidth = Math.max(mc.font.width(header), mc.font.width(footer));
		for (int i = 0; i < labels.size(); i++) {
			contentWidth = Math.max(contentWidth, labelColW + mc.font.width(values.get(i)));
		}

		int rowsHeight = labels.size() * LINE_HEIGHT;
		int contentHeight = LINE_HEIGHT
				+ SECTION_GAP + 1 + SECTION_GAP
				+ rowsHeight
				+ SECTION_GAP + 1 + SECTION_GAP
				+ LINE_HEIGHT;

		float scale = cfg.hudScale;
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		int boxX = anchorX(cfg.hudCorner, graphics.guiWidth() / scale, contentWidth);
		int boxY = anchorY(cfg.hudCorner, graphics.guiHeight() / scale, contentHeight);

		int bgX1 = boxX - PADDING;
		int bgY1 = boxY - PADDING;
		int bgX2 = boxX + contentWidth + PADDING;
		int bgY2 = boxY + contentHeight + PADDING;

		graphics.fill(bgX1 - BORDER, bgY1 - BORDER, bgX2 + BORDER, bgY2 + BORDER, COLOR_BORDER);
		graphics.fill(bgX1, bgY1, bgX2, bgY2, COLOR_BG);

		int cursorY = boxY;

		int headerX = boxX + (contentWidth - mc.font.width(header)) / 2;
		graphics.text(mc.font, header, headerX, cursorY, COLOR_GOLD);
		cursorY += LINE_HEIGHT + SECTION_GAP;

		graphics.fill(boxX, cursorY, boxX + contentWidth, cursorY + 1, COLOR_DIVIDER);
		cursorY += 1 + SECTION_GAP;

		for (int i = 0; i < labels.size(); i++) {
			graphics.text(mc.font, labels.get(i), boxX, cursorY, COLOR_GRAY);
			graphics.text(mc.font, values.get(i), boxX + labelColW, cursorY, valueColors.get(i));
			cursorY += LINE_HEIGHT;
		}

		cursorY += SECTION_GAP;
		graphics.fill(boxX, cursorY, boxX + contentWidth, cursorY + 1, COLOR_DIVIDER);
		cursorY += 1 + SECTION_GAP;

		int footerX = boxX + (contentWidth - mc.font.width(footer)) / 2;
		graphics.text(mc.font, footer, footerX, cursorY, COLOR_DIM);
		graphics.pose().popMatrix();
	}

	/** One-line panel: just the status and attempt count, for when the full panel is in the way. */
	private static void extractCompact(GuiGraphicsExtractor graphics, Minecraft mc,
									   RerollerConfig.Config cfg, RerollController ctrl, int statusColor) {
		Component label = Texts.tr("hud.villager-reroll.compact", ctrl.getAttempts());
		Component status = Component.literal(ctrl.getStatusMessage());
		int contentWidth = mc.font.width(label) + mc.font.width(status);
		int contentHeight = LINE_HEIGHT - 1;

		float scale = cfg.hudScale;
		graphics.pose().pushMatrix();
		graphics.pose().scale(scale, scale);
		int boxX = anchorX(cfg.hudCorner, graphics.guiWidth() / scale, contentWidth);
		int boxY = anchorY(cfg.hudCorner, graphics.guiHeight() / scale, contentHeight);

		int pad = PADDING / 2;
		graphics.fill(boxX - pad, boxY - pad, boxX + contentWidth + pad, boxY + contentHeight + pad, COLOR_BG);
		graphics.text(mc.font, label, boxX, boxY, COLOR_GOLD);
		graphics.text(mc.font, status, boxX + mc.font.width(label), boxY, statusColor);
		graphics.pose().popMatrix();
	}

	// Margin keeps the panel's padding and border on screen at every corner
	private static final int MARGIN = X + PADDING + BORDER;

	private static int anchorX(RerollerConfig.HudCorner corner, float screenWidth, int contentWidth) {
		return corner.isRight() ? (int) screenWidth - contentWidth - MARGIN : MARGIN;
	}

	private static int anchorY(RerollerConfig.HudCorner corner, float screenHeight, int contentHeight) {
		return switch (corner) {
			case TOP_LEFT, TOP_RIGHT -> MARGIN;
			case BOTTOM_LEFT, BOTTOM_RIGHT -> (int) screenHeight - contentHeight - MARGIN;
			default -> ((int) screenHeight - contentHeight) / 2;
		};
	}
}
