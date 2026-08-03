package jp.cssj.test.unit._1080_FONT;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
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
		if (box.getType() == BoxType.INLINE) {
			System.err.println("x/"+x);
			System.err.println("width/"+box.getWidth());
			assertEquals(186, x, 1);
			// ph-css移行(2026-07)で unicode-range が実際に効くようになった。
			// myfont1 は U+100-FFFF 限定のため ASCII は範囲外となる。
			// 2026-08-03にテスト用フォントを公開Notoの自動取得へ切り替え、
			// さらに総称フォント monospace の先頭を Noto Sans Mono CJK JP に
			// した。**ASCIIの受け皿が本物の等幅フォント(半角=0.5em)になった**
			// ため 252.0 に戻った——これは unicode-range が無視されていた時期の
			// 値と同じだが、由来が違う(当時はipamの半角、今は等幅フォントの
			// 半角)。途中の245.124は受け皿が無くMISSINGで描かれていた時期の値。
			assertEquals(252.0, box.getWidth(), 1);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			System.err.println("x/"+x);
			System.err.println("width/"+box.getWidth());
			assertEquals(186, x, 1);
			// MinionPro-Regular. Narrower than the earlier 222 because pdfg2d
			// now applies GSUB standard ligatures (fj/fi/ff) and GPOS pair
			// kerning (VA, Va) to the run.
			assertEquals(210.85, box.getWidth(), 1);
			return true;
		}
		return false;
	}
}
