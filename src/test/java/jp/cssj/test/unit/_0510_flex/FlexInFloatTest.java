package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 幅なしfloat内のFlexを範囲から再構築し、配置座標とrecipe再生の発火を検証する。
 */
public class FlexInFloatTest extends AbstractTestCase {
	public FlexInFloatTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		final long replaysBefore = net.zamasoft.foliojet.layout.segment.BoxRecipeBoxFactory.FLEX_REPLAYS.get();
		File file = new File("files/unittest/0510-flex/flex-in-float.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
		assertTrue("範囲再生がFlexBoxを再構築すること",
				net.zamasoft.foliojet.layout.segment.BoxRecipeBoxFactory.FLEX_REPLAYS.get() > replaysBefore);
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** F1f: TwoPass宿主でもrow配置(2番目のitemは主軸+30pt)。 */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 30, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}

	/** 3番目のitemは主軸+60pt。 */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 60, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}
}
