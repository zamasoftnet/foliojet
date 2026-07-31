package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;

/**
 * TwoPass宿主内Gridのテストです。G1dでは幅なしfloat内のGridは
 * G0(単一列積み)、G3d1でLegacyRecords bind経路の実トラック配置
 * (2列)になり、G3d3でrange sealが解禁された——floatの本文は
 * SourceRangeBodyへseal(records解放)され、bindは範囲再生
 * (DocumentBuilder駆動の新品GridBuilder=GRIDレシピ再構築)を通る。
 */
public class GridInFloatTest extends AbstractTestCase {
	public GridInFloatTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long rejectsBefore = ContinuationStats
				.twoPassSealRejects(ContinuationStats.TwoPassSealReject.GRID_RANGE);
		final long replaysBefore = net.zamasoft.foliojet.layout.segment.BoxRecipeBoxFactory.GRID_REPLAYS.get();
		File file = new File("files/unittest/0500-grid/grid-in-float.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		// G3d3: GRID_RANGE rejectは解除され、floatのbindは範囲再生が
		// GRIDレシピからGridBoxを再構築する(空虚な緑の防止)
		assertEquals("GRID_RANGE rejectは発火しないこと", rejectsBefore,
				ContinuationStats.twoPassSealRejects(ContinuationStats.TwoPassSealReject.GRID_RANGE));
		assertTrue("範囲再生がGridBoxを再構築すること",
				net.zamasoft.foliojet.layout.segment.BoxRecipeBoxFactory.GRID_REPLAYS.get() > replaysBefore);
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
