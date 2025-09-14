package com.playmonumenta.networkchat.inlinereplacements;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.intellij.lang.annotations.RegExp;

public class EquipmentHoverReplacement extends InlineReplacement {

	private static final Set<Material> BANNED_MATERIALS;

	static {
		Set<Material> bannedMaterials = new HashSet<>();
		bannedMaterials.add(Material.WRITTEN_BOOK);

		for (Material mat : Material.values()) {
			if (!mat.isItem()) {
				continue;
			}

			String id = mat.getKey().toString();
			if (id.endsWith("shulker_box") || id.endsWith("_sign")) {
				bannedMaterials.add(mat);
			}
		}

		BANNED_MATERIALS = bannedMaterials.stream().collect(Collectors.toUnmodifiableSet());
	}

	@RegExp
	private static final String mEquipmentRegex = "<equipment>";

	public EquipmentHoverReplacement() {
		super("Equipment Hover",
			"(?<=^|[^\\\\])<(equipment)>",
			"equipmenthover");
		addHandler(mEquipmentRegex, sender -> {
			if (sender instanceof Player player
				&& !BANNED_MATERIALS.contains(player.getInventory().getItemInMainHand().getType())
				&& !BANNED_MATERIALS.contains(player.getInventory().getItemInOffHand().getType())) {
				ItemStack item = new ItemStack(Material.PAPER);
				ItemMeta meta = item.getItemMeta();
				meta.displayName(Component.text(player.getName() + "'s Equipment", NamedTextColor.WHITE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
				List<Component> lore = new ArrayList<>();
				lore.add(player.getInventory().getHelmet() != null ? player.getInventory().getHelmet().displayName().decoration(TextDecoration.ITALIC, false) : Component.text("No Helmet", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
				lore.add(player.getInventory().getChestplate() != null ? player.getInventory().getChestplate().displayName().decoration(TextDecoration.ITALIC, false) : Component.text("No Chestplate", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
				lore.add(player.getInventory().getLeggings() != null ? player.getInventory().getLeggings().displayName().decoration(TextDecoration.ITALIC, false) : Component.text("No Leggings", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
				lore.add(player.getInventory().getBoots() != null ? player.getInventory().getBoots().displayName().decoration(TextDecoration.ITALIC, false) : Component.text("No Boots", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
				lore.add(player.getInventory().getItemInOffHand().getType() != Material.AIR ? player.getInventory().getItemInOffHand().displayName().decoration(TextDecoration.ITALIC, false) : Component.text("No Offhand", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
				lore.add(player.getInventory().getItemInMainHand().getType() != Material.AIR ? player.getInventory().getItemInMainHand().displayName().decoration(TextDecoration.ITALIC, false) : Component.text("No Mainhand", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
				meta.lore(lore);
				item.setItemMeta(meta);
				return Component.text("EQUIPMENT").decoration(TextDecoration.BOLD, true).hoverEvent(item);
			}
			return Component.text("<equipment>");
		});
	}
}
