package jp.cssj.test.unit._0320_list_style_type;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

public class KanjiZenkakuTest extends AbstractTestCase {
	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0320-list-style-type/kanji-zenkaku.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public KanjiZenkakuTest(String name) {
		super(name);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder textBuff = new StringBuilder();
			box.getText(textBuff);
			String text = textBuff.toString();
			System.out.println(text);
			assertEquals("一、 A二、 B三、 C四、 D", text);
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder textBuff = new StringBuilder();
			box.getText(textBuff);
			String text = textBuff.toString();
			System.out.println(text);
			assertEquals("１． A２． B３． C４． D", text);
			return true;
		}
		return false;
	}

	public boolean check_c(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder textBuff = new StringBuilder();
			box.getText(textBuff);
			String text = textBuff.toString();
			System.out.println(text);
			assertEquals(
					"●A一千二百三十四万五千六百七十九●B一千二百三十四万五千六百八十●C一千二百三十四万五千六百八十一●D一千二百三十四万五千六百八十二",
					text);
			return true;
		}
		return false;
	}

	public boolean check_d(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder textBuff = new StringBuilder();
			box.getText(textBuff);
			String text = textBuff.toString();
			System.out.println(text);
			assertEquals("●A１２３４５６７９●B１２３４５６８０●C１２３４５６８１●D１２３４５６８２", text);
			return true;
		}
		return false;
	}
}
