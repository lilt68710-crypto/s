package com.witherfang;

import net.fabricmc.api.ModInitializer;

public class Witherfang implements ModInitializer {
	public static final String MOD_ID = "witherfang";

	@Override
	public void onInitialize() {
		ModItems.init();
		WitherfangEvents.register();
	}
}
