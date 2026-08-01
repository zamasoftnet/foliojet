package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * auto marginのテストです(Flex F3e——§8.1。主軸: margin-left:autoが
 * 余白120ptを全て消費しjustify-content:centerは働かない(a=+0、b=+160)。
 * cross軸: 行高40ptに対しtop+bottom auto=中央(+10)、top autoのみ=
 * 終端寄せ(+20))。
 */
public class FlexAutoMarginTest extends AbstractTestCase {
	public FlexAutoMarginTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, base2Y = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/auto-margin.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** auto marginが余白を消費するためjustifyは働かずa=行頭。 */
	public boolean check_a1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			return true;
		}
		return false;
	}

	/** margin-left:autoが120pt全てを得る(+160)。 */
	public boolean check_a2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 160, x, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_tallm(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.base2Y = y;
			return true;
		}
		return false;
	}

	/** top+bottom auto=行内中央(+10)。 */
	public boolean check_cm(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.base2Y + 10, y, 0.1);
			return true;
		}
		return false;
	}

	/** top autoのみ=終端寄せ(+20)。 */
	public boolean check_em(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.base2Y + 20, y, 0.1);
			return true;
		}
		return false;
	}
}
