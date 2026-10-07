package jp.cssj.test.unit._0390_writing_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Geometry of a block ({@code div#a}) and an inline ({@code span#b}) spanning pages in vertical writing.
 *
 * <p>
 * 2026-07-25: Ruby became annotated text (specification decision in the development record).
 * Lines containing ruby no longer increased the line pitch, compacting the whole document and making
 * the expected values obsolete. To preserve the purpose of testing {@code span#b} across a page boundary,
 * two lines of body text were added just before {@code span#b} in the fixture, then the baseline was
 * updated. A ruby unit (曲者/くせもの) sits just before the split, also ensuring that a split paragraph
 * does not resume in the middle of a ruby unit (it resumes at the unit's source end).
 * </p>
 */
public class FlowInlinePagebreakTest extends AbstractTestCase {
	public FlowInlinePagebreakTest(String name) {
		super(name);
	}

	/** Order of appearance of {@code span#b} fragments (document order). */
	private int bFragment = 0;

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0390-writing-mode/flow-inline-pagebreak.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			if (pageNumber == 1) {
				// The rest of page 1 (at the left edge, since lines progress right to left).
				assertEquals(0, x, 0);
				assertEquals(6, y, 0);
				assertEquals(67, box.getWidth(), 1);
			} else if (pageNumber == 2) {
				// All of page 2.
				assertEquals(0, x, 0);
				assertEquals(6, y, 0);
				assertEquals(243, box.getWidth(), 1);
			} else if (pageNumber == 3) {
				// The start of page 3 (at the right edge).
				assertEquals(183.31, x, 1);
				assertEquals(6, y, 1);
				assertEquals(60, box.getWidth(), 1);
			}
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.INLINE) {
			return false;
		}
		++this.bFragment;
		assertEquals(12, box.getWidth(), 0);
		switch (this.bFragment) {
		case 1:
			// Starts at a line partway through page 2.
			assertEquals(2, pageNumber);
			assertEquals(12, x, 1);
			assertEquals(76.06, y, 1);
			break;
		case 2:
			// The last line of page 2 (contains the ruby unit and splits at the line end).
			assertEquals(2, pageNumber);
			assertEquals(2, x, 1);
			assertEquals(16, y, 1);
			break;
		case 3:
			// Continues onto the first line of page 3.
			assertEquals(3, pageNumber);
			assertEquals(232, x, 1);
			assertEquals(16, y, 1);
			break;
		default:
			fail("span#bの断片が想定より多い: " + this.bFragment);
		}
		return this.bFragment >= 3;
	}
}
