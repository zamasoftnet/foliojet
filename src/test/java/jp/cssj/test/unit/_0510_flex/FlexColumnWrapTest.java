package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * column wrapのテストです(Flex F4d——definite main(100pt)+cross
 * (200pt)限定・align-content: flex-start(既定normal=stretchは列幅へ余白を
 * 均等加算するため密配置で検証)。basis 40pt×3は列あたり2個(40+40≦100、3個目は
 * 40×3=120&gt;100)で2列、列幅=item明示幅50pt: rはx=+50/y=+0)。
 */
public class FlexColumnWrapTest extends AbstractTestCase {
	public FlexColumnWrapTest(String name) {
		super(name);
	}

	private double baseX = Double.NaN, baseY = Double.NaN;

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/column-wrap.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_p(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			this.baseX = x;
			this.baseY = y;
			return true;
		}
		return false;
	}

	/** 同一列の2番目(主軸+40pt)。 */
	public boolean check_q(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX, x, 0.1);
			assertEquals(this.baseY + 40, y, 0.1);
			return true;
		}
		return false;
	}

	/** 2列目の先頭(cross+50pt=1列目の幅、主軸+0)。 */
	public boolean check_r(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(this.baseX + 50, x, 0.1);
			assertEquals(this.baseY, y, 0.1);
			return true;
		}
		return false;
	}
}
