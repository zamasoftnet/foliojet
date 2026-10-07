package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Contract test for flex row splitting ({@code page-break-inside: avoid})
 * (2026-08-07, since Bug C).
 *
 * <p>
 * At F0b, flex was always atomic (fragmentation in css-flexbox-1 §10 was unsupported because it is
 * informative), and this document checked that default behavior. After Bug C introduced row splitting
 * like table rows (see {@code FlexBox.split}), flex rows also split forcibly by default.
 * The test was therefore updated to verify that explicit {@code page-break-inside: avoid}
 * correctly forces "move whole to the next page"
 * ({@code page-break-inside: avoid} was added to the fixture).
 * </p>
 */
public class FlexAtomicTest extends AbstractTestCase {
	public FlexAtomicTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/atomic-move.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** A flex with page-break-inside: avoid moves whole to the next page without splitting. */
	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals("the flex container must move to page 2 as a whole", 2, pageNumber);
			final StringBuilder buff = new StringBuilder();
			box.getText(buff);
			final String text = buff.toString();
			assertTrue("all items must be present: " + text, text.contains("alpha") && text.contains("gamma"));
			return true;
		}
		return false;
	}
}
