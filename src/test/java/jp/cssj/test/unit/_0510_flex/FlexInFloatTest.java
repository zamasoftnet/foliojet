package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * TwoPass宿主(幅なしfloat)内のFlexコンテナのテストです(Flex F0c)。
 * floatの本文はSourceRangeBodyへseal(records解放)され、bindは範囲再生
 * (FLEXレシピからFlexBoxを再構築)を通る——FLEX_REPLAYSの増加で
 * 空虚な緑を防ぐ({@code GridInFloatTest}と同型)。F0は単一列縮退の
 * ため、itemは縦積み。
 */
public class FlexInFloatTest extends AbstractTestCase {
	public FlexInFloatTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long rejectsBefore = net.zamasoft.foliojet.layout.fragment.ContinuationStats
				.twoPassSealRejects(net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.FLEX_RANGE);
		File file = new File("files/unittest/0510-flex/flex-in-float.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		// Flex F1d: 範囲再生はFlexBuilder活性・recordsはF0単一列のため、
		// Flexを含む範囲のsealはFLEX_RANGEでfail closed(Grid G1dと同型)。
		// F1fのRetainedFlex/FlexEventでparity確立後に解禁し、このassertを
		// FLEX_REPLAYS>0(GridInFloatTestのG3d3形)へ戻す
		assertTrue("FLEX_RANGE rejectが発火すること",
				net.zamasoft.foliojet.layout.fragment.ContinuationStats.twoPassSealRejects(
						net.zamasoft.foliojet.layout.fragment.ContinuationStats.TwoPassSealReject.FLEX_RANGE) > rejectsBefore);
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** F0: 単一列縮退のため2番目のitemは直下(+20)。 */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 20, y, 0.1);
			return true;
		}
		return false;
	}

	/** 3番目のitemはさらに直下(+40)。 */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 40, y, 0.1);
			return true;
		}
		return false;
	}
}
