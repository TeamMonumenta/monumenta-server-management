package com.playmonumenta.networkchat.tagresolvers;

import com.playmonumenta.networkchat.utils.MessagingUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

public class OptSpace {
	public static TagResolver optAppendSpace() {
		return TagResolver.resolver("opt_append_space", (ArgumentQueue args, Context context) -> {
			String contents = args.popOr("Expected minimessage value").value();
			Component component = context.deserialize(contents);
			if (MessagingUtils.plainText(component).isEmpty()) {
				return Tag.selfClosingInserting(Component.empty());
			}
			return Tag.selfClosingInserting(component.append(Component.space()));
		});
	}

	public static TagResolver optPrependSpace() {
		return TagResolver.resolver("opt_prepend_space", (ArgumentQueue args, Context context) -> {
			String contents = args.popOr("Expected minimessage value").value();
			Component component = context.deserialize(contents);
			if (MessagingUtils.plainText(component).isEmpty()) {
				return Tag.selfClosingInserting(Component.empty());
			}
			return Tag.selfClosingInserting(Component.space().append(component));
		});
	}
}
