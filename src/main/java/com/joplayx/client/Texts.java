package com.joplayx.client;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public final class Texts {

	private Texts() {}

	public static MutableComponent tr(String key, Object... args) {
		return Component.translatable(key, args);
	}

	public static Component prefixed(String key, Object... args) {
		return tr("message.villager-reroll.prefix").append(tr(key, args));
	}

	public static String str(String key, Object... args) {
		return tr(key, args).getString();
	}
}
