package jp.cssj.test.unit.misc;

import jp.cssj.cti2.helpers.MimeTypeHelper;
import junit.framework.TestCase;

public class MimeTypeUtilsTest extends TestCase {
	public MimeTypeUtilsTest(String name) {
		super(name);
	}

	public void testGetParameter() throws Exception {
		assertEquals("UTF-8", MimeTypeHelper.getParameter(
				"text/html; charset=UTF-8", "charset"));
		assertEquals("UTF-8", MimeTypeHelper.getParameter(
				"text/html; charset= UTF-8 ", "charset"));
		assertEquals("UTF-8", MimeTypeHelper.getParameter(
				"text/html; charset ='UTF-8'", "charset"));
		assertEquals("UTF-8", MimeTypeHelper.getParameter(
				"text/html; charset=\"UTF-8\"", "charset"));
		assertEquals("UTF-8", MimeTypeHelper.getParameter(
				"text/html; charset= UTF-8 ; hoge=hige;", "charset"));
		assertEquals("UTF-8 ", MimeTypeHelper.getParameter(
				"text/html; charset= \"UTF-8 \"; hoge=hige;", "charset"));
	}

	public void testGetTypePart() throws Exception {
		assertEquals("text/html", MimeTypeHelper
				.getTypePart("text/html; charset=UTF-8"));
		assertEquals("text/html", MimeTypeHelper
				.getTypePart("text/html ; charset=UTF-8 ; hoge=hige;"));
		assertEquals("text/html", MimeTypeHelper.getTypePart(" text/html "));
	}
}
