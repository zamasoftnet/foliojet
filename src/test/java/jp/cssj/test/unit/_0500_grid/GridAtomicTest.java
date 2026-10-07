package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G0 atomic contract test (consult-codex-2026-07-31-grid.txt §1.2).
 * A Grid container that does not fit in the remaining page space moves whole to page 2 without internal splitting.
 *
 * <p>
 * Since 2026-08-10 (G6 row splitting), this contract remains only for configurations without
 * row-boundary bookkeeping (rowSpan&gt;1, etc.). The fixture was replaced with one containing rowSpan
 * to serve as a regression test for this gate. Splitting behavior for configurations with bookkeeping
 * is covered by the row-split-carry / row-split-force / min-height-slack goldens.
 * </p>
 */
public class GridAtomicTest extends AbstractTestCase {
	public GridAtomicTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/atomic-move-rowspan.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** Grid moves whole to the next page without splitting (PageAtomicBox). */
	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals("the grid must move to page 2 as a whole", 2, pageNumber);
			final StringBuilder buff = new StringBuilder();
			box.getText(buff);
			final String text = buff.toString();
			assertTrue("all items must be present: " + text, text.contains("alpha") && text.contains("delta"));
			return true;
		}
		return false;
	}
}
