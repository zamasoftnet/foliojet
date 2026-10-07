package net.zamasoft.foliojet.layout.text.bidi;

import java.text.Bidi;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.UnicodeBidiValue;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * Tests for the paragraph-level bidirectional resolution model
 * ({@link BidiParagraphBuffer}/{@link BidiResolver})
 * (2026-09-04, bidi-isolation-design.md batch A-1a).
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
	}

	public void testBaseDirection() {
		assertEquals(Bidi.DIRECTION_LEFT_TO_RIGHT,
				BidiResolver.baseDirectionFlag(AbstractTextParams.DIRECTION_LTR, UnicodeBidiValue.NORMAL));
		assertEquals(Bidi.DIRECTION_RIGHT_TO_LEFT,
				BidiResolver.baseDirectionFlag(AbstractTextParams.DIRECTION_RTL, UnicodeBidiValue.EMBED));
		assertEquals(Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
				BidiResolver.baseDirectionFlag(AbstractTextParams.DIRECTION_LTR, UnicodeBidiValue.PLAINTEXT));
		// A plaintext block: the first strong character determines the paragraph level.
		final BidiParagraphBuffer heb = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.PLAINTEXT);
		heb.addText(HEB + " 123", null);
		assertEquals(1, heb.paragraphLevel());
		final BidiParagraphBuffer lat = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.PLAINTEXT);
		lat.addText("abc " + HEB, null);
		assertEquals(0, lat.paragraphLevel());
	}

	/** `אבג ABC` in an RTL paragraph: Hebrew is level 1, Latin is level 2. The paragraph level is 1. */
	public void testLevelsInRtlParagraph() {
		final BidiParagraphBuffer buffer = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_RTL,
				UnicodeBidiValue.NORMAL);
		final BidiParagraphBuffer.Event heb = buffer.addText(HEB, null);
		buffer.addText(" ", null);
		final BidiParagraphBuffer.Event lat = buffer.addText("ABC", null);
		assertEquals(1, buffer.paragraphLevel());
		assertTrue(buffer.requiresVisualReordering());
		for (int i = heb.start(); i < heb.limit(); ++i) {
			assertEquals(1, buffer.levelAt(i));
		}
		for (int i = lat.start(); i < lat.limit(); ++i) {
			assertEquals(2, buffer.levelAt(i));
		}
	}

	/** A purely LTR paragraph needs no reordering. */
	public void testPureLtrIsNotMixed() {
		final BidiParagraphBuffer buffer = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.NORMAL);
		buffer.addText("abc ", null);
		buffer.inlineStart(AbstractTextParams.DIRECTION_LTR, UnicodeBidiValue.NORMAL, "span");
		buffer.addText("def", null);
		buffer.inlineEnd("span");
		assertFalse(buffer.requiresVisualReordering());
		assertEquals("abc def", buffer.synthetic());
	}

	/**
	 * `A <rtl isolate>אב 12</> - B` in an LTR paragraph: digits inside the isolate are level 2;
	 * the external ` - B` remains at base level 0 (the isolate does not affect its surroundings).
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

	/** With embed, external neutral characters (` - `) can be pulled toward RTL; this differs from isolate. */
	public void testEmbedLeaksIntoNeighbours() {
		final BidiParagraphBuffer buffer = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.NORMAL);
		buffer.addText("A ", null);
		buffer.inlineStart(AbstractTextParams.DIRECTION_RTL, UnicodeBidiValue.EMBED, "span");
		buffer.addText("אב", null);
		buffer.inlineEnd("span");
		final BidiParagraphBuffer.Event tail = buffer.addText(" ג", null);
		assertEquals("A \u202Bאב\u202C ג", buffer.synthetic());
		// The whitespace outside the embed becomes R (level 1) because both sides are R.
		assertEquals(1, buffer.levelAt(tail.start()));
	}

	/** An atomic inline is one U+FFFC, a neutral object that follows the surrounding direction. */
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

	/** Per-line Bidi (L1): trailing whitespace falls back to the paragraph level. */
	public void testLineBidiTrailingWhitespace() {
		final BidiParagraphBuffer buffer = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.NORMAL);
		// Line 1 "אבג " / line 2 "אבג" (assuming a wrap at whitespace after an RTL word).
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
