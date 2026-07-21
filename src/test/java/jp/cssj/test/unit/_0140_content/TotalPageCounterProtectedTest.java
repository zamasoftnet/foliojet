package jp.cssj.test.unit._0140_content;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code pages}はUA予約カウンタ(css-page-3 §6.1)であり、著者の
 * {@code counter-reset}/{@code counter-increment}で上書きされずに
 * 正しい総ページ数のまま解決することを確認する。
 */
public class TotalPageCounterProtectedTest extends AbstractTestCase {
	public TotalPageCounterProtectedTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0140-content/total-page-counter-protected.html");
		this.session.property("processing.pass-count", "3");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
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
