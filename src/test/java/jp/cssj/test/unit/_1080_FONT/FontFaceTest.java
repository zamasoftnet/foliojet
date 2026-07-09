package jp.cssj.test.unit._1080_FONT;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FontFaceTest extends AbstractTestCase {
	public FontFaceTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/1080-FONT/font-face.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.err.println("x/"+x);
			System.err.println("width/"+box.getWidth());
			assertEquals(186, x, 1);
			// 14文字 × 18pt (36ptフォントの半角0.5em)。
			// 旧値245はTrueTypeGlyphListのarraycopy不具合でグリフ取得が
			// 失敗していた時期の値。
			assertEquals(252, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.err.println("x/"+x);
			System.err.println("width/"+box.getWidth());
			assertEquals(186, x, 1);
			assertEquals(222, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
