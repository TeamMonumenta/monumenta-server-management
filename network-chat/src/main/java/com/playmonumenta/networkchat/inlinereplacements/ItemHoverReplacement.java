package com.playmonumenta.networkchat.inlinereplacements;

import com.destroystokyo.paper.Namespaced;
import com.google.common.collect.Multimap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
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
		addHandler(mMainhandRegex, sender -> hoverComponent(sender, true));
		addHandler(mOffhandRegex, sender -> hoverComponent(sender, false));
	}

	@SuppressWarnings("EnumOrdinal")
	public static Component hoverComponent(CommandSender sender, boolean isMainHand) {
		if (sender instanceof Player player) {
		    ItemStack item = isMainHand ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand();
			if (!BANNED_MATERIALS.contains(item.getType())) {
				List<Component> lines = new ArrayList<>();
				lines.add(item.displayName());
				ItemMeta meta = item.getItemMeta();

				if (!meta.hasItemFlag(ItemFlag.HIDE_POTION_EFFECTS)) {
					if (meta instanceof BookMeta bookMeta) {
						Component author = bookMeta.author();
						if (bookMeta.hasAuthor() && author != null) {
							lines.add(Component.translatable("book.byAuthor", List.of(author))
								.color(NamedTextColor.GRAY));
						}
						BookMeta.Generation generation = bookMeta.getGeneration();
						if (bookMeta.hasGeneration() && generation != null) {
							int generationId = generation.ordinal();
							lines.add(Component.translatable(String.format("book.generation.%d", generationId), NamedTextColor.GRAY));
						}
					}
				}

				if (!meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) {
					for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
						lines.add(entry.getKey().displayName(entry.getValue()).color(NamedTextColor.GRAY));
					}
				}

				List<Component> lore = meta.lore();
				if (lore != null) {
					lines.addAll(lore);
				}

				Multimap<Attribute, AttributeModifier> attributeModifiers = meta.getAttributeModifiers();
				if (!meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES) && attributeModifiers != null) {
					Map<EquipmentSlot, List<AttributeAndModifier>> modifiersBySlot = new HashMap<>();

					for (Map.Entry<Attribute, AttributeModifier> entry : attributeModifiers.entries()) {
						Attribute attribute = entry.getKey();
						AttributeModifier modifier = entry.getValue();
						modifiersBySlot.computeIfAbsent(modifier.getSlot(), k -> new ArrayList<>())
							.add(new AttributeAndModifier(attribute, modifier));
					}

					List<AttributeAndModifier> onAllSlots = modifiersBySlot.getOrDefault(null, List.of());

					for (EquipmentSlot slot : EquipmentSlot.values()) {
						List<AttributeAndModifier> slotAttributes = new ArrayList<>(onAllSlots);
						slotAttributes.addAll(modifiersBySlot.getOrDefault(slot, List.of()));
						if (slotAttributes.isEmpty()) {
							continue;
						}

						String whenInSlotTranslatable = switch (slot) {
							case HAND -> "item.modifiers.mainhand";
							case OFF_HAND -> "item.modifiers.offhand";
							case FEET -> "item.modifiers.feet";
							case LEGS -> "item.modifiers.legs";
							case CHEST -> "item.modifiers.chest";
							case HEAD -> "item.modifiers.head";
						};

						lines.add(Component.empty());
						lines.add(Component.translatable(whenInSlotTranslatable, NamedTextColor.GRAY));
						for (AttributeAndModifier attributeAndModifier : slotAttributes) {
							Attribute attribute = attributeAndModifier.attribute;

							AttributeModifier modifier = attributeAndModifier.modifier;
							double amount = modifier.getAmount();
							boolean isNegative = amount < 0;
							int operationId = modifier.getOperation().ordinal();
							String modifierTranslatable = String.format(
								"attribute.modifier.%s.%d",
								isNegative ? "take" : "plus",
								operationId
							);
							List<Component> modifierTranslatableArguments = List.of(
								Component.text(isNegative ? -amount : amount),
								Component.translatable(attribute)
							);

							lines.add(Component.translatable(modifierTranslatable, modifierTranslatableArguments)
								.color(NamedTextColor.BLUE));
						}
					}
				}

				if (!meta.hasItemFlag(ItemFlag.HIDE_UNBREAKABLE) && meta.isUnbreakable()) {
					lines.add(Component.translatable("item.unbreakable", NamedTextColor.BLUE));
				}

				Set<Namespaced> destroyables = meta.getDestroyableKeys();
				if (!meta.hasItemFlag(ItemFlag.HIDE_DESTROYS) && !destroyables.isEmpty()) {
					lines.add(Component.empty());
					lines.add(Component.translatable("item.canBreak", NamedTextColor.GRAY));
					for (Namespaced destoryable : destroyables) {
						lines.add(Component.translatable(
							String.format("block.%s.%s", destoryable.getNamespace(), destoryable.getKey()),
							NamedTextColor.DARK_GRAY
						));
					}
				}

				Set<Namespaced> placeables = meta.getPlaceableKeys();
				if (!meta.hasItemFlag(ItemFlag.HIDE_DESTROYS) && !placeables.isEmpty()) {
					lines.add(Component.empty());
					lines.add(Component.translatable("item.canPlace", NamedTextColor.GRAY));
					for (Namespaced placeable : placeables) {
						lines.add(Component.translatable(
							String.format("block.%s.%s", placeable.getNamespace(), placeable.getKey()),
							NamedTextColor.DARK_GRAY
						));
					}
				}

				return item.displayName().hoverEvent(Component.join(JoinConfiguration.newlines(), lines));
		    }
		}
		return Component.text(isMainHand ? "<mainhand>" : "<offhand>");
	}

	public record AttributeAndModifier(Attribute attribute, AttributeModifier modifier) {
	}
}
