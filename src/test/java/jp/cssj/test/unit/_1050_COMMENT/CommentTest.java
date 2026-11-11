package jp.cssj.test.unit._1050_COMMENT;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * HTML comments. A comment that is never closed takes the rest of the document, as in Chrome (2026-11-11): the
 * fixture ends with {@code <!-- comment>} that no {@code -->} follows, so nothing after {@code b} is drawn. Until
 * then a filter over every HTML input ended such a comment at its first {@code >}, as the browsers of the 2000s did,
 * and this test expected "a b c" (HTMLParser.parseReader says why the filter went).
 */
public class CommentTest extends AbstractTestCase {
	public CommentTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/1050-COMMENT/comment.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder buff = new StringBuilder();
			box.getText(buff);
			assertEquals("a b", buff.toString().trim());
			return true;
		}
		return false;
	}
}
