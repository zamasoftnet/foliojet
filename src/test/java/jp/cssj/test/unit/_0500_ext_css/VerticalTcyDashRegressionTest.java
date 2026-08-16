package jp.cssj.test.unit._0500_ext_css;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import net.zamasoft.pdfg2d.gc.text.Text;

/** 実書籍で見つかった縦中横の中央揃えと縦組みダッシュの回帰です。 */
public class VerticalTcyDashRegressionTest extends AbstractTestCase {
	public VerticalTcyDashRegressionTest(final String name) {
		super(name);
	}

	@Override
	protected void transcode() throws Exception {
		CTISessionHelper.transcodeFile(this.session,
				new File("files/unittest/0500-ext-css/vertical-tcy-dash-regression.html"), "text/html", null);
	}

	private boolean tcy(final IBox box) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		assertEquals("縦中横セルは1em", 12, box.getWidth(), .01);
		assertEquals("縦中横セルは1em", 12, box.getHeight(), .01);
		final java.awt.geom.GeneralPath ink = new java.awt.geom.GeneralPath();
		box.textShape(null, ink, new java.awt.geom.AffineTransform(), 0, 0);
		assertFalse("縦中横の字面輪郭", ink.getBounds2D().isEmpty());
		assertEquals("縮小後の字面を送り幅でなく実輪郭により1em中央へ置く",
				box.getWidth() / 2.0, ink.getBounds2D().getCenterX(), .02);
		return true;
	}

	public boolean check_t41(final IBox box, final int page, final double x, final double y) { return tcy(box); }
	public boolean check_t43(final IBox box, final int page, final double x, final double y) { return tcy(box); }
	public boolean check_t45(final IBox box, final int page, final double x, final double y) { return tcy(box); }
	public boolean check_f41(final IBox box, final int page, final double x, final double y) { return tcy(box); }
	public boolean check_f43(final IBox box, final int page, final double x, final double y) { return tcy(box); }
	public boolean check_f45(final IBox box, final int page, final double x, final double y) { return tcy(box); }
	public boolean check_n41(final IBox box, final int page, final double x, final double y) { return tcy(box); }
	public boolean check_n43(final IBox box, final int page, final double x, final double y) { return tcy(box); }
	public boolean check_n45(final IBox box, final int page, final double x, final double y) { return tcy(box); }

	public boolean check_dash(final IBox box, final int page, final double x, final double y) {
		if (!(box instanceof TextBlockBox textBlock)) {
			return false;
		}
		final int[] dashPair = { 0 };
		textBlock.forEachLine(line -> {
			for (int i = 0; i < line.getContentCount(); ++i) {
				if (!(line.getContent(i) instanceof Text text)) {
					continue;
				}
				final String chars = new String(text.getChars(), 0, text.getCharCount());
				if (chars.contains("――")) {
					final int[] gids = text.getGlyphIds();
					for (int g = 1; g < text.getGlyphCount(); ++g) {
						if (text.getFontMetrics().getKerning(gids[g - 1], gids[g]) > 0) {
							dashPair[0]++;
						}
					}
				}
			}
		});
		assertTrue("二倍ダーシは実グリフ輪郭由来の詰めを持つ", dashPair[0] > 0);
		return true;
	}
}
