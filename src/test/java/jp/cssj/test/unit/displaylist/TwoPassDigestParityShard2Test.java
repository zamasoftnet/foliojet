package jp.cssj.test.unit.displaylist;

import junit.framework.TestCase;

/** Shard 2 of {@link TwoPassDigestParityTest} (2026-10-05; see that class for the sharding rationale). */
public final class TwoPassDigestParityShard2Test extends TestCase {
	public void testDigestParity() throws Exception {
		TwoPassDigestParityTest.checkShard(2);
	}
}
