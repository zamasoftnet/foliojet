package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * Tests stretch (the align-items default) (Flex F3c).
 * An item with auto cross size stretches to the 40 pt line height, and **the authored background
 * follows the item size**, demonstrating the purpose of the takeover design (F1d).
 */
public class FlexStretchBackgroundTest extends AbstractTestCase {
	public FlexStretchBackgroundTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/stretch-card-background.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** An auto-height item with a background stretches to the 40 pt line height. */
	public boolean check_card(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(40.0, box.getPageExtent(WritingMode.TB), 0.1);
			return true;
		}
		return false;
	}
}
