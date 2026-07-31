package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;

/**
 * Grid G1dのテストです。幅なしfloat(TwoPass計測)の中のGridは
 * GridBuilderが活性化せずG0(単一列積み)で両passとも一貫し、
 * floatのrange sealはGRID_RANGEで弾かれてLegacyRecords bindに
 * 留まる(範囲再生だけがGridBuilderを活性化して計測と食い違う
 * 事故の防止)。
 */
public class GridInFloatTest extends AbstractTestCase {
	public GridInFloatTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long before = ContinuationStats
				.twoPassSealRejects(ContinuationStats.TwoPassSealReject.GRID_RANGE);
		File file = new File("files/unittest/0500-grid/grid-in-float.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		final long after = ContinuationStats
				.twoPassSealRejects(ContinuationStats.TwoPassSealReject.GRID_RANGE);
		assertTrue("Gridを含むfloat本文のrange sealはGRID_RANGEで弾かれること", after > before);
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** G0フォールバック: トラック配置されず単一列に積まれる。 */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 20, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 40, y, 0.1);
			return true;
		}
		return false;
	}
}
