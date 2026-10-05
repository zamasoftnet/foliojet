package jp.cssj.test.unit.displaylist;

import junit.framework.TestCase;

/** {@link TwoPassDigestParityTest} の 2 番の受け持ち(2026-10-05、分割の説明はそちら)。 */
public final class TwoPassDigestParityShard2Test extends TestCase {
	public void testDigestParity() throws Exception {
		TwoPassDigestParityTest.checkShard(2);
	}
}
