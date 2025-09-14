package com.playmonumenta.networkchat.inlinereplacements;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.intellij.lang.annotations.RegExp;

public class ItemHoverReplacement extends InlineReplacement {

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
	private static final String mMainhandRegex = "<mainhand>";
	@RegExp
	private static final String mOffhandRegex = "<offhand>";

	public ItemHoverReplacement() {
		super("Item Hover",
			"(?<=^|[^\\\\])<(mainhand|offhand)>",
			"itemhover");
		addHandler(mMainhandRegex, sender -> {
			if (sender instanceof Player player
				&& !BANNED_MATERIALS.contains(player.getInventory().getItemInMainHand().getType())) {
				ItemStack item = player.getInventory().getItemInMainHand();
				return item.displayName().hoverEvent(item);
			}
			return Component.text("<mainhand>");
		});
		addHandler(mOffhandRegex, sender -> {
			if (sender instanceof Player player
				&& !BANNED_MATERIALS.contains(player.getInventory().getItemInOffHand().getType())) {
				ItemStack item = player.getInventory().getItemInOffHand();
				return item.displayName().hoverEvent(item);
			}
			return Component.text("<offhand>");
		});
	}
}
