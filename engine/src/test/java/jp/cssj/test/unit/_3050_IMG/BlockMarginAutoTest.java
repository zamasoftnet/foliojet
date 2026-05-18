package jp.cssj.test.unit._3050_IMG;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.AbstractReplacedBox;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class BlockMarginAutoTest extends AbstractTestCase {
	public BlockMarginAutoTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3050-IMG/block-margin-auto.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_REPLACED) {
			AbstractReplacedBox r = (AbstractReplacedBox) box;
			System.err.println("m/" + r.getFrame().margin.left);
			assertEquals(94, r.getFrame().margin.left, 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_REPLACED) {
			AbstractReplacedBox r = (AbstractReplacedBox) box;
			System.err.println("m/" + r.getFrame().margin.left);
			assertEquals(188, r.getFrame().margin.left, 0);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_REPLACED) {
			AbstractReplacedBox r = (AbstractReplacedBox) box;
			System.err.println("m/" + r.getFrame().margin.left);
			assertEquals(0, r.getFrame().margin.left, 0);
			return true;
		}
		return false;
	}
}
