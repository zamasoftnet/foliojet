package jp.cssj.test.unit._0530_leader;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * 目次の標準形 {@code leader(dotted) target-counter(attr(href), page)} の
 * 統合テストです(leader() L1/T1——consult-codex-2026-07-31-leader.txt)。
 * リーダーの反復ドットは論理テキストへ混入せず(単一空白のみ)、
 * ページ番号はpass-count機構で解決されること。
 */
public class LeaderTargetCounterTest extends AbstractTestCase {
	public LeaderTargetCounterTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0530-leader/target-counter.html");
		this.session.property("processing.page-references", "true");
		this.session.property("processing.pass-count", "3");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_aa(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("First 1", text.toString());
			return true;
		}
		return false;
	}

	public boolean check_bb(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			StringBuilder text = new StringBuilder();
			box.getText(text);
			assertEquals("Third 3", text.toString());
			return true;
		}
		return false;
	}
}
