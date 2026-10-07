package jp.cssj.test.unit._0060_page_break_after;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Regression test for {@code page-break-after: if-recto} on floats
 * (2026-08-01).
 *
 * <p>
 * Previously, the break-after switch in {@code addBound()} had no IF_VERSO/IF_RECTO cases and fell
 * through to {@code default: throw new IllegalStateException()} (a crash on valid CSS values).
 * It now uses the same rule as {@code endFlowBlock()} for flow blocks: immediately break to the
 * opposite side if the current page is on the specified side.
 * </p>
 *
 * <p>
 * The first page of the document is recto (an odd page), so content after the if-recto float
 * moves to page 2.
 * </p>
 */
public class FloatIfRectoTest extends AbstractTestCase {
	public FloatIfRectoTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0060-page-break-after/float-if-recto.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertEquals(1, pageNumber);
		return box.getType() == BoxType.BLOCK;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		assertEquals(2, pageNumber);
		return box.getType() == BoxType.BLOCK;
	}
}
