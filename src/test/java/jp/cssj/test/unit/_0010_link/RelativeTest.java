package jp.cssj.test.unit._0010_link;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.style.box.BoxType;
import net.zamasoft.foliojet.style.box.IBox;
import net.zamasoft.foliojet.xml.Constants;
import jp.cssj.test.unit.AbstractTestCase;

public class RelativeTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File("files/unittest/0010-link/relative.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public RelativeTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			CSSElement ce = (CSSElement) box.getParams().element;
			String link = Constants.XLINK_HREF_ATTR.getValue(ce.atts);
			assertEquals("http://www.yahoo.co.jp/", link);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			CSSElement ce = (CSSElement) box.getParams().element;
			String link = Constants.XLINK_HREF_ATTR.getValue(ce.atts);
			assertEquals("http://www.w3.org/TR/CSS21/page.html#page-breaks",
					link);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			CSSElement ce = (CSSElement) box.getParams().element;
			String link = Constants.XLINK_HREF_ATTR.getValue(ce.atts);
			assertEquals("#next", link);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			CSSElement ce = (CSSElement) box.getParams().element;
			String link = Constants.XLINK_HREF_ATTR.getValue(ce.atts);
			assertEquals("#hoge", link);
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			CSSElement ce = (CSSElement) box.getParams().element;
			String link = Constants.XLINK_HREF_ATTR.getValue(ce.atts);
			assertEquals("relative.html", link);
			return true;
		}
		return false;
	}

	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			CSSElement ce = (CSSElement) box.getParams().element;
			String link = Constants.XLINK_HREF_ATTR.getValue(ce.atts);
			assertEquals("#prev", link);
			return true;
		}
		return false;
	}

	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.INLINE) {
			CSSElement ce = (CSSElement) box.getParams().element;
			String link = Constants.XLINK_HREF_ATTR.getValue(ce.atts);
			assertEquals("relative.html#tag", link);
			return true;
		}
		return false;
	}
}
