package jp.cssj.test.unit._3040_plugin;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class InlineObjectFactoryTest extends AbstractTestCase {
	public InlineObjectFactoryTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.err.println(x + "/" + y);
			assertEquals(0, x, 0);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_INLINE) {
			System.err.println(x + "/" + y);
			assertEquals(140, x, 0);
			return true;
		}
		return false;
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/3040-plugins/inline-object-factory.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}
}
