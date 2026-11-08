package com.joplayx.client.util;

import com.joplayx.client.config.RerollerConfig;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;

/**
 * Trade reading and evaluation utilities.
 *
 * Reads from OffersStore which is populated by MerchantOffersPacketMixin
 * when the server sends ClientboundMerchantOffersPacket.
 *
 * Your configured targets are a WISHLIST, not a simultaneous-match requirement -
 * breaking/replacing the lectern wipes and re-rolls a villager's ENTIRE trade list,
 * so there's no way to "keep" one good trade while re-rolling for another. Instead:
 * the reroller stops on the FIRST target it finds, and that target is removed from
 * the config so the next time you start it (on a new villager), it's only looking
 * for whatever's left on the wishlist.
 */
public class TradeUtil {

    /** Whether a specific target was satisfied by the villager's current trades, and what was found for it. */
    public record TargetStatus(RerollerConfig.EnchantTarget target, boolean satisfied, String description) {}

    /**
     * foundTarget is the first configured target satisfied by the villager's current
     * trades, or null if none are. statuses covers every configured target (for HUD
     * display), even though only the first match actually stops the reroller.
     * lastOfferSeen is the last enchanted book offer encountered this check, regardless
     * of whether it matched anything - useful for confirming trades are being read at all.
     */
    public record TradeResult(RerollerConfig.EnchantTarget foundTarget, List<TargetStatus> statuses, String lastOfferSeen) {

        public boolean anyFound() {
            return foundTarget != null;
        }

        public String descriptionFor(RerollerConfig.EnchantTarget target) {
            return statuses.stream()
                    .filter(s -> s.target() == target)
                    .findFirst()
                    .map(TargetStatus::description)
                    .orElse("");
        }
    }

    private record SeenOffer(Holder<Enchantment> holder, String enchantmentId, int level, int cost) {}

    public static TradeResult checkTrades(RerollerConfig.Config cfg) {
        MerchantOffers offers = OffersStore.get();

        if (cfg.targets.isEmpty()) {
            return new TradeResult(null, List.of(), "");
        }

        Minecraft mc = Minecraft.getInstance();
        if (offers == null || offers.isEmpty() || mc.level == null) {
            List<TargetStatus> statuses = new ArrayList<>();
            for (RerollerConfig.EnchantTarget t : cfg.targets) {
                statuses.add(new TargetStatus(t, false, Component.translatable("message.villager-reroll.no_trades").getString()));
            }
            return new TradeResult(null, statuses, Component.translatable("message.villager-reroll.no_trades").getString());
        }

        // Registry lookup — confirmed from working mod line 707
        var reg = mc.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);

        // Single pass over every enchanted book offer, so both per-target matching
        // and the general "last offer seen" summary come from the same data.
        List<SeenOffer> seen = new ArrayList<>();

        for (MerchantOffer offer : offers) {
            ItemStack result = offer.getResult();
            if (!result.is(Items.ENCHANTED_BOOK)) continue;

            // EnchantmentHelper.getEnchantmentsForCrafting — confirmed from working mod line 686
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : EnchantmentHelper.getEnchantmentsForCrafting(result).entrySet()) {
                Holder<Enchantment> enchHolder = entry.getKey();
                int level = entry.getIntValue();
                // Registry key string — confirmed from working mod line 708
                String enchantmentId = reg.getKey(enchHolder.value()).toString();

                // getCostA() returns the CURRENT price (after demand/reputation/Hero of
                // the Village discounts are applied) - getBaseCostA() returns the original
                // undiscounted price, which is what the old code was reading. That's the
                // markdown bug: a trade discounted to 18 emeralds with a base price above
                // your max would get rejected even though the actual price was fine.
                int cost = offer.getCostA().getCount();

                seen.add(new SeenOffer(enchHolder, enchantmentId, level, cost));
            }
        }

        String lastOfferSeen = seen.isEmpty()
                ? Component.translatable("message.villager-reroll.no_books").getString()
                : describe(seen.getLast(), false);

        List<TargetStatus> statuses = new ArrayList<>();
        RerollerConfig.EnchantTarget foundTarget = null;

        for (RerollerConfig.EnchantTarget target : cfg.targets) {
            boolean satisfied = false;
            String description = Component.translatable("message.villager-reroll.not_offered").getString();

            for (SeenOffer s : seen) {
                if (!s.enchantmentId().equals(target.enchantmentId)) continue;
                String desc = describe(s, false);

                if (s.level() >= target.minLevel && s.cost() <= target.maxEmeraldCost) {
                    satisfied = true;
                    description = desc;
                    break;
                } else if (!satisfied) {
                    description = describe(s, true);
                }
            }

            statuses.add(new TargetStatus(target, satisfied, description));

            // First match wins - only assign once, but keep scanning the rest so the
            // HUD can still show accurate status for every target, not just the winner.
            if (satisfied && foundTarget == null) {
                foundTarget = target;
            }
        }

        return new TradeResult(foundTarget, statuses, lastOfferSeen);
    }

    private static String describe(SeenOffer offer, boolean mismatch) {
        Component name = Enchantment.getFullname(offer.holder(), offer.level());
        String key = mismatch
                ? "message.villager-reroll.offer_mismatch"
                : "message.villager-reroll.offer";
        return Component.translatable(key, name, offer.cost()).getString();
    }
}
