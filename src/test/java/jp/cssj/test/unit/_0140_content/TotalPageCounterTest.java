package jp.cssj.test.unit._0140_content;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * 総ページ数カウンタ({@code counter(pages)})の検証。実装自体は
 * {@code AbstractUserAgent.prepare()}で既に行われていたが(css-page-3
 * §6.1)、無テストだったため今回追加した。
 */
public class TotalPageCounterTest extends AbstractTestCase {
	public TotalPageCounterTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0140-content/total-page-counter.html");
		this.session.property("processing.pass-count", "3");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("Alpha 1/5", text.toString());
			return true;
		}
		return false;
	}

	public boolean check_e(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("Epsilon 5/5", text.toString());
			return true;
		}
		return false;
	}
}
