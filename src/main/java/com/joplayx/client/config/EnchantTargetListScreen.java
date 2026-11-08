package com.joplayx.client.config;

import com.joplayx.client.config.RerollerConfig.Config;
import com.joplayx.client.config.RerollerConfig.EnchantTarget;
import com.joplayx.client.Texts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom screen for adding/removing/editing enchantment targets, and the new
 * top-level Mod Menu config entry point (see ModMenuIntegration) - this is
 * what you edit constantly, so it shouldn't be hidden behind an extra button.
 *
 * Uses extractRenderState(GuiGraphicsExtractor, int, int, float) instead of the
 * older render(GuiGraphics, ...) - confirmed from Fabric's own "Custom Screens"
 * documentation, since MC 26.x screens use the same extract/render split as the
 * HUD system.
 */
public class EnchantTargetListScreen extends Screen {

    private static final int ROW_HEIGHT = 26;
    private static final int TOP = 56;
    private static final int LEFT = 20;
    private static final int ID_WIDTH = 220;
    private static final int LEVEL_WIDTH = 40;
    private static final int COST_WIDTH = 50;
    private static final int GAP = 10;
    private static final int MAX_SUGGESTIONS = 5;
    private static final int SUGGESTION_WIDTH = 100;
    private static final int SUGGESTION_HEIGHT = 16;

    /** Fallback list used when no world is loaded yet (e.g. opened from the main menu),
     *  so autocomplete still works even without live registry access. */
    public static final List<String> FALLBACK_ENCHANTMENT_IDS = List.of(
            "minecraft:aqua_affinity", "minecraft:bane_of_arthropods", "minecraft:blast_protection",
            "minecraft:breach", "minecraft:channeling", "minecraft:curse_of_binding", "minecraft:curse_of_vanishing",
            "minecraft:density", "minecraft:depth_strider", "minecraft:efficiency", "minecraft:feather_falling",
            "minecraft:fire_aspect", "minecraft:fire_protection", "minecraft:flame", "minecraft:fortune",
            "minecraft:frost_walker", "minecraft:impaling", "minecraft:infinity", "minecraft:knockback",
            "minecraft:looting", "minecraft:loyalty", "minecraft:luck_of_the_sea", "minecraft:lure",
            "minecraft:mending", "minecraft:multishot", "minecraft:piercing", "minecraft:power",
            "minecraft:projectile_protection", "minecraft:protection", "minecraft:punch", "minecraft:quick_charge",
            "minecraft:respiration", "minecraft:riptide", "minecraft:sharpness", "minecraft:silk_touch",
            "minecraft:smite", "minecraft:soul_speed", "minecraft:sweeping_edge", "minecraft:swift_sneak",
            "minecraft:thorns", "minecraft:unbreaking", "minecraft:wind_burst"
    );

    private final Screen parent;
    private final List<RowWidgets> rows = new ArrayList<>();
    private List<String> knownEnchantmentIds = FALLBACK_ENCHANTMENT_IDS;

    private static class RowWidgets {
        final EditBox idBox;
        final EditBox levelBox;
        final EditBox costBox;
        final EnchantTarget target;
        final List<Button> suggestionButtons = new ArrayList<>();

        RowWidgets(EditBox idBox, EditBox levelBox, EditBox costBox, EnchantTarget target) {
            this.idBox = idBox;
            this.levelBox = levelBox;
            this.costBox = costBox;
            this.target = target;
        }
    }

