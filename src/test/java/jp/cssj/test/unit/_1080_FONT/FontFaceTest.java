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
			// The ph-css migration (2026-07) made unicode-range actually take effect.
			// myfont1 is limited to U+100-FFFF, so ASCII is outside its range.
			// On 2026-08-03, test fonts switched to automatic download of public Noto,
			// and Noto Sans Mono CJK JP became the first font in the generic monospace family.
			// **ASCII now falls back to a true monospace font (halfwidth = 0.5em)**,
			// restoring 252.0. This equals the value from when unicode-range was ignored,
			// but has a different origin (then ipam halfwidth glyphs; now monospace
			// halfwidth glyphs). The intervening 245.124 came from rendering with MISSING, with no fallback font.
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
