package jp.cssj.test.unit._0415_column_fill;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Verifies the column fragments around a float (image) in a two-column layout.
 *
 * <p>
 * On 2026-09-11, correcting the built-in CID-keyed font width table restored full widths for the
 * fullwidth space, Japanese comma, and Japanese period, changing this document's layout.
 * <b>Previously, the float split across columns</b>: the top 93.60 pt of the image (85.50×114.96)
 * was drawn in the left column, and the remaining 21.36 pt at the start of the right column.
 * Only those two lines of body text moved aside to x=286.50. Correct advances let the image fit in
 * the left column, and body text now wraps to its right.
 * </p>
 *
 * <p>
 * Column fragments represent box divisions, not breaks in rendering. The right column of #b splits
 * into two boxes, but they continue without a gap at 126.56+57.6 = 184.16,
 * laying out six lines (86.4 pt) continuously.
 * </p>
 */
public class FloatInFlowTest extends AbstractTestCase {
	public FloatInFlowTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0415-column-fill/float-in-flow.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** Column fragments of #a. Balanced, with four lines on each side. {x, y, width, height} */
	private static final double[][] A = { //
			{ 6, 6, 171, 57.6 }, //
			{ 201, 6, 171, 57.6 }, //
	};

	/**
	 * Column fragments of #b. The left column has eight lines to the right of the float
	 * (height 114.96, matching the float). The right column has six lines split into two boxes,
	 * 57.6 and 28.8, continuing without a gap.
	 */
	private static final double[][] B = { //
			{ 6, 126.56, 171, 114.96 }, //
			{ 201, 126.56, 171, 57.6 }, //
			{ 201, 184.16, 171, 28.8 }, //
	};

	private int ia = 0;

	private int ib = 0;

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertTrue("#a の断片が想定より多い: " + this.ia, this.ia < A.length);
			check("#a", this.ia, A[this.ia], box, x, y);
			this.ia++;
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertTrue("#b の断片が想定より多い: " + this.ib, this.ib < B.length);
			check("#b", this.ib, B[this.ib], box, x, y);
			this.ib++;
			return true;
		}
		return false;
	}

	private static void check(final String id, final int index, final double[] expected, final IBox box,
			final double x, final double y) {
		final String at = id + " 断片" + index;
		assertEquals(at + " x", expected[0], x, 1);
		assertEquals(at + " y", expected[1], y, 1);
		assertEquals(at + " 幅", expected[2], box.getWidth(), 1);
		assertEquals(at + " 高さ", expected[3], box.getHeight(), 1);
	}
}
