package jp.cssj.test.unit._1060_META;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class DoubleDeclMetaTest extends AbstractTestCase {
	public DoubleDeclMetaTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/1060-META/double-decl-meta.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
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
