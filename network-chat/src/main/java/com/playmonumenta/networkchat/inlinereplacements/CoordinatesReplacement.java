package com.playmonumenta.networkchat.inlinereplacements;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.intellij.lang.annotations.RegExp;

public class CoordinatesReplacement extends InlineReplacement {

	@RegExp
	private static final String mCoordinatesRegex = "<coords>";

	public CoordinatesReplacement() {
		super("Coordinates",
			"(?<=^|[^\\\\])<(coordinates|coords)>",
			"coordinatesreplacement");
		addHandler(mCoordinatesRegex, sender -> {
			if (sender instanceof Player player) {
				Location loc = player.getLocation();
				String coordinates = (int) loc.getX() + ", " + (int) loc.getY() + ", " + (int) loc.getZ();
				String coordinatesNoComma = (int) loc.getX() + " " + (int) loc.getY() + " " + (int) loc.getZ();
				return Component.text(coordinates).decoration(TextDecoration.UNDERLINED, true)
					.hoverEvent(Component.text("Click to add " + player.getName() + "'s location as a compass waypoint."))
					.clickEvent(ClickEvent.runCommand("/waypoint set @s \"&a&l" + player.getName() + "'s Coordinates\" \"&a" + coordinates + "\" " + coordinatesNoComma + " " + player.getWorld().getName()));
			}
			return Component.text("<coordinates>");
		});
	}
}
