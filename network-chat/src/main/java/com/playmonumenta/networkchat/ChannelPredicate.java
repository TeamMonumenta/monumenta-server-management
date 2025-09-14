package com.playmonumenta.networkchat;

import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.channel.ChannelFuture;
import com.playmonumenta.networkchat.channel.ChannelLoading;
import com.playmonumenta.networkchat.channel.interfaces.ChannelAutoJoin;
import com.playmonumenta.networkchat.channel.interfaces.ChannelInviteOnly;
import com.playmonumenta.networkchat.channel.interfaces.ChannelPermissionNode;
import java.util.Objects;
import java.util.function.Predicate;
import org.bukkit.command.CommandSender;

public interface ChannelPredicate {
	default ChannelPredicate and(ChannelPredicate other) {
		return (sender, channel) -> test(sender, channel) && other.test(sender, channel);
	}

	default ChannelPredicate isEqual(Object target) {
		return (sender, channel) -> Objects.equals(channel, target);
	}

	default ChannelPredicate negate() {
		return (sender, channel) -> !test(sender, channel);
	}

	static ChannelPredicate not(ChannelPredicate other) {
		return other.negate();
	}

	default ChannelPredicate or(ChannelPredicate other) {
		return (sender, channel) -> test(sender, channel) || other.test(sender, channel);
	}

	boolean test(CommandSender sender, Channel channel);

	static ChannelPredicate fromPredicate(Predicate<Channel> other) {
		return (sender, channel) -> other.test(channel);
	}

	default Predicate<Channel> toPredicate(CommandSender sender) {
		return channel -> test(sender, channel);
	}

	ChannelPredicate SAFE = (sender, channel) -> !(channel instanceof ChannelLoading || channel instanceof ChannelFuture);

	ChannelPredicate MAY_MANAGE = (sender, channel) -> channel.mayManage(sender);

	ChannelPredicate MAY_CHAT = (sender, channel) -> channel.mayChat(sender);

	ChannelPredicate MAY_LISTEN = (sender, channel) -> channel.mayListen(sender);

	ChannelPredicate INSTANCE_OF_AUTOJOIN = (sender, channel) -> channel instanceof ChannelAutoJoin;

	ChannelPredicate NOT_INSTANCE_OF_AUTOJOIN = (sender, channel) -> !(channel instanceof ChannelAutoJoin);

	ChannelPredicate INSTANCE_OF_INVITE_ONLY = (sender, channel) -> channel instanceof ChannelInviteOnly;

	ChannelPredicate NOT_INSTANCE_OF_INVITE_ONLY = (sender, channel) -> !(channel instanceof ChannelInviteOnly);

	ChannelPredicate INSTANCE_OF_PERMISSION_NODE = (sender, channel) -> channel instanceof ChannelPermissionNode;

	ChannelPredicate NOT_INSTANCE_OF_PERMISSION_NODE = (sender, channel) -> !(channel instanceof ChannelPermissionNode);

	static ChannelPredicate channelType(String channelType) {
		return (sender, channel) -> channel.getClassId().equals(channelType);
	}
}
