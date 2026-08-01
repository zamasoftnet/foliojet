package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * column方向のテストです(Flex F4b——主軸=page軸。definite主軸300ptに
 * basis 60/80/100+grow 1/1/2でfree 60を15/15/30分配=高さ75/95/130、
 * y=+0/+75/+170。crossはstretch既定で幅200pt(rは明示幅80pt)。
 * justify-content: flex-endは残余70ptを先頭へ。basis/height未確定の
 * columnはコンテナ単位fallback(単一列縮退+FLEX_COLUMN_FALLBACKS)。
 */
public class FlexColumnFixedBasisTest extends AbstractTestCase {
	public FlexColumnFixedBasisTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;
	private double e0Y = Double.NaN;

	protected void transcode() throws Exception {
		final long fallbacksBefore = net.zamasoft.foliojet.layout.builder.impl.FlexBuilder.FLEX_COLUMN_FALLBACKS_AUTO_MAIN
				.get();
		File file = new File("files/unittest/0510-flex/column-fixed-basis.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertEquals("auto高columnはコンテナ単位fallback(AUTO_MAIN_SIZE)", fallbacksBefore + 1,
				net.zamasoft.foliojet.layout.builder.impl.FlexBuilder.FLEX_COLUMN_FALLBACKS_AUTO_MAIN.get());
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			assertEquals("stretch幅", 200.0, box.getLineExtent(WritingMode.TB), 0.1);
			assertEquals("grow後の高さ", 75.0, box.getPageExtent(WritingMode.TB), 0.1);
			return true;
		}
		return false;
	}

	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 75, y, 0.1);
			return true;
		}
		return false;
	}

	/** grow 2は2倍の伸長(+30)。明示幅80ptはstretchより優先。 */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 170, y, 0.1);
			assertEquals(80.0, box.getLineExtent(WritingMode.TB), 0.1);
			assertEquals(130.0, box.getPageExtent(WritingMode.TB), 0.1);
			return true;
		}
		return false;
	}

	/** コンテナはdefinite高300ptを占める(後続=+300+10marker)。 */
	public boolean check_after2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.e0Y = y + 10;
			assertEquals(this.baseY + 300, y, 0.1);
			return true;
		}
		return false;
	}

	/** justify-content: flex-end——残余70ptが先頭に入る。 */
	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.e0Y + 70, y, 0.1);
			return true;
		}
		return false;
	}

	/** fallback(単一列): 内容が失われず全幅で積まれる。 */
	public boolean check_fb2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.e0Y + 100 + 25, y, 0.1);
			return true;
		}
		return false;
	}
}
