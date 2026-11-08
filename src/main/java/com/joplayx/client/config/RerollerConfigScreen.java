package com.joplayx.client.config;

import com.joplayx.client.Texts;
import com.joplayx.client.VillagerRerollClient;
import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.FloatSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class RerollerConfigScreen {

    public static Screen create(Screen parent) {
        RerollerConfig.Config cfg = RerollerConfig.get();
        Minecraft mc = Minecraft.getInstance();

        return YetAnotherConfigLib.createBuilder()
                .title(Texts.tr("screen.villager-reroll.title"))
                .save(RerollerConfig::save)

                .category(ConfigCategory.createBuilder()
                        .name(Texts.tr("config.villager-reroll.lectern"))
                        .tooltip(Texts.tr("config.villager-reroll.lectern.tooltip"))

                        .option(ButtonOption.createBuilder()
                                .name(Texts.tr("config.villager-reroll.use_position"))
                                .description(OptionDescription.of(Texts.tr(
                                        "config.villager-reroll.use_position.desc",
                                        VillagerRerollClient.setPositionKey.getTranslatedKeyMessage())))
                                .text(mc.player != null
                                        ? Texts.tr("config.villager-reroll.use_position.here",
                                        (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ())
                                        : Texts.tr("config.villager-reroll.use_position.no_player"))
                                .action((screen, opt) -> {
                                    if (mc.player == null) return;
                                    Component message = VillagerRerollClient.setLecternFromCrosshair(mc);
                                    if (cfg.hasLecternPos()) mc.gui.setScreen(create(parent));
                                    mc.player.sendSystemMessage(message);
                                })
                                .build()
                        )

                        .option(ButtonOption.createBuilder()
                                .name(Texts.tr("config.villager-reroll.clear"))
                                .description(OptionDescription.of(Texts.tr("config.villager-reroll.clear.desc")))
                                .text(cfg.hasLecternPos()
                                        ? Texts.tr("config.villager-reroll.clear.current", cfg.lecternPosString())
                                        : Texts.tr("config.villager-reroll.clear.unset"))
                                .action((screen, opt) -> {
                                    cfg.clearLecternPos();
                                    RerollerConfig.save();
                                    mc.gui.setScreen(create(parent));
                                })
                                .build()
                        )

                        .option(Option.<Integer>createBuilder()
                                .name(Texts.tr("config.villager-reroll.x"))
                                .description(OptionDescription.of(Texts.tr("config.villager-reroll.x.desc")))
                                .binding(
                                        0,
                                        () -> cfg.lecternX == Integer.MIN_VALUE ? 0 : cfg.lecternX,
                                        val -> cfg.lecternX = val
                                )
                                .controller(IntegerFieldControllerBuilder::create)
                                .build()
                        )

                        .option(Option.<Integer>createBuilder()
                                .name(Texts.tr("config.villager-reroll.y"))
                                .description(OptionDescription.of(Texts.tr("config.villager-reroll.y.desc")))
                                .binding(
                                        64,
                                        () -> cfg.lecternY == Integer.MIN_VALUE ? 64 : cfg.lecternY,
                                        val -> cfg.lecternY = val
                                )
                                .controller(IntegerFieldControllerBuilder::create)
                                .build()
                        )

                        .option(Option.<Integer>createBuilder()
                                .name(Texts.tr("config.villager-reroll.z"))
                                .description(OptionDescription.of(Texts.tr("config.villager-reroll.z.desc")))
                                .binding(
                                        0,
                                        () -> cfg.lecternZ == Integer.MIN_VALUE ? 0 : cfg.lecternZ,
                                        val -> cfg.lecternZ = val
                                )
                                .controller(IntegerFieldControllerBuilder::create)
                                .build()
                        )

                        .build()
                )

                .category(ConfigCategory.createBuilder()
                        .name(Texts.tr("config.villager-reroll.timing"))
                        .tooltip(Texts.tr("config.villager-reroll.timing.tooltip"))

                        .option(Option.<Integer>createBuilder()
                                .name(Texts.tr("config.villager-reroll.close_delay"))
                                .description(OptionDescription.of(Texts.tr("config.villager-reroll.close_delay.desc")))
                                .binding(20, () -> cfg.closeDelayTicks, val -> cfg.closeDelayTicks = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(5, 100).step(5))
                                .build()
                        )

                        .option(Option.<Integer>createBuilder()
                                .name(Texts.tr("config.villager-reroll.retry_delay"))
                                .description(OptionDescription.of(Texts.tr("config.villager-reroll.retry_delay.desc")))
                                .binding(40, () -> cfg.retryDelayTicks, val -> cfg.retryDelayTicks = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(10, 200).step(10))
                                .build()
                        )

                        .option(Option.<Boolean>createBuilder()
                                .name(Texts.tr("config.villager-reroll.pickup"))
                                .description(OptionDescription.of(Texts.tr("config.villager-reroll.pickup.desc")))
                                .binding(true, () -> cfg.autoPickup, val -> cfg.autoPickup = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build()
                        )

                        .option(Option.<Integer>createBuilder()
                                .name(Texts.tr("config.villager-reroll.radius"))
                                .description(OptionDescription.of(Texts.tr("config.villager-reroll.radius.desc")))
                                .binding(6, () -> cfg.searchRadius, val -> cfg.searchRadius = val)
                                .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(1, 16).step(1))
                                .build()
                        )

                        .build()
                )

                .category(ConfigCategory.createBuilder()
                        .name(Texts.tr("config.villager-reroll.display"))
                        .tooltip(Texts.tr("config.villager-reroll.display.tooltip"))

                        .option(Option.<Boolean>createBuilder()
                                .name(Texts.tr("config.villager-reroll.hud"))
                                .description(OptionDescription.of(Texts.tr("config.villager-reroll.hud.desc")))
                                .binding(true, () -> cfg.hudEnabled, val -> cfg.hudEnabled = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build()
                        )

                        .option(Option.<Float>createBuilder()
                                .name(Texts.tr("config.villager-reroll.hud_scale"))
                                .description(OptionDescription.of(Texts.tr("config.villager-reroll.hud_scale.desc")))
                                .binding(1.0f, () -> cfg.hudScale, val -> cfg.hudScale = val)
                                .controller(opt -> FloatSliderControllerBuilder.create(opt)
                                        .range(RerollerConfig.MIN_HUD_SCALE, RerollerConfig.MAX_HUD_SCALE)
                                        .step(0.05f))
                                .build()
                        )

                        .option(Option.<RerollerConfig.HudCorner>createBuilder()
                                .name(Texts.tr("config.villager-reroll.hud_pos"))
                                .description(OptionDescription.of(Texts.tr("config.villager-reroll.hud_pos.desc")))
                                .binding(RerollerConfig.HudCorner.MIDDLE_LEFT, () -> cfg.hudCorner, val -> cfg.hudCorner = val)
                                .controller(opt -> EnumControllerBuilder.create(opt)
                                        .enumClass(RerollerConfig.HudCorner.class)
                                        .formatValue(corner -> Texts.tr(
                                                "config.villager-reroll.corner." + corner.name().toLowerCase(Locale.ROOT))))
                                .build()
                        )

                        .option(Option.<Boolean>createBuilder()
                                .name(Texts.tr("config.villager-reroll.hud_compact"))
                                .description(OptionDescription.of(Texts.tr("config.villager-reroll.hud_compact.desc")))
                                .binding(false, () -> cfg.hudCompact, val -> cfg.hudCompact = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build()
                        )

                        .option(Option.<Boolean>createBuilder()
                                .name(Texts.tr("config.villager-reroll.highlight"))
                                .description(OptionDescription.of(Texts.tr("config.villager-reroll.highlight.desc")))
                                .binding(true, () -> cfg.highlightVillager, val -> cfg.highlightVillager = val)
                                .controller(TickBoxControllerBuilder::create)
                                .build()
                        )

                        .build()
                )

                .build()
                .generateScreen(parent);
    }
}
