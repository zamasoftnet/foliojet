package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Tests footnote F4 (detecting call movement)
 * (consult-codex-2026-07-31-footnote-f4.txt validation fixture 2).
 * The page-break-inside:avoid block fits in the remaining space (75.9 pt) before footnote reservation,
 * but not after reservation (≈21 pt), so it moves as a whole to page 2. Traversing the finalized tree
 * detects that the call no longer remains on page 1, and the note also moves to page 2
 * (the reservation on page 1 is not released: conservative reservation).
 */
public class FootnoteAvoidMoveTest extends AbstractTestCase {
	public FootnoteAvoidMoveTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0125-footnote/footnote-avoidmove.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** The note moves to page 2 with the call (never orphaned on a page without its call). */
	public boolean check_mv(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals("the note must follow its call to page 2", 2, pageNumber);
			return true;
		}
		return false;
	}
}
