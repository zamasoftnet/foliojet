package net.zamasoft.foliojet.layout.text.spacing;

import junit.framework.TestCase;

/**
 * {@link JapaneseSpacingResolver#endAllowance}(和文詰めT2/H1)の
 * 決定的テストです。コーパスのフォントは約物が半角advance(wide gateが
 * 正しく除外)のため、実文書での発火は環境依存——ここが正本の検証。
 */
public class EndAllowanceTest extends TestCase {

	/** 行末trim(T2): 超過≤0.5emなら半角化で追い込む。 */
	public void testEndTrim() {
		// 閉じ括弧・超過3pt≤6pt → trim 6pt
		assertEquals(6.0, JapaneseSpacingResolver.endAllowance('」', true, false, false, 12, 12, 3), 0.001);
		// 句読点も対象
		assertEquals(6.0, JapaneseSpacingResolver.endAllowance('。', true, false, false, 12, 12, 6), 0.001);
		// 超過>0.5emはtrim不成立(hangも無効なら0=追い出し)
		assertEquals(0.0, JapaneseSpacingResolver.endAllowance('」', true, false, false, 12, 12, 7), 0.001);
		// space-allはtrim無効
		assertEquals(0.0, JapaneseSpacingResolver.endAllowance('」', true, true, false, 12, 12, 3), 0.001);
	}

	/** ぶら下げ(H1): trim不成立でも句読点はallow-endで全advanceまで。 */
	public void testHang() {
		// 超過7pt: trim(6)不成立→hang(12)成立
		assertEquals(12.0, JapaneseSpacingResolver.endAllowance('、', true, false, true, 12, 12, 7), 0.001);
		// 優先順: 超過≤trimならtrimが勝つ(hangしない)
		assertEquals(6.0, JapaneseSpacingResolver.endAllowance('、', true, false, true, 12, 12, 5), 0.001);
		// 閉じ括弧はhang対象外(句読点のみ)
		assertEquals(0.0, JapaneseSpacingResolver.endAllowance('」', true, false, true, 12, 12, 7), 0.001);
		// 超過>advanceはhangも不成立
		assertEquals(0.0, JapaneseSpacingResolver.endAllowance('、', true, false, true, 12, 12, 13), 0.001);
		// space-allでもhangは可(trimだけが無効)
		assertEquals(12.0, JapaneseSpacingResolver.endAllowance('、', true, true, true, 12, 12, 7), 0.001);
	}

	/** 縦組でも呼び出し側から渡されたvertical advanceをhang量に使う。 */
	public void testVerticalAdvanceForHang() {
		// 0.5em(6pt)では収まらず、vmtx由来10ptなら収まる
		assertEquals(10.0, JapaneseSpacingResolver.endAllowance('、', true, false, true, 10, 12, 8), 0.001);
	}

	/** 対象外: 半角約物・非約物。 */
	public void testExcluded() {
		assertEquals(0.0, JapaneseSpacingResolver.endAllowance('、', false, false, true, 6, 12, 3), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.endAllowance('あ', true, false, true, 12, 12, 3), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.endAllowance('「', true, false, true, 12, 12, 3), 0.001);
	}

	/** trim-bothの無条件行末詰め量。 */
	public void testEndTrimAmount() {
		assertEquals(6.0, JapaneseSpacingResolver.endTrim('」', true, 12), 0.001);
		assertEquals(6.0, JapaneseSpacingResolver.endTrim('。', true, 12), 0.001);
		assertEquals(3.0, JapaneseSpacingResolver.endTrim('・', true, 12), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.endTrim('「', true, 12), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.endTrim('」', false, 12), 0.001);
	}

	/** first/force-endのぶら下げ量。 */
	public void testUnconditionalHangs() {
		assertEquals(-6.0, JapaneseSpacingResolver.firstHang('「', true, 12, 12, true), 0.001);
		assertEquals(-12.0, JapaneseSpacingResolver.firstHang('「', true, 12, 12, false), 0.001);
		assertEquals(-12.0, JapaneseSpacingResolver.firstHang('\u3000', true, 12, 12, true), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.firstHang('あ', true, 12, 12, true), 0.001);
		assertEquals(12.0, JapaneseSpacingResolver.forceEndHang('。', 12), 0.001);
		assertEquals(0.0, JapaneseSpacingResolver.forceEndHang('」', 12), 0.001);
	}
}
