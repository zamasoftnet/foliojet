package jp.cssj.test.unit._0120_float;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FloatMaxSizeVTest extends AbstractTestCase {
	public FloatMaxSizeVTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0120-float/float-max-size-h.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println(box.getWidth());
			assertEquals(284, box.getWidth(), 1);
			System.out.println(box.getHeight());
			// 2026-09-02: 標準モード(DOCTYPE あり)では画像だけの行にも strut が
			// 入るので、画像の下に descent+半行送りの隙間(4.7pt)が付く(CSS 2.1
			// §10.8。ブラウザの標準モードと同じ。quirks なら従来どおり 86)
			assertEquals(90.73, box.getHeight(), 1);
			return true;
		}
		return false;
	}
}
