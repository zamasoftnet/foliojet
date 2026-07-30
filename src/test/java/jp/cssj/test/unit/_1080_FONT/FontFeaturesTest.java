package jp.cssj.test.unit._1080_FONT;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * {@code font-feature-settings}/{@code font-variant-east-asian}の統合テストです
 * (増分④まで=GSUB単一置換とGPOS palt advance。
 * consult-codex-2026-07-31-font-features.txt §5.3)。pdfg2dのテスト用CJK
 * フォント(U+3001のpalt: xAdvance=-500/1000em)を embedded 経路で使い、
 * 全角読点10文字のインライン幅が palt で半分になることを固定する。
 */
public class FontFeaturesTest extends AbstractTestCase {
	public FontFeaturesTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/1080-FONT/font-features.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** featureなし: 全角読点10文字 × 10pt = 100pt。 */
	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(100, box.getWidth(), 0.01);
			return true;
		}
		return false;
	}

	/** palt: 各グリフ 10pt - 5pt(xAdvance -500/1000em)= 50pt。 */
	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(50, box.getWidth(), 0.01);
			return true;
		}
		return false;
	}

	/** jis78: 異体字への置換は幅を変えない(置換自体はpdfg2d側で固定済み)。 */
	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(10, box.getWidth(), 0.01);
			return true;
		}
		return false;
	}

	/** 既定: スラッシュ対(kern負)が効き、素の幅6×3.48=20.88ptより狭い。 */
	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertTrue("kerned width must be narrower: " + box.getWidth(), box.getWidth() < 20.88 - 0.001);
			return true;
		}
		return false;
	}

	/** "kern" off: 明示無効化で素の幅ちょうど(slash advance 348/1000em × 6)。 */
	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(20.88, box.getWidth(), 0.01);
			return true;
		}
		return false;
	}

	/** 既定: fi合字(advance 671 < f+i=710)が効き、14.20ptより狭い。 */
	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertTrue("ligated width must be narrower: " + box.getWidth(), box.getWidth() < 14.20 - 0.001);
			return true;
		}
		return false;
	}

	/** "liga" 0, "kern" 0: 素の幅ちょうど(f 385 + i 325)×2/1000em。 */
	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			assertEquals(14.20, box.getWidth(), 0.01);
			return true;
		}
		return false;
	}
}
