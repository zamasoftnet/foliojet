package jp.cssj.test.unit._0510_flex;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * A {@code column-reverse} flex with an indefinite main size breaks across pages in its visual order (2026-10-08,
 * cfp-6 of copperpdf4/docs/design/column-flex-indefinite-main-design.md): on 200pt pages, items of 80pt plus a 1pt
 * border come p4, p3, p2 on page 1, p2 straddling the break, and p1 at 46pt on page 2, as Chrome prints it. The
 * streamed flow stacked them in source order.
 */
public class FlexColumnReversePagesTest extends AbstractTestCase {
	public FlexColumnReversePagesTest(String name) {
		super(name);
	}

	private final Map<String, double[]> at = new HashMap<>();

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/column-reverse-pages.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals("p4 の頁", 1, (int) this.at.get("p4")[2]);
		assertEquals("p3 の頁", 1, (int) this.at.get("p3")[2]);
		assertEquals("p2 の頁(頭)", 1, (int) this.at.get("p2")[2]);
		assertEquals("p1 の頁", 2, (int) this.at.get("p1")[2]);
		assertEquals(0, this.at.get("p4")[1], 0.1);
		assertEquals(82, this.at.get("p3")[1], 0.1);
		assertEquals(164, this.at.get("p2")[1], 0.1);
		assertEquals(46, this.at.get("p1")[1], 0.1);
	}

	private boolean record(final String id, final IBox box, final int pageNumber, final double x, final double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.at.putIfAbsent(id, new double[] { x, y, pageNumber });
			return true;
		}
		return false;
	}

	public boolean check_p1(IBox box, int pageNumber, double x, double y) {
		return this.record("p1", box, pageNumber, x, y);
	}

	public boolean check_p2(IBox box, int pageNumber, double x, double y) {
		return this.record("p2", box, pageNumber, x, y);
	}

	public boolean check_p3(IBox box, int pageNumber, double x, double y) {
		return this.record("p3", box, pageNumber, x, y);
	}

	public boolean check_p4(IBox box, int pageNumber, double x, double y) {
		return this.record("p4", box, pageNumber, x, y);
	}
}
