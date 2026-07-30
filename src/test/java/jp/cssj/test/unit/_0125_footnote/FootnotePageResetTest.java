package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 脚注F5(ページ毎の再採番)のテストです
 * (consult-codex-2026-07-31-footnote-f5.txt F5-d fixture 1)。番号は
 * 文書通番ではなく「callが残ったページ」ごとに1から——page 1が[1,2]、
 * page 2も[1,2]になることを固定する。
 */
public class FootnotePageResetTest extends AbstractTestCase {
	public FootnotePageResetTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0125-footnote/footnote-pagereset.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	private boolean checkNote(IBox box, int pageNumber, int expectedPage, String expectedPrefix) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		assertEquals(expectedPage, pageNumber);
		final StringBuilder buff = new StringBuilder();
		box.getText(buff);
		assertTrue("note text must start with \"" + expectedPrefix + "\": " + buff,
				buff.toString().startsWith(expectedPrefix));
		return true;
	}

	public boolean check_a1(IBox box, int pageNumber, double x, double y) {
		return this.checkNote(box, pageNumber, 1, "1. note a1");
	}

	public boolean check_a2(IBox box, int pageNumber, double x, double y) {
		return this.checkNote(box, pageNumber, 1, "2. note a2");
	}

	/** 2ページ目の最初の脚注は文書通番3ではなくページローカルの1。 */
	public boolean check_b1(IBox box, int pageNumber, double x, double y) {
		return this.checkNote(box, pageNumber, 2, "1. note b1");
	}

	public boolean check_b2(IBox box, int pageNumber, double x, double y) {
		return this.checkNote(box, pageNumber, 2, "2. note b2");
	}
}
