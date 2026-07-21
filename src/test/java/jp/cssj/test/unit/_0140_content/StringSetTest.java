package jp.cssj.test.unit._0140_content;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code string-set: name content();}(要素自身の描画テキストを捕捉)が
 * ページを跨いで正しく{@code string()}へ反映されることを検証する。
 * {@code content()}は要素のボックスが確定するdraw時まで解決されない
 * ため、同じページ内の後続要素からの参照は検証対象にしない
 * ({@code StringSetCounterTest}でbuild時解決のみのケースを網羅的に
 * 検証する)。
 */
public class StringSetTest extends AbstractTestCase {
	public StringSetTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0140-content/string-set.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_fa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("Alpha", text.toString());
			return true;
		}
		return false;
	}

	public boolean check_fb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("Beta", text.toString());
			return true;
		}
		return false;
	}

	public boolean check_fc(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("H2:Gamma", text.toString());
			return true;
		}
		return false;
	}
}
