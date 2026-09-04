package net.zamasoft.foliojet.layout.text.bidi;

import java.text.Bidi;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.UnicodeBidiValue;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * 段落単位の双方向解決の model({@link BidiParagraphBuffer}/{@link BidiResolver})の
 * 試験(2026-09-04、bidi-isolation-design.md batch A-1a)。まだレイアウトには
 * 配線されていない(flag {@code layout.bidi.paragraph} は既定 OFF)。
 */
public class BidiParagraphBufferTest extends TestCase {
	private static final String HEB = "אבג"; // אבג

	public void testControlsPerUnicodeBidiValue() {
		final byte ltr = AbstractTextParams.DIRECTION_LTR, rtl = AbstractTextParams.DIRECTION_RTL;
		assertEquals("", BidiResolver.openingControls(rtl, UnicodeBidiValue.NORMAL));
		assertEquals("", BidiResolver.closingControls(UnicodeBidiValue.NORMAL));
		assertEquals("\u202B", BidiResolver.openingControls(rtl, UnicodeBidiValue.EMBED));
		assertEquals("\u202A", BidiResolver.openingControls(ltr, UnicodeBidiValue.EMBED));
		assertEquals("\u202C", BidiResolver.closingControls(UnicodeBidiValue.EMBED));
		assertEquals("\u202E", BidiResolver.openingControls(rtl, UnicodeBidiValue.BIDI_OVERRIDE));
		assertEquals("\u202D", BidiResolver.openingControls(ltr, UnicodeBidiValue.BIDI_OVERRIDE));
		assertEquals("\u202C", BidiResolver.closingControls(UnicodeBidiValue.BIDI_OVERRIDE));
		assertEquals("\u2067", BidiResolver.openingControls(rtl, UnicodeBidiValue.ISOLATE));
		assertEquals("\u2066", BidiResolver.openingControls(ltr, UnicodeBidiValue.ISOLATE));
		assertEquals("\u2069", BidiResolver.closingControls(UnicodeBidiValue.ISOLATE));
		assertEquals("\u2068\u202E", BidiResolver.openingControls(rtl, UnicodeBidiValue.ISOLATE_OVERRIDE));
		assertEquals("\u202C\u2069", BidiResolver.closingControls(UnicodeBidiValue.ISOLATE_OVERRIDE));
		assertEquals("\u2068", BidiResolver.openingControls(ltr, UnicodeBidiValue.PLAINTEXT));
		assertEquals("\u2069", BidiResolver.closingControls(UnicodeBidiValue.PLAINTEXT));
		assertTrue(BidiResolver.isControl('\u2066'));
		assertFalse(BidiResolver.isControl('\uFFFC'));
	}

