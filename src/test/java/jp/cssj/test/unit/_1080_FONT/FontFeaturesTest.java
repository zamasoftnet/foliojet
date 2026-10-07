package jp.cssj.test.unit._1080_FONT;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Integration test for {@code font-feature-settings}/{@code font-variant-east-asian}
 * (through increment ④: GSUB single substitution and GPOS palt advance;
 * consult-codex-2026-07-31-font-features.txt §5.3).
 * Uses pdfg2d's test CJK font (U+3001 palt: xAdvance=-500/1000em) on the embedded path
 * to verify that palt halves the inline width of ten fullwidth Japanese commas.
 */
public class FontFeaturesTest extends AbstractTestCase {
	public FontFeaturesTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/1080-FONT/font-features.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** Without features: ten fullwidth Japanese commas × 10 pt = 100 pt. */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(100, box.getWidth(), 0.01);
			return true;
		}
		return false;
	}

	/** palt: each glyph 10 pt - 5 pt (xAdvance -500/1000em) = 50 pt. */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(50, box.getWidth(), 0.01);
			return true;
		}
		return false;
	}

	/** jis78: variant substitution does not change width (the substitution itself is already covered in pdfg2d). */
	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(10, box.getWidth(), 0.01);
			return true;
		}
		return false;
	}

	/** Default: slash-pair kerning (negative kern) applies, making this narrower than the raw width 6×3.48=20.88 pt. */
	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertTrue("kerned width must be narrower: " + box.getWidth(), box.getWidth() < 20.88 - 0.001);
			return true;
		}
		return false;
	}

	/** "kern" off: explicit disabling gives exactly the raw width (slash advance 348/1000em × 6). */
	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(20.88, box.getWidth(), 0.01);
			return true;
		}
		return false;
	}

	/** Default: fi ligature (advance 671 < f+i=710) applies, making this narrower than 14.20 pt. */
	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertTrue("ligated width must be narrower: " + box.getWidth(), box.getWidth() < 14.20 - 0.001);
			return true;
		}
		return false;
	}

	/** "liga" 0, "kern" 0: exactly the raw width (f 385 + i 325)×2/1000em. */
	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(14.20, box.getWidth(), 0.01);
			return true;
		}
		return false;
	}

	/**
	 * font-variant-ligatures: none (+kern 0): disables standard ligatures, giving the raw width
	 * (2026-09-03; correction of the old CSS-SUPPORT description).
	 */
	public boolean check_h(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(14.20, box.getWidth(), 0.01);
			return true;
		}
		return false;
	}

	/** font-variant-ligatures: no-common-ligatures (+kern 0): same as above. */
	public boolean check_i(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(14.20, box.getWidth(), 0.01);
			return true;
		}
		return false;
	}

	/** font-variant-ligatures: common-ligatures: ligatures apply as they do by default. */
	public boolean check_j(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertTrue("ligated width must be narrower: " + box.getWidth(), box.getWidth() < 14.20 - 0.001);
			return true;
		}
		return false;
	}
}
