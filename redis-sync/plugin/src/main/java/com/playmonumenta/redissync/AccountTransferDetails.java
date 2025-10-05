package com.playmonumenta.redissync;

import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public record AccountTransferDetails(
	LocalDateTime transferTime,
	UUID oldId,
	UUID newId,
	String oldName,
	String newName
) implements Comparable<AccountTransferDetails> {
	private static final Comparator<AccountTransferDetails> COMPARATOR =
		Comparator.comparing(AccountTransferDetails::transferTime)
			.thenComparing(AccountTransferDetails::oldId)
			.thenComparing(AccountTransferDetails::newId)
			.thenComparing(AccountTransferDetails::oldName)
			.thenComparing(AccountTransferDetails::newName);

	AccountTransferDetails(JsonObject data) {
		this(
			AccountTransferManager.EPOCH.plus(
				data.getAsJsonPrimitive("timestamp_millis").getAsLong(),
				ChronoUnit.MILLIS
			),
			UUID.fromString(data.getAsJsonPrimitive("old_id").getAsString()),
			UUID.fromString(data.getAsJsonPrimitive("new_id").getAsString()),
			data.getAsJsonPrimitive("old_name").getAsString(),
			data.getAsJsonPrimitive("new_name").getAsString()
		);
	}

	AccountTransferDetails(AccountTransferDetails oldTransfer, AccountTransferDetails newTransfer) {
		this(
			newTransfer.transferTime,
			oldTransfer.oldId,
			newTransfer.newId,
			oldTransfer.oldName,
			newTransfer.newName
		);
	}

	@Override
	public int compareTo(@NotNull AccountTransferDetails o) {
		return COMPARATOR.compare(this, o);
	}
}