	public void testBaseDirection() {
		assertEquals(Bidi.DIRECTION_LEFT_TO_RIGHT,
				BidiResolver.baseDirectionFlag(AbstractTextParams.DIRECTION_LTR, UnicodeBidiValue.NORMAL));
		assertEquals(Bidi.DIRECTION_RIGHT_TO_LEFT,
				BidiResolver.baseDirectionFlag(AbstractTextParams.DIRECTION_RTL, UnicodeBidiValue.EMBED));
		assertEquals(Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
				BidiResolver.baseDirectionFlag(AbstractTextParams.DIRECTION_LTR, UnicodeBidiValue.PLAINTEXT));
		// plaintext のブロック: 先頭の強い文字で段落レベルが決まる
		final BidiParagraphBuffer heb = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.PLAINTEXT);
		heb.addText(HEB + " 123", null);
		assertEquals(1, heb.paragraphLevel());
		final BidiParagraphBuffer lat = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.PLAINTEXT);
		lat.addText("abc " + HEB, null);
		assertEquals(0, lat.paragraphLevel());
	}

	/** RTL 段落の `אבג ABC`: Hebrew はレベル 1、Latin はレベル 2。段落レベルは 1。 */
	public void testLevelsInRtlParagraph() {
		final BidiParagraphBuffer buffer = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_RTL,
				UnicodeBidiValue.NORMAL);
		final BidiParagraphBuffer.Event heb = buffer.addText(HEB, null);
		buffer.addText(" ", null);
		final BidiParagraphBuffer.Event lat = buffer.addText("ABC", null);
		assertEquals(1, buffer.paragraphLevel());
		assertTrue(buffer.isMixed());
		for (int i = heb.start(); i < heb.limit(); ++i) {
			assertEquals(1, buffer.levelAt(i));
		}
		for (int i = lat.start(); i < lat.limit(); ++i) {
			assertEquals(2, buffer.levelAt(i));
		}
	}

	/** 純 LTR の段落は並べ替え不要。 */
	public void testPureLtrIsNotMixed() {
		final BidiParagraphBuffer buffer = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.NORMAL);
		buffer.addText("abc ", null);
		buffer.inlineStart(AbstractTextParams.DIRECTION_LTR, UnicodeBidiValue.NORMAL, "span");
		buffer.addText("def", null);
		buffer.inlineEnd("span");
		assertFalse(buffer.isMixed());
		assertEquals("abc def", buffer.synthetic());
	}

	/**
	 * LTR 段落の `A <rtl isolate>אב 12</> - B`: isolate の中の数字はレベル 2、
	 * 外の ` - B` は基準レベル 0 のまま(isolate は周囲へ影響しない)。
	 */
	public void testIsolateDoesNotLeak() {
		final BidiParagraphBuffer buffer = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.NORMAL);
		buffer.addText("A ", null);
		buffer.inlineStart(AbstractTextParams.DIRECTION_RTL, UnicodeBidiValue.ISOLATE, "span");
		final BidiParagraphBuffer.Event heb = buffer.addText("אב", null);
		buffer.addText(" ", null);
		final BidiParagraphBuffer.Event digits = buffer.addText("12", null);
		buffer.inlineEnd("span");
		final BidiParagraphBuffer.Event tail = buffer.addText(" - B", null);
		assertEquals("A \u2067אב 12\u2069 - B", buffer.synthetic());
		assertEquals(1, buffer.levelAt(heb.start()));
		assertEquals(2, buffer.levelAt(digits.start()));
		for (int i = tail.start(); i < tail.limit(); ++i) {
			assertEquals("index " + i, 0, buffer.levelAt(i));
		}
	}

	/** embed なら外側の中立文字(` - `)が RTL 側へ引き込まれ得る——isolate との差。 */
	public void testEmbedLeaksIntoNeighbours() {
		final BidiParagraphBuffer buffer = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.NORMAL);
		buffer.addText("A ", null);
		buffer.inlineStart(AbstractTextParams.DIRECTION_RTL, UnicodeBidiValue.EMBED, "span");
		buffer.addText("אב", null);
		buffer.inlineEnd("span");
		final BidiParagraphBuffer.Event tail = buffer.addText(" ג", null);
		assertEquals("A \u202Bאב\u202C ג", buffer.synthetic());
		// embed の外の空白は両側が R なので R(レベル 1)になる
		assertEquals(1, buffer.levelAt(tail.start()));
	}

	/** atomic inline は U+FFFC 1 個で、周囲の方向に従う中立オブジェクト。 */
	public void testAtomicInline() {
		final BidiParagraphBuffer buffer = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_RTL,
				UnicodeBidiValue.NORMAL);
		buffer.addText("אב ", null);
		final BidiParagraphBuffer.Event img = buffer.atomic("img");
		buffer.addText(" גד", null);
		assertEquals(1, img.length());
		assertEquals('\uFFFC', buffer.synthetic().charAt(img.start()));
		assertEquals(1, buffer.levelAt(img.start()));
		assertEquals(BidiParagraphBuffer.Kind.ATOMIC, img.kind());
	}

	/** 行ごとの Bidi(L1): 行末の空白は段落レベルへ落ちる。 */
	public void testLineBidiTrailingWhitespace() {
		final BidiParagraphBuffer buffer = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.NORMAL);
		// 1 行目 "אבג " / 2 行目 "אבג"(RTL の語のあとの空白で折り返す想定)
		buffer.addText(HEB + " " + HEB, null);
		final Bidi line = buffer.lineBidi(0, 4);
		assertEquals(4, line.getLength());
		assertEquals(1, line.getLevelAt(0));
		assertEquals("行末の空白は段落レベル(0)", 0, line.getLevelAt(3));
		assertEquals(0, line.getBaseLevel());
	}

	public void testParagraphBreakAndBarrierBookkeeping() {
		final BidiParagraphBuffer buffer = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.NORMAL);
		buffer.addText("abc", null);
		final BidiParagraphBuffer.Event barrier = buffer.barrier("float");
		final BidiParagraphBuffer.Event br = buffer.paragraphBreak("br");
		assertEquals(0, barrier.length());
		assertEquals(3, barrier.start());
		assertEquals('\u2029', buffer.synthetic().charAt(br.start()));
		assertEquals(3, buffer.events().size());
		try {
			buffer.inlineEnd("x");
			fail("inlineEnd without start must fail");
		} catch (IllegalStateException e) {
			// expected
		}
	}
}
