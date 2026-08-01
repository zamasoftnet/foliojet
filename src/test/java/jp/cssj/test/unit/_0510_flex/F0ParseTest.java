package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Flex F0a(consult-codex-2026-08-02-flexbox.txt): display:flexが
 * パースされ、レイアウト未配線の間は通常ブロックへ縮退して内容が
 * 失われないことの回帰テストです。F0bでatomic化されても
 * 「全itemが出力される」ことは不変条件のまま。
 */
public class F0ParseTest extends AbstractTestCase {
	public F0ParseTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/f0-parse.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		assertEquals(1, pageNumber);
		return box.getType() == BoxType.BLOCK;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		assertEquals(1, pageNumber);
		return box.getType() == BoxType.BLOCK;
	}

	public boolean check_after(IBox box, int pageNumber, double x, double y) {
		assertEquals(1, pageNumber);
		return box.getType() == BoxType.BLOCK;
	}
}
