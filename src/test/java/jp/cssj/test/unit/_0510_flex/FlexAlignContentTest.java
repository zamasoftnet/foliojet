package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * Tests align-content (definite cross size) (Flex F3d, §9.6).
 * A 100 pt-high wrap container with two 20 pt rows has 60 pt free:
 * center puts row 1 at +30; space-between puts row 2 at +80.
 * For a single line (nowrap) with definite cross size, line height equals the container's inner
 * cross size (§9.4; an auto-height item stretches to 60 pt).
 */
public class FlexAlignContentTest extends AbstractTestCase {
	public FlexAlignContentTest(String name) {
		super(name);
	}

	private double m1Y = Double.NaN, m2Y = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/align-content.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_m1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.m1Y = y;
			return true;
		}
		return false;
	}

	/** center: row 1 = container top +30. */
	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.m1Y + 40, y, 0.1);
			return true;
		}
		return false;
	}

	/** center: row 2 = +50. */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.m1Y + 60, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_m2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.m2Y = y;
			assertEquals(this.m1Y + 110, y, 0.1);
			return true;
		}
		return false;
	}

	/** space-between: row 1 = +0. */
	public boolean check_t(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.m2Y + 10, y, 0.1);
			return true;
		}
		return false;
	}

	/** space-between: row 2 = +80. */
	public boolean check_v(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.m2Y + 90, y, 0.1);
			return true;
		}
		return false;
	}

	/** §9.4: In a single line with definite cross size, auto-height items stretch to the container's inner cross size. */
	public boolean check_card2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(60.0, box.getPageExtent(WritingMode.TB), 0.1);
			return true;
		}
		return false;
	}
}
