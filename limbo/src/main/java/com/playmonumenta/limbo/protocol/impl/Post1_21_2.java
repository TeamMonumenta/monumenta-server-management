package com.playmonumenta.limbo.protocol.impl;

import com.playmonumenta.limbo.ConnectionImpl;
import com.playmonumenta.limbo.network.PacketWriter;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class Post1_21_2 extends Post1_20_5 {
	public static final Data V1_21_2_DATA = V1_21_DATA
		.registry("damage_type", "ender_pearl")
		.loginId(44);

	public static final Data V1_21_4_DATA = V1_21_2_DATA;
	public static final Data V1_21_5_DATA = V1_21_4_DATA
		.loginId(43)
		.registry("minecraft:cat_variant", "minecraft:all_black")
		.registry("minecraft:chicken_variant", "minecraft:cold")
		.registry("minecraft:cow_variant", "minecraft:cold")
		.registry("minecraft:frog_variant", "minecraft:cold")
		.registry("minecraft:pig_variant", "minecraft:cold")
		.registry("minecraft:wolf_sound_variant", "minecraft:classic");
	public static final Data V1_21_6_DATA = V1_21_5_DATA;
	public static final Data V1_21_7_DATA = V1_21_6_DATA;

	@Override
	protected void writeCommonRespawnData(PacketWriter writer) {
		super.writeCommonRespawnData(writer);
		writer.varInt(64);
	}

	public Post1_21_2(Data data, ConnectionImpl connection) {
		super(data, connection);
	}
}
