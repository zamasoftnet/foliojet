package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Flex F0b: rescue path for an atomic container larger than the page.
 * It is output without infinite loops or content loss (visual rescue), and subsequent content
 * is preserved (the absolute requirement to eliminate crashes).
 */
public class FlexOversizedAtomicTest extends AbstractTestCase {
	public FlexOversizedAtomicTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/oversized-atomic.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		// An atomic box exceeding the page is output by rescue splitting; its presence is sufficient.
		return box.getType() == BoxType.BLOCK;
	}

	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		// Subsequent content is preserved.
		return box.getType() == BoxType.BLOCK;
	}
}
