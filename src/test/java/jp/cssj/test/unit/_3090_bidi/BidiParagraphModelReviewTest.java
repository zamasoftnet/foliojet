package jp.cssj.test.unit._3090_bidi;

import java.text.Bidi;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.value.UnicodeBidiValue;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.text.bidi.BidiParagraphBuffer;

/** A-1a review §0-12〜§0-16/§0-18 で追加された model 契約。 */
public class BidiParagraphModelReviewTest extends TestCase {
	public void testBreakSnapshotClosesAndReopensInlines() {
		final BidiParagraphBuffer first = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.NORMAL);
		first.inlineStart(AbstractTextParams.DIRECTION_RTL, UnicodeBidiValue.ISOLATE, "outer");
		first.inlineStart(AbstractTextParams.DIRECTION_LTR, UnicodeBidiValue.BIDI_OVERRIDE, "inner");
		first.addText("abc", null);
		final BidiParagraphBuffer.ParagraphBreak br = first.paragraphBreak("br");
		assertEquals("\u2067\u202Dabc\u202C\u2069\u2029", first.synthetic());
		assertEquals(2, br.openInlines().size());
		try {
			first.addText("closed", null);
			fail("paragraphBreak 後の buffer へ追加できてはなりません");
		} catch (IllegalStateException e) {
			// expected
		}
		try {
			br.openInlines().clear();
			fail("snapshot は不変です");
		} catch (UnsupportedOperationException e) {
			// expected
		}
		final BidiParagraphBuffer next = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.NORMAL);
		next.reopen(br.openInlines());
		next.addText("def", null);
		assertEquals("\u2067\u202Ddef", next.synthetic());
	}

	public void testBlockOverrideAndAtomicDirection() {
		final BidiParagraphBuffer root = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_RTL,
				UnicodeBidiValue.BIDI_OVERRIDE);
		final BidiParagraphBuffer.Event text = root.addText("ABC-12", null);
		assertEquals('\u202E', root.synthetic().charAt(0));
		assertTrue(root.isSyntheticControl(0));
		for (int i = text.start(); i < text.limit(); ++i) {
			assertEquals("root override must resolve every character RTL", 1, root.levelAt(i) & 1);
		}

		final BidiParagraphBuffer neutral = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.NORMAL);
		final BidiParagraphBuffer.Event object = neutral.atomic("img", AbstractTextParams.DIRECTION_RTL,
				UnicodeBidiValue.NORMAL);
		assertEquals('\uFFFC', neutral.synthetic().charAt(object.start()));
		final BidiParagraphBuffer strong = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.NORMAL);
		final BidiParagraphBuffer.Event rtl = strong.atomic("img", AbstractTextParams.DIRECTION_RTL,
				UnicodeBidiValue.EMBED);
		assertEquals('\u200F', strong.synthetic().charAt(rtl.start()));
		assertEquals(1, strong.levelAt(rtl.start()));
	}

	public void testSourceControlsAndPureRtl() {
		final BidiParagraphBuffer source = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_LTR,
				UnicodeBidiValue.NORMAL);
		final BidiParagraphBuffer.Event text = source.addText("A\u202B\u05D0\u202CB", null);
		assertFalse(source.isSyntheticControl(text.start() + 1));
		assertFalse(source.isSyntheticControl(text.start() + 3));
		source.inlineStart(AbstractTextParams.DIRECTION_RTL, UnicodeBidiValue.EMBED, "span");
		assertTrue(source.isSyntheticControl(text.limit()));

		final BidiParagraphBuffer rtl = new BidiParagraphBuffer(AbstractTextParams.DIRECTION_RTL,
				UnicodeBidiValue.NORMAL);
		rtl.addText("\u05D0\u05D1\u05D2", null);
		assertTrue(rtl.resolve().isRightToLeft());
		assertEquals(Bidi.DIRECTION_RIGHT_TO_LEFT,
				net.zamasoft.foliojet.layout.text.bidi.BidiResolver.baseDirectionFlag(
						AbstractTextParams.DIRECTION_RTL, UnicodeBidiValue.NORMAL));
		assertTrue(rtl.requiresVisualReordering());
	}
}
