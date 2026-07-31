package jp.cssj.test.unit._0500_grid;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * Grid G0のatomic契約テストです(consult-codex-2026-07-31-grid.txt §1.2)。
 * ページ残量に入らないGridコンテナは内部分割されず丸ごとpage 2へ移る。
 * G0時点の内容配置は単一列の通常フロー(トラック解決はG1以降)。
 */
public class GridAtomicTest extends AbstractTestCase {
	public GridAtomicTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0500-grid/atomic-move.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** Gridは分割されず丸ごと次ページへ(PageAtomicBox)。 */
	public boolean check_g(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals("the grid must move to page 2 as a whole", 2, pageNumber);
			final StringBuilder buff = new StringBuilder();
			box.getText(buff);
			final String text = buff.toString();
			assertTrue("all items must be present: " + text, text.contains("alpha") && text.contains("delta"));
			return true;
		}
		return false;
	}
}
