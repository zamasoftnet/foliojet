package jp.cssj.test.unit.builder;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.builder.impl.TextEventJournal;

/**
 * Tests the boundary event journal (M3b Phase 0). Verifies at the type level the divergence between
 * deliveredCharEnd (advances only on glyphs) and the normalized event cursor (includes controls),
 * which is why the former cannot serve as the join key for an open paragraph.
 */
public class TextEventJournalTest extends TestCase {
	public void testCursorAdvancesOnTrailingControls() {
		final TextEventJournal j = new TextEventJournal();
		j.run(0);
		j.glyph(0, 3); // 「abc」
		// The deliveredCharEnd equivalent is 3 here.
		j.control(3); // Trailing whitespace.
		j.control(4); // Line break.
		// The normalized event delivery boundary advances to 5, including controls.
		assertEquals(5, j.cursor());
		assertEquals(4, j.seq());
	}

	public void testNegativeOffsetsDoNotAdvance() {
		final TextEventJournal j = new TextEventJournal();
		j.glyph(-1, 2); // Generated glyph (no source position).
		j.control(-1); // Generated control.
		assertEquals(0, j.cursor());
		assertEquals(2, j.seq());
	}

	public void testSeqCountsEveryDeliveredEvent() {
		final TextEventJournal j = new TextEventJournal();
		j.run(0);
		j.glyph(0, 1);
		j.inline();
		j.glyph(1, 2);
		j.flush();
		assertEquals(5, j.seq());
		assertEquals(2, j.cursor());
	}
}
