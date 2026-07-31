package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 脚注F7(縦書き)のテストです(consult-codex-2026-07-31-footnote-f6f7.txt
 * §3)。vertical-rl(300pt角ページ)では脚注領域は版面のblock-end=左端の
 * 列になる——noteのx座標が版面左寄り(予約領域内)にあり、本文は右端の
 * 行から始まることを固定する。番号ラベルは直立(限定縦中横、意図的
 * 仕様逸脱)。
 */
public class FootnoteVerticalTest extends AbstractTestCase {
	public FootnoteVerticalTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0125-footnote/footnote-vertical-rl.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** 縦書きでもmarker番号はページローカル(1)で、noteは左端側の領域へ。 */
	public boolean check_v1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(1, pageNumber);
			final StringBuilder buff = new StringBuilder();
			box.getText(buff);
			assertTrue("marker must carry number 1: " + buff, buff.toString().startsWith("1. "));
			assertTrue("the note must sit in the block-end (left) area: x=" + x, x < 100);
			return true;
		}
		return false;
	}

	public boolean check_v2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals(1, pageNumber);
			final StringBuilder buff = new StringBuilder();
			box.getText(buff);
			assertTrue("second marker must carry number 2: " + buff, buff.toString().startsWith("2. "));
			assertTrue("the note must sit in the block-end (left) area: x=" + x, x < 100);
			return true;
		}
		return false;
	}
}
