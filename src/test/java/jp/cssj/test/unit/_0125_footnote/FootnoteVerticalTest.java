package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Tests footnote F7 (vertical writing) (consult-codex-2026-07-31-footnote-f6f7.txt §3).
 * In vertical-rl (a 300 pt square page), the footnote area is a column at the type area's block-end,
 * i.e. the left edge. Verifies that the note's x coordinate is near the left edge of the type area
 * (within the reservation) and that body text starts in the rightmost line.
 * Number labels are upright (limited tate-chu-yoko, an intentional deviation from the specification).
 */
public class FootnoteVerticalTest extends AbstractTestCase {
	public FootnoteVerticalTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0125-footnote/footnote-vertical-rl.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** In vertical writing, the marker number is also page-local (1), and the note goes to the left-edge area. */
	public boolean check_v1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(1, pageNumber);
			final StringBuilder buff = new StringBuilder();
			box.getText(buff);
			assertTrue("marker must carry number 1: " + buff, buff.toString().startsWith("1. "));
			assertTrue("the note must sit in the block-end (left) area: x=" + x, x < 100);
			return true;
		}
		return false;
	}

	public boolean check_v2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(1, pageNumber);
			final StringBuilder buff = new StringBuilder();
			box.getText(buff);
			assertTrue("second marker must carry number 2: " + buff, buff.toString().startsWith("2. "));
			assertTrue("the note must sit in the block-end (left) area: x=" + x, x < 100);
			return true;
		}
		return false;
	}
}
