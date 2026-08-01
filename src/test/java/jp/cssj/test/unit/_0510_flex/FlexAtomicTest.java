package jp.cssj.test.unit._0510_flex;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Flex F0bのatomic契約テストです(consult-codex-2026-08-02-flexbox.txt)。
 * ページ残量に入らないFlexコンテナは内部分割されず丸ごとpage 2へ移る
 * (PageAtomicBox——css-flexbox-1 §10の断片化はinformativeのため非対応)。
 * F0時点の内容配置は単一列の通常フロー(行分割・伸縮はF1以降)。
 */
public class FlexAtomicTest extends AbstractTestCase {
	public FlexAtomicTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0510-flex/atomic-move.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** Flexは分割されず丸ごと次ページへ(PageAtomicBox)。 */
	public boolean check_f(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals("the flex container must move to page 2 as a whole", 2, pageNumber);
			final StringBuilder buff = new StringBuilder();
			box.getText(buff);
			final String text = buff.toString();
			assertTrue("all items must be present: " + text, text.contains("alpha") && text.contains("gamma"));
			return true;
		}
		return false;
	}
}
