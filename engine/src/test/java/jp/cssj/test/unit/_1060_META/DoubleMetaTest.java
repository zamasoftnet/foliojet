package jp.cssj.test.unit._1060_META;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class DoubleMetaTest extends AbstractTestCase {
	public DoubleMetaTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/1060-META/double-meta.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			StringBuffer buff = new StringBuffer();
			box.getText(buff);
			assertEquals("テスト", buff.toString().trim());
			return true;
		}
		return false;
	}
}
