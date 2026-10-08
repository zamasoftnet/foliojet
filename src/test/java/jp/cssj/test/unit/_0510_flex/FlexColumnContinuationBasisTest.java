package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * A column flex item broken across pages takes its flex basis only on its first page (codex review 2026-10-08): an
 * item of {@code flex: 0 1 80pt} with 140pt of lines on 100pt pages keeps 40pt on page 2 and the next item follows at
 * 40pt. The continuation took the 80pt basis again (its remaining minimum, zero, looked like the head's), and the next
 * item started at 80pt.
 */
public class FlexColumnContinuationBasisTest extends AbstractTestCase {
	public FlexColumnContinuationBasisTest(String name) {
		super(name);
	}

	private int nextPage;

	private double nextY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/column-continuation-basis.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals("next item's page", 2, this.nextPage);
		assertEquals(40, this.nextY, 0.1);
	}

	public boolean check_n(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK && Double.isNaN(this.nextY)) {
			this.nextPage = pageNumber;
			this.nextY = y;
			return true;
		}
		return false;
	}
}
