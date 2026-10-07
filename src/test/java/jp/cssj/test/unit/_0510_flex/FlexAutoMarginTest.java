package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Tests auto margins (Flex F3e, §8.1). Main axis: margin-left:auto consumes all 120 pt of free space,
 * so justify-content:center has no effect (a=+0, b=+160).
 * Cross axis: for a 40 pt line height, top+bottom auto centers (+10);
 * top auto alone aligns to the end (+20).
 */
public class FlexAutoMarginTest extends AbstractTestCase {
	public FlexAutoMarginTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, base2Y = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/auto-margin.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** Auto margin consumes free space, so justification has no effect and a is at the line start. */
	public boolean check_a1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			return true;
		}
		return false;
	}

	/** margin-left:auto receives all 120 pt (+160). */
	public boolean check_a2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 160, x, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_tallm(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.base2Y = y;
			return true;
		}
		return false;
	}

	/** top+bottom auto = centered within the line (+10). */
	public boolean check_cm(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.base2Y + 10, y, 0.1);
			return true;
		}
		return false;
	}

	/** top auto alone = end-aligned (+20). */
	public boolean check_em(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.base2Y + 20, y, 0.1);
			return true;
		}
		return false;
	}
}
