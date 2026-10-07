package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Tests continuation fragment dimensions for grid row splitting (G6, 2026-08-10).
 *
 * <p>
 * A 3-row × 2-column grid (92 pt per row) splits at the 300 pt split line, carrying the lower 56 pt
 * of row 3 to page 2 as a continuation fragment. Continuation fragments are excluded from chain
 * continuation (PageAtomicBox), so they undergo generic restyle reconstruction (re-registering
 * items in a vertical stack). Without RowSplitContainer cursor rewind, a fragment with two 56 pt
 * items expands to 112 pt, pushing down subsequent content. This was a measured defect;
 * extending rewind from self-anchors only to all cases fixed the root cause.
 * </p>
 */
public class GridRowSplitContinuationTest extends AbstractTestCase {
	public GridRowSplitContinuationTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/row-split-carry.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** The preceding fragment reaches the split line; the continuation fragment is exactly the remaining height. */
	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		if (pageNumber == 1) {
			assertEquals("前断片はページ端(y=300)まで", 220.0, box.getHeight(), 0.1);
			return false;
		}
		assertEquals("継続断片が縦積み再構築で膨らんではならない", 2, pageNumber);
		assertEquals("継続断片は3行目の残余ちょうど", 56.0, box.getHeight(), 0.1);
		return true;
	}

	/** The following element continues immediately after the continuation fragment (without being pushed down). */
	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		assertEquals("AFTERはgrid継続断片と同じ2ページ目", 2, pageNumber);
		return true;
	}
}
