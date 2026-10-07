package jp.cssj.test.unit._0030_text_align;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class HankakuTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0030-text-align/hankaku.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public HankakuTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			// Japanese justification leaves Latin letters unspaced (2026-10-06; abcdefghi was spaced: x=56.64, width 43.36).
			// The remainder goes into "i|あ" and "あ|あ".
			assertEquals(45.0, x, 0);
			assertEquals(55.0, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
