package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 縦書き(vertical-rl)Flexのテストです(Flex F6——FlexAxesの写像どおり
 * rowの主軸=線軸(上→下)、columnの主軸=page軸(右→左)。
 * row: qはpの下+40pt(同一line=同x)。column: wはvの左40pt(同y)。
 */
public class FlexVerticalTest extends AbstractTestCase {
	public FlexVerticalTest(String name) {
		super(name);
	}

	private double pX = Double.NaN, pY = Double.NaN;
	private double vX = Double.NaN, vY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/vertical-flex.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.pX = x;
			this.pY = y;
			return true;
		}
		return false;
	}

	/** 縦書きrow: 主軸=線軸(上→下)。qはpの直下+40pt、同x(同一line)。 */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.pX, x, 0.1);
			assertEquals(this.pY + 40, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_v(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.vX = x;
			this.vY = y;
			return true;
		}
		return false;
	}

	/** 縦書きcolumn: 主軸=page軸(右→左)。wはvの左40pt、同y。 */
	public boolean check_w(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.vX - 40, x, 0.1);
			assertEquals(this.vY, y, 0.1);
			return true;
		}
		return false;
	}
}
