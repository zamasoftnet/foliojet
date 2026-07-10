package jp.cssj.test.unit._1050_COMMENT;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.style.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class CommentTest extends AbstractTestCase {
	public CommentTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/1050-COMMENT/comment.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == IBox.TYPE_BLOCK) {
			StringBuilder buff = new StringBuilder();
			box.getText(buff);
			assertEquals("a b c", buff.toString().trim());
			return true;
		}
		return false;
	}
}
