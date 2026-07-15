package jp.cssj.test.unit._1060_META;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class XHTMLUTF8Test extends AbstractTestCase {
	public XHTMLUTF8Test(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/1060-META/xhtml-utf8.xhtml");
		CTISessionHelper.transcodeFile(this.session, file, "application/xhtml+xml", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder buff = new StringBuilder();
			box.getText(buff);
			assertEquals("テスト", buff.toString().trim());
			return true;
		}
		return false;
	}
}
