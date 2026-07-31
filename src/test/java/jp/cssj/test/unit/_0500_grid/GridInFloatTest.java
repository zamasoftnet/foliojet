package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;

/**
 * TwoPass宿主内Gridのテストです。G1dでは幅なしfloat内のGridは
 * G0(単一列積み)だったが、G3d1のRetainedGrid/GridEventにより
 * LegacyRecords bind経路で実トラック配置(2列)になる。floatの
 * range sealは引き続きGRID_RANGEで弾かれLegacyRecordsに留まる
 * (範囲再生とのparity確立=G3d3までのゲート)。
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

	/** G3d1: float内でも実トラック配置(2列目=+60)。 */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 60, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** 2行目1列目。 */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 20, y, 0.1);
			return true;
		}
		return false;
	}
}
