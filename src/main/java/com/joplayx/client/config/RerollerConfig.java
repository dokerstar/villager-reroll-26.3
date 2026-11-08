package com.joplayx.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.joplayx.VillagerReroll;
import com.joplayx.client.Texts;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class RerollerConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance()
            .getConfigDir().resolve("villager-reroll.json");

    public static final float MIN_HUD_SCALE = 0.5f;
    public static final float MAX_HUD_SCALE = 2.0f;

    private static Config instance = new Config();

    public static Config get() { return instance; }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                instance = GSON.fromJson(reader, Config.class);
                if (instance == null) instance = new Config();
                instance.sanitize();
                VillagerReroll.LOGGER.info("[Reroller] Config loaded.");
            } catch (IOException e) {
                VillagerReroll.LOGGER.error("[Reroller] Failed to load config.", e);
                instance = new Config();
            }
        } else {
            instance = new Config();
            save();
        }
    }

    public static void save() {
        try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(instance, writer);
            VillagerReroll.LOGGER.info("[Reroller] Config saved.");
        } catch (IOException e) {
            VillagerReroll.LOGGER.error("[Reroller] Failed to save config.", e);
        }
    }

    public static class Config {
        // Every enchantment the reroller must find on ONE villager's trades
        // simultaneously before it stops. All of them, not just the first match.
        public List<EnchantTarget> targets = new ArrayList<>();

        // Lectern position stored as separate ints so YACL can edit them directly
        // Integer.MIN_VALUE means "not set"
        public int lecternX = Integer.MIN_VALUE;
        public int lecternY = Integer.MIN_VALUE;
        public int lecternZ = Integer.MIN_VALUE;

        // Timing
        public int closeDelayTicks = 20;
        public int retryDelayTicks = 40;

        // Villager search radius (blocks) used when no villager has been selected
        public int searchRadius = 6;

        // Display
        public boolean hudEnabled = true;
        public float hudScale = 1.0f;
        public HudCorner hudCorner = HudCorner.MIDDLE_LEFT;
        public boolean hudCompact = false;
        public boolean highlightVillager = true;

        // Walk over to a lectern that dropped out of pickup range, then walk back
        public boolean autoPickup = true;

        /**
         * Older config files predate the newer fields, so Gson leaves them null/zero.
         * Pull everything back into a sane range after loading.
         */
        void sanitize() {
            if (targets == null) targets = new ArrayList<>();
            if (hudCorner == null) hudCorner = HudCorner.MIDDLE_LEFT;
            if (hudScale < MIN_HUD_SCALE || hudScale > MAX_HUD_SCALE) hudScale = 1.0f;
            if (searchRadius < 1 || searchRadius > 16) searchRadius = 6;
            closeDelayTicks = Math.clamp(closeDelayTicks, 5, 100);
            retryDelayTicks = Math.clamp(retryDelayTicks, 10, 200);
        }

        /**
         * Returns the lectern BlockPos, or null if not set.
         */
        public BlockPos lecternPos() {
            if (lecternX == Integer.MIN_VALUE) return null;
            return new BlockPos(lecternX, lecternY, lecternZ);
        }

        /**
         * Returns true if the lectern position has been set.
         */
        public boolean hasLecternPos() {
            return lecternX != Integer.MIN_VALUE;
        }

        /**
         * Sets the lectern position from a BlockPos.
         */
        public void setLecternPos(BlockPos pos) {
            lecternX = pos.getX();
            lecternY = pos.getY();
            lecternZ = pos.getZ();
        }

        /**
         * Clears the lectern position.
         */
        public void clearLecternPos() {
            lecternX = Integer.MIN_VALUE;
            lecternY = Integer.MIN_VALUE;
            lecternZ = Integer.MIN_VALUE;
        }

        /**
         * Returns a human-readable string of the lectern position.
         */
        public String lecternPosString() {
            if (!hasLecternPos()) return Texts.str("message.villager-reroll.pos_unset");
            return Texts.str("message.villager-reroll.pos_coords", lecternX, lecternY, lecternZ);
        }
    }

    /** Where the HUD panel is anchored on screen. */
    public enum HudCorner {
        TOP_LEFT, MIDDLE_LEFT, BOTTOM_LEFT, TOP_RIGHT, MIDDLE_RIGHT, BOTTOM_RIGHT;

        public boolean isRight() {
            return this == TOP_RIGHT || this == MIDDLE_RIGHT || this == BOTTOM_RIGHT;
        }
    }

    /**
     * One enchantment the reroller should look for, with its own level/price criteria.
     * A trade must meet ALL THREE (enchantment id, minLevel, maxEmeraldCost) to count
     * as satisfying this target.
     */
    public static class EnchantTarget {
        public String enchantmentId = "";
        public int minLevel = 1;
        public int maxEmeraldCost = 64;

        public EnchantTarget() {
        }

        public EnchantTarget(String enchantmentId, int minLevel, int maxEmeraldCost) {
            this.enchantmentId = enchantmentId;
            this.minLevel = minLevel;
            this.maxEmeraldCost = maxEmeraldCost;
        }
    }
}
