package com.playmonumenta.redissync;

import java.util.concurrent.ExecutionException;

/**
 * NOT FOR PRODUCTION USE. Validates that static analysis rules correctly detect
 * misuse of RedisAPI.borrow(). Each method intentionally violates exactly one rule.
 *
 * After building, confirm the expected violations appear in the output:
 *   testBorrowWithoutTryWithResources — error-prone MustBeClosed
 *   testJoinInsideBorrow              — PMD NoBorrowJoinOrGet
 *   testGetInsideBorrow               — PMD NoBorrowJoinOrGet
 *   testMultiInsideBorrow             — PMD NoMultiInsideBorrow (multi + exec)
 */
public class RedisUsageTest {

	// Expected: error-prone MustBeClosed.
	// borrow() is @MustBeClosed; its result must be used in a try-with-resources.
	// The lock is never released here, causing all future Redis calls to deadlock.
	public static void testBorrowWithoutTryWithResources() {
		RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow();
		conn.hset("key", "field", "value");
		// Missing try-with-resources — lock is never released
	}

	// Expected: PMD NoBorrowJoinOrGet.
	// .join() on a CompletableFuture inside a borrow() block holds the connection
	// lock for the full network round-trip, blocking all other Redis calls.
	public static void testJoinInsideBorrow() {
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			conn.hget("key", "field").toCompletableFuture().join();
		}
	}

	// Expected: PMD NoBorrowJoinOrGet.
	// .get() on a RedisFuture inside a borrow() block holds the connection lock
	// for the full network round-trip, blocking all other Redis calls.
	public static void testGetInsideBorrow() throws ExecutionException, InterruptedException {
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			conn.hget("key", "field").get();
		}
	}

	// Expected: PMD NoMultiInsideBorrow (two violations: multi and exec).
	// Calling .multi()/.exec() directly on BorrowedCommands risks leaving a
	// transaction open if an exception is thrown. Use RedisAPI.multi() instead.
	public static void testMultiInsideBorrow() {
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			conn.multi();
			conn.hset("key", "field", "value");
			conn.exec();
		}
	}
}
