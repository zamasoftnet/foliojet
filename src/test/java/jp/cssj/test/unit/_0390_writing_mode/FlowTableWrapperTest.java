package jp.cssj.test.unit._0390_writing_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class FlowTableWrapperTest extends AbstractTestCase {
	public FlowTableWrapperTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0390-writing-mode/flow-table-wrapper.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			System.out.println("pageNumber: " + pageNumber);
			System.out.println("width: " + box.getWidth());
			System.out.println("height: " + box.getHeight());
			assertEquals(2, pageNumber);
			assertEquals(98, box.getWidth(), 0);
			assertEquals(90, box.getHeight(), 0);
			return true;
		}
		return false;
	}
}
