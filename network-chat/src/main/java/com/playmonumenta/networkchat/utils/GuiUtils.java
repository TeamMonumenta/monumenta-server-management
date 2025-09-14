package com.playmonumenta.networkchat.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class GuiUtils {
	public static void fillWithFiller(Inventory inventory, Material fillerMaterial) {
		ItemStack filler = new ItemStack(fillerMaterial, 1);
		ItemMeta meta = filler.getItemMeta();
		meta.displayName(Component.empty());
		filler.setItemMeta(meta);
		for (int i = 0; i < inventory.getSize(); i++) {
			if (inventory.getItem(i) == null) {
				inventory.setItem(i, filler.clone());
			}
		}
	}
}
