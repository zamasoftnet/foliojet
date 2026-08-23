package net.zamasoft.foliojet.layout.text.spacing;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.TextAutospaceValue;
import net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClasses.Kind;

/**
 * {@link TextAutospaceClasses}(和文詰めA2)の純粋計算テストです。
 */
public class TextAutospaceClassesTest extends TestCase {

	private static final byte BOTH = (byte) (TextAutospaceValue.ALPHA | TextAutospaceValue.NUMERIC);

	public void testClassification() {
		assertEquals(Kind.IDEOGRAPH, TextAutospaceClasses.of('漢'));
		assertEquals(Kind.IDEOGRAPH, TextAutospaceClasses.of('あ'));
		assertEquals(Kind.IDEOGRAPH, TextAutospaceClasses.of('ア'));
		assertEquals(Kind.IDEOGRAPH, TextAutospaceClasses.of('々'));
		assertEquals(Kind.IDEOGRAPH, TextAutospaceClasses.of(0x20B9F)); // 𠮟(追加面)
		assertEquals(Kind.ALPHA, TextAutospaceClasses.of('A'));
		assertEquals(Kind.ALPHA, TextAutospaceClasses.of('z'));
		assertEquals(Kind.ALPHA, TextAutospaceClasses.of('é'));
		assertEquals(Kind.NUMERIC, TextAutospaceClasses.of('7'));
		assertEquals(Kind.OTHER, TextAutospaceClasses.of('Ａ')); // 全角英字は対象外
		assertEquals(Kind.OTHER, TextAutospaceClasses.of('。')); // 約物は対象外
		assertEquals(Kind.OTHER, TextAutospaceClasses.of(' '));
	}

	/** 漢A・A漢・漢1・1漢の4方向。flagsによる選別。 */
	public void testGap() {
		assertEquals(0.25, TextAutospaceClasses.gapEm('漢', 'A', BOTH), 0.0001);
		assertEquals(0.25, TextAutospaceClasses.gapEm('A', '漢', BOTH), 0.0001);
		assertEquals(0.25, TextAutospaceClasses.gapEm('漢', '1', BOTH), 0.0001);
		assertEquals(0.25, TextAutospaceClasses.gapEm('1', '漢', BOTH), 0.0001);
		assertEquals(0.25, TextAutospaceClasses.gapEm('あ', 'A', BOTH), 0.0001);

		assertEquals(0.0, TextAutospaceClasses.gapEm('漢', 'A', TextAutospaceValue.NUMERIC), 0.0001);
		assertEquals(0.0, TextAutospaceClasses.gapEm('漢', '1', TextAutospaceValue.ALPHA), 0.0001);
		assertEquals(0.25, TextAutospaceClasses.gapEm('漢', '1', TextAutospaceValue.NUMERIC), 0.0001);
		assertEquals(0.0, TextAutospaceClasses.gapEm('漢', 'A', (byte) 0), 0.0001);
	}

	/** 非対象pair: 和字同士・欧字同士・約物/空白との境界・A1(欧字と数字)。 */
	public void testNoGap() {
		assertEquals(0.0, TextAutospaceClasses.gapEm('漢', 'あ', BOTH), 0.0001);
		assertEquals(0.0, TextAutospaceClasses.gapEm('A', 'B', BOTH), 0.0001);
		assertEquals(0.0, TextAutospaceClasses.gapEm('A', '1', BOTH), 0.0001);
		assertEquals(0.0, TextAutospaceClasses.gapEm('漢', '。', BOTH), 0.0001);
		assertEquals(0.0, TextAutospaceClasses.gapEm('。', 'A', BOTH), 0.0001);
		assertEquals(0.0, TextAutospaceClasses.gapEm('漢', 'Ａ', BOTH), 0.0001);
	}

	public void testIdeographFirst() {
		assertTrue(TextAutospaceClasses.ideographFirst('漢'));
		assertFalse(TextAutospaceClasses.ideographFirst('A'));
	}
}
