package jp.cssj.test.unit._1000_TABLE;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.style.box.impl.TableCellBox;
import jp.cssj.test.unit.AbstractTestCase;

public class StrictFontSizeTest extends AbstractTestCase {
	public StrictFontSizeTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/1000-TABLE/strict-font-size.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_TABLE_CELL) {
			TableCellBox cell = (TableCellBox) box;
			assertEquals(20, cell.getBlockParams().fontStyle.getSize(), 0);
			return true;
		}
		return false;
	}
}
