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
			// 和文の両端揃えでは欧文の字間を空けない(2026-10-06。以前は abcdefghi の字間にも配り、x=56.64・幅 43.36)。
			// 余りは「i|あ」「あ|あ」へ入る
			assertEquals(45.0, x, 0);
			assertEquals(55.0, box.getWidth(), 0);
			return true;
		}
		return false;
	}
}
