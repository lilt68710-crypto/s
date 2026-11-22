package com.witherfang;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;

public final class ModItems {
	// Netherite-grade material: durability, mining speed, damage bonus, enchantability
	public static final ToolMaterial WITHERFANG_MATERIAL = new ToolMaterial(
			BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
			2031,
			9.0F,
			4.0F,
			15,
			ItemTags.NETHERITE_TOOL_MATERIALS
	);

	public static final Item WITHERFANG = register(
			"witherfang",
			WitherfangItem::new,
			new Item.Properties()
					.sword(WITHERFANG_MATERIAL, 3.0F, -2.4F)
					.rarity(Rarity.EPIC)
					.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
	);

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Witherfang.MOD_ID, name));
		T item = factory.apply(properties.setId(key));
		Registry.register(BuiltInRegistries.ITEM, key, item);
		return item;
	}

	public static void init() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT)
				.register(output -> output.accept(WITHERFANG));
	}

	private ModItems() {
	}
}