    public EnchantTargetListScreen(Screen parent) {
        super(Texts.tr("screen.villager-reroll.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();

        // Every enchantment that can legally go on an enchanted book, straight from the
        // live registry when a world is loaded (covers modded enchantments too), or the
        // vanilla fallback list above if opened from the main menu with no world yet.
        if (this.minecraft.level != null) {
            knownEnchantmentIds = this.minecraft.level.registryAccess()
                    .lookupOrThrow(Registries.ENCHANTMENT)
                    .listElements()
                    .map(ref -> ref.key().identifier().toString())
                    .sorted()
                    .toList();
        }

        Config cfg = RerollerConfig.get();
        int y = TOP;
        for (EnchantTarget target : cfg.targets) {
            addRow(target, y);
            y += ROW_HEIGHT;
        }

        this.addRenderableWidget(Button.builder(Texts.tr("screen.villager-reroll.add"), b -> {
            saveAll();
            RerollerConfig.get().targets.add(new EnchantTarget("", 1, 64));
            RerollerConfig.save();
            rebuild();
        }).bounds(LEFT, this.height - 58, 200, 20).build());

        this.addRenderableWidget(Button.builder(Texts.tr("screen.villager-reroll.more"), b -> {
            saveAll();
            RerollerConfig.save();
            this.minecraft.gui.setScreen(RerollerConfigScreen.create(this));
        }).bounds(LEFT, this.height - 30, 260, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> {
            saveAll();
            RerollerConfig.save();
            this.minecraft.gui.setScreen(parent);
        }).bounds(this.width - 120, this.height - 30, 100, 20).build());
    }

    private void addRow(EnchantTarget target, int y) {
        EditBox idBox = new EditBox(this.font, LEFT, y, ID_WIDTH, 20, Texts.tr("screen.villager-reroll.enchant_id"));
        idBox.setValue(target.enchantmentId);
        idBox.setMaxLength(64);
        idBox.setHint(Texts.tr("screen.villager-reroll.hint_id"));

        int levelX = LEFT + ID_WIDTH + GAP;
        EditBox levelBox = new EditBox(this.font, levelX, y, LEVEL_WIDTH, 20, Texts.tr("screen.villager-reroll.min_level"));
        levelBox.setValue(String.valueOf(target.minLevel));
        levelBox.setMaxLength(2);
        levelBox.setHint(Texts.tr("screen.villager-reroll.hint_level"));

        int costX = levelX + LEVEL_WIDTH + GAP;
        EditBox costBox = new EditBox(this.font, costX, y, COST_WIDTH, 20, Texts.tr("screen.villager-reroll.max_cost"));
        costBox.setValue(String.valueOf(target.maxEmeraldCost));
        costBox.setMaxLength(2);
        costBox.setHint(Texts.tr("screen.villager-reroll.hint_cost"));

        int removeX = costX + COST_WIDTH + GAP;
        Button removeButton = Button.builder(Texts.tr("screen.villager-reroll.remove"), b -> {
            saveAll();
            RerollerConfig.get().targets.remove(target);
            RerollerConfig.save();
            rebuild();
        }).bounds(removeX, y, 65, 20).build();

        this.addRenderableWidget(idBox);
        this.addRenderableWidget(levelBox);
        this.addRenderableWidget(costBox);
        this.addRenderableWidget(removeButton);

        RowWidgets row = new RowWidgets(idBox, levelBox, costBox, target);
        rows.add(row);

        int suggestionsX = removeX + 65 + GAP;
        idBox.setResponder(text -> updateSuggestions(row, text, suggestionsX, y));
    }

    /** Shows up to MAX_SUGGESTIONS matching enchantment IDs to the right of the row being edited. */
    private void updateSuggestions(RowWidgets row, String typed, int x, int y) {
        for (Button b : row.suggestionButtons) this.removeWidget(b);
        row.suggestionButtons.clear();

        String query = typed.trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
        if (query.isEmpty()) return;
        if (knownEnchantmentIds.contains(typed.trim())) return; // already a full valid id, no need to suggest

        List<String> matches = knownEnchantmentIds.stream()
                .filter(id -> id.toLowerCase(Locale.ROOT).replace("minecraft:", "").contains(query))
                .limit(MAX_SUGGESTIONS)
                .toList();

        int rowX = x;
        for (String match : matches) {
            String shortName = match.contains(":") ? match.substring(match.indexOf(':') + 1) : match;
            Button suggestion = Button.builder(Component.literal(shortName), b -> {
                row.idBox.setValue(match);
                for (Button sb : row.suggestionButtons) this.removeWidget(sb);
                row.suggestionButtons.clear();
            }).bounds(rowX, y, SUGGESTION_WIDTH, SUGGESTION_HEIGHT).build();
            this.addRenderableWidget(suggestion);
            row.suggestionButtons.add(suggestion);
            rowX += SUGGESTION_WIDTH + 4;
        }
    }

    /** Rebuilds the widget list after an add/remove so row positions stay correct. */
    private void rebuild() {
        this.clearWidgets();
        this.init();
    }

    /** Writes whatever is currently typed in each row's boxes back into the config objects. */
    private void saveAll() {
        for (RowWidgets row : rows) {
            row.target.enchantmentId = row.idBox.getValue().trim();
            row.target.minLevel = parseIntClamped(row.levelBox.getValue(), row.target.minLevel, 1, 5);
            row.target.maxEmeraldCost = parseIntClamped(row.costBox.getValue(), row.target.maxEmeraldCost, 1, 64);
        }
    }

    private static int parseIntClamped(String s, int fallback, int min, int max) {
        try {
            int v = Integer.parseInt(s.trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        graphics.text(this.font, this.title,
                (this.width - this.font.width(this.title)) / 2, 12, 0xFFFFAA00);

        Component subtitle = Texts.tr("screen.villager-reroll.subtitle");
        graphics.text(this.font, subtitle,
                (this.width - this.font.width(subtitle)) / 2,
                26, 0xFFAAAAAA);

        if (!rows.isEmpty()) {
            graphics.text(this.font, Texts.tr("screen.villager-reroll.col_id"), LEFT, TOP - 12, 0xFFAAAAAA);
            graphics.text(this.font, Texts.tr("screen.villager-reroll.col_level"), LEFT + ID_WIDTH + GAP, TOP - 12, 0xFFAAAAAA);
            graphics.text(this.font, Texts.tr("screen.villager-reroll.col_cost"), LEFT + ID_WIDTH + GAP + LEVEL_WIDTH + GAP, TOP - 12, 0xFFAAAAAA);
        } else {
            graphics.text(this.font, Texts.tr("screen.villager-reroll.empty"),
                    LEFT, TOP, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        saveAll();
        RerollerConfig.save();
        this.minecraft.gui.setScreen(parent);
    }
}
