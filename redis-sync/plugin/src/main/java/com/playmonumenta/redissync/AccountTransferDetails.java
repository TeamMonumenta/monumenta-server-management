package com.playmonumenta.redissync;

import com.google.gson.JsonObject;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;

public class AccountTransferDetails implements Comparable<AccountTransferDetails> {
	private final LocalDateTime mTransferTime;
	private final UUID mOldId;
	private final UUID mNewId;
	private final String mOldName;
	private final String mNewName;

	AccountTransferDetails(JsonObject data) {
		long timestampMillis = data.getAsJsonPrimitive("timestamp_millis").getAsLong();
		mTransferTime = AccountTransferManager.EPOCH.plus(timestampMillis, ChronoUnit.MILLIS);

		String oldIdStr = data.getAsJsonPrimitive("old_id").getAsString();
		mOldId = UUID.fromString(oldIdStr);

		String newIdStr = data.getAsJsonPrimitive("new_id").getAsString();
		mNewId = UUID.fromString(newIdStr);

		mOldName = data.getAsJsonPrimitive("old_name").getAsString();

		mNewName = data.getAsJsonPrimitive("new_name").getAsString();
	}

	// Merge transfers if relevant
	AccountTransferDetails(AccountTransferDetails oldTransfer, AccountTransferDetails newTransfer) {
		mTransferTime = newTransfer.mTransferTime;
		mOldId = oldTransfer.mOldId;
		mOldName = oldTransfer.mOldName;
		mNewId = newTransfer.mNewId;
		mNewName = newTransfer.mNewName;
	}

	public LocalDateTime transferTime() {
		return mTransferTime;
	}

	public UUID oldId() {
		return mOldId;
	}

	public UUID newId() {
		return mNewId;
	}

	public String oldName() {
		return mOldName;
	}

	public String newName() {
		return mNewName;
	}

	@Override
	public int compareTo(@NotNull AccountTransferDetails o) {
		int diff;

		diff = mTransferTime.compareTo(o.mTransferTime);
		if (diff != 0) {
			return diff;
		}

		diff = mOldId.compareTo(o.mOldId);
		if (diff != 0) {
			return diff;
		}

		diff = mNewId.compareTo(o.mNewId);
		if (diff != 0) {
			return diff;
		}

		diff = mOldName.compareTo(o.mOldName);
		if (diff != 0) {
			return diff;
		}

		return mNewName.compareTo(o.mNewName);
	}
}
