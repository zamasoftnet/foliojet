package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * align-content(definite cross)のテストです(Flex F3d——§9.6。
 * 高さ100ptのwrapコンテナに20pt×2行=free 60pt: center=行1が+30、
 * space-between=行2が+80。単一行(nowrap)+definite crossの行高=
 * コンテナ内cross(§9.4——auto高itemが60ptへstretch)。
 */
public class FlexAlignContentTest extends AbstractTestCase {
	public FlexAlignContentTest(String name) {
		super(name);
	}

	private double m1Y = Double.NaN, m2Y = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/align-content.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_m1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.m1Y = y;
			return true;
		}
		return false;
	}

	/** center: 行1=コンテナ上端+30。 */
	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.m1Y + 40, y, 0.1);
			return true;
		}
		return false;
	}

	/** center: 行2=+50。 */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.m1Y + 60, y, 0.1);
			return true;
		}
		return false;
	}

	public boolean check_m2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.m2Y = y;
			assertEquals(this.m1Y + 110, y, 0.1);
			return true;
		}
		return false;
	}

	/** space-between: 行1=+0。 */
	public boolean check_t(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.m2Y + 10, y, 0.1);
			return true;
		}
		return false;
	}

	/** space-between: 行2=+80。 */
	public boolean check_v(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.m2Y + 90, y, 0.1);
			return true;
		}
		return false;
	}

	/** §9.4: 単一行+definite crossはauto高itemがコンテナ内crossへstretch。 */
	public boolean check_card2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(60.0, box.getPageExtent(WritingMode.TB), 0.1);
			return true;
		}
		return false;
	}
}
