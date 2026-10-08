package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * Tests column direction (Flex F4b, main axis = page axis). With a definite 300 pt main axis,
 * basis 60/80/100 + grow 1/1/2 distributes 60 free as 15/15/30: heights 75/95/130,
 * y=+0/+75/+170. Default cross-axis stretch gives width 200 pt (r has explicit width 80 pt).
 * justify-content: flex-end puts the remaining 70 pt at the start.
 * A column item with an auto basis and an auto height is measured at its cross size (2026-10-08; until then the
 * whole container fell back to one stacked column, FLEX_COLUMN_FALLBACKS_AUTO_MAIN).
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
		assertEquals("auto高の項目は測ってfallbackしない", fallbacksBefore,
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

	/** grow 2 gets twice the expansion (+30). Explicit width 80 pt takes precedence over stretch. */
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

	/** The container occupies the definite height of 300 pt (following content = +300+10 marker). */
	public boolean check_after2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.e0Y = y + 10;
			assertEquals(this.baseY + 300, y, 0.1);
			return true;
		}
		return false;
	}

	/** justify-content: flex-end: the remaining 70 pt goes at the start. */
	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.e0Y + 70, y, 0.1);
			return true;
		}
		return false;
	}

	/** The auto item follows the 25 pt item (no grow): the same stacking as the old fallback. */
	public boolean check_fb2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.e0Y + 100 + 25, y, 0.1);
			return true;
		}
		return false;
	}
}
