package com.playmonumenta.networkchat.inlinereplacements;

import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
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
import org.bukkit.inventory.ItemStack;
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
					lines.add(formatItem(inv.getHelmet(), "Helmet"));
					lines.add(formatItem(inv.getChestplate(), "Chestplate"));
					lines.add(formatItem(inv.getLeggings(), "Leggings"));
					lines.add(formatItem(inv.getBoots(), "Boots"));
					lines.add(formatItem(inv.getItemInOffHand(), "Offhand"));
					lines.add(formatItem(inv.getItemInMainHand(), "Mainhand"));
					MMLog.info(MessagingUtils.toJson(Component.text("EQUIPMENT").decoration(TextDecoration.BOLD, true)
						.hoverEvent(Component.join(JoinConfiguration.newlines(), lines))).toString());
					return Component.text("EQUIPMENT").decoration(TextDecoration.BOLD, true)
						.hoverEvent(Component.join(JoinConfiguration.newlines(), lines));
				}
			}
			return Component.text("<equipment>");
		});
	}

	private static Component formatItem(ItemStack item, String placeholderSlot) {
		// displayName() contains all the NBT in the hover event, so we set it to null
		return item == null || item.getType() == Material.AIR ? Component.text("No " + placeholderSlot, NamedTextColor.GRAY) : item.displayName().hoverEvent(null);
	}
}
