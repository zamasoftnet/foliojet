package jp.cssj.test.unit._0330_table_border;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.part.TableCollapsedBorders;

/**
 * The sides the frame attribute leaves out are hidden, and hidden wins the collapsing border conflicts on the edges of
 * the table (2026-10-09; files/unittest/0330-table-border/frame-rules-hidden.html). Expected lines are Chrome 151's:
 * frame=hsides rules=groups draws no left and right lines (it drew the column groups' borders there, pmc.ncbi.nlm.nih.gov),
 * frame=void rules=all and a table with every side hidden keep only the inner lines (the hidden table had lost its frame
 * when it had no margin, padding or background).
 */
public class FrameRulesHiddenTest extends AbstractTestCase {
	public FrameRulesHiddenTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		CTISessionHelper.transcodeFile(this.session, new File("files/unittest/0330-table-border/frame-rules-hidden.html"),
				"text/html", null);
	}

	/** Whether each vertical line (left edge .. right edge) of every row and each horizontal line of every column is drawn. */
	private static void assertLines(final IBox box, final boolean[] vertical, final boolean[] horizontal) {
		final TableCollapsedBorders borders = ((TableBox) box).getCollapsedBorders();
		assertNotNull(borders);
		for (int row = 0; row < 3; ++row) {
			for (int i = 0; i < vertical.length; ++i) {
				assertEquals("row " + row + " vertical line " + i, vertical[i], borders.getVBorder(row, i).width > 0);
			}
		}
		for (int col = 0; col < 3; ++col) {
			for (int i = 0; i < horizontal.length; ++i) {
				assertEquals("column " + col + " horizontal line " + i, horizontal[i],
						borders.getHBorder(col, i).width > 0);
			}
		}
	}

	public boolean check_hsides(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.TABLE) {
			return false;
		}
		assertLines(box, new boolean[] { false, true, false, false }, new boolean[] { true, true, false, true });
		return true;
	}

	public boolean check_void(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.TABLE) {
			return false;
		}
		assertLines(box, new boolean[] { false, true, true, false }, new boolean[] { false, true, true, false });
		return true;
	}

	public boolean check_css(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.TABLE) {
			return false;
		}
		assertLines(box, new boolean[] { false, true, true, false }, new boolean[] { false, true, true, false });
		return true;
	}
}
