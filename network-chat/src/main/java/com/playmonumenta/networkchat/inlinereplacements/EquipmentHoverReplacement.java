package com.playmonumenta.networkchat.inlinereplacements;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
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
		super(
			"Equipment Hover",
			"(?<=^|[^\\\\])<(equipment)>",
			"equipmenthover"
		);
		addHandler(mEquipmentRegex, sender -> {
			if (sender instanceof Player player) {
				PlayerInventory inv = player.getInventory();
				if (
					!BANNED_MATERIALS.contains(inv.getItemInMainHand().getType()) &&
					!BANNED_MATERIALS.contains(inv.getItemInOffHand().getType())
				) {
					List<Component> lines = new ArrayList<>();
					lines.add(Component.text(player.getName() + "'s Equipment", NamedTextColor.WHITE, TextDecoration.BOLD));
					lines.add(inv.getHelmet() != null ? inv.getHelmet().displayName() : Component.text("No Helmet", NamedTextColor.GRAY));
					lines.add(inv.getChestplate() != null ? inv.getChestplate().displayName() : Component.text("No Chestplate", NamedTextColor.GRAY));
					lines.add(inv.getLeggings() != null ? inv.getLeggings().displayName() : Component.text("No Leggings", NamedTextColor.GRAY));
					lines.add(inv.getBoots() != null ? inv.getBoots().displayName() : Component.text("No Boots", NamedTextColor.GRAY));
					lines.add(inv.getItemInOffHand().getType() != Material.AIR ? inv.getItemInOffHand().displayName() : Component.text("No Offhand", NamedTextColor.GRAY));
					lines.add(inv.getItemInMainHand().getType() != Material.AIR ? inv.getItemInMainHand().displayName() : Component.text("No Mainhand", NamedTextColor.GRAY));
					return Component.text("EQUIPMENT").decoration(TextDecoration.BOLD, true)
						.hoverEvent(Component.join(JoinConfiguration.newlines(), lines));
				}
			}
			return Component.text("<equipment>");
		});
	}
}
