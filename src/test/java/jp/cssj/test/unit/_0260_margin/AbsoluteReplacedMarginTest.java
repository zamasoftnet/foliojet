package jp.cssj.test.unit._0260_margin;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class AbsoluteReplacedMarginTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0260-margin/absolute-replaced-margin.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public AbsoluteReplacedMarginTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.REPLACED) {
			System.out.println(x + "/" + y + "/" + box.getWidth() + "/"
					+ box.getHeight());
			assertEquals(5, x, 0);
			assertEquals(5, y, 0);
			assertEquals(220, box.getWidth(), 0);
			assertEquals(160, box.getHeight(), 0);
			return true;
		}
		return false;
	}
}
