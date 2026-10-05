package jp.cssj.test.unit.displaylist;

import junit.framework.TestCase;

/** {@link TwoPassDigestParityTest} の 3 番の受け持ち(2026-10-05、分割の説明はそちら)。 */
public final class TwoPassDigestParityShard3Test extends TestCase {
	public void testDigestParity() throws Exception {
		TwoPassDigestParityTest.checkShard(3);
	}
}
