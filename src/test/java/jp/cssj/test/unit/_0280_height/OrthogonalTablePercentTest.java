package jp.cssj.test.unit._0280_height;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Regression test for <b>falling back to the fragmentainer as the percentage basis for an orthogonal
 * flow's table</b> (2026-09-16).
 *
 * <p>
 * When a horizontal-writing table was placed in vertical-writing body text, {@code getFixedWidth()}
 * (which searches ancestors for explicit dimensions to use as the percentage-width basis) found
 * no match and returned 0. As a result, {@code max-width: 50%} became 0, and <b>content overflowed
 * off the page from a zero-width table</b> ("all rendering outside the page" in the sweep;
 * rendering started at x=200.5 in a document with 200 pt paper).
 * Paper dimensions are definite, so they serve as the final basis, per css-writing-modes-4 §7.3.
 * </p>
 */
public class OrthogonalTablePercentTest extends AbstractTestCase {
	public OrthogonalTablePercentTest(final String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		final File file = new File("files/unittest/0280-height/orthogonal-table-percent.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** The table fits in 50% of the 200 pt paper width = 100 pt, and vertical writing places it from the right edge. */
	public boolean check_t(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() != net.zamasoft.foliojet.layout.box.BoxType.TABLE) {
			// Anonymous block boxes with the same id also arrive here (the table box carries the dimensions).
			return false;
		}
		assertEquals("ページ", 1, pageNumber);
		assertEquals("表の幅は用紙幅の 50%", 100.0, box.getWidth(), 0.5);
		// In vertical writing (vertical-rl), block-start is the right edge. A 100 pt table starts at x=100.
		assertEquals("表の左端", 100.0, x, 0.5);
		assertTrue("表の右端が紙面内", x + box.getWidth() <= 200.5);
		return true;
	}

	/** An auto-width table with `max-width: 50%` also fits within 100 pt because its basis is the paper width. */
	public boolean check_m(final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() != net.zamasoft.foliojet.layout.box.BoxType.TABLE) {
			return false;
		}
		assertTrue("max-width が効いて 100pt 以内(実測 " + box.getWidth() + ")", box.getWidth() <= 100.5);
		assertTrue("表が紙面内(x=" + x + " w=" + box.getWidth() + ")", x >= -0.5 && x + box.getWidth() <= 200.5);
		return true;
	}
}
