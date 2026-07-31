package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 脚注F6(1脚注のページ跨ぎ分割)のテストです
 * (consult-codex-2026-07-31-footnote-f6f7.txt §2)。55行の脚注は
 * 最大脚注領域(≈749.9pt)を超える。分割予約はcallが確定済み(committed)の
 * entryに限る(同一ページ予約は本文圧縮→call押し出しの循環を生む)ため、
 * page 1はcallのみ(丸ごと送り=F4)、前半(marker付き)がpage 2、
 * 後半(markerなし継続)がpage 3となる。
 */
public class FootnoteSplitTest extends AbstractTestCase {
	public FootnoteSplitTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0125-footnote/footnote-split.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** 断片は2つ訪問される: 前半(marker付き、page 1)と後半(page 2)。 */
	public boolean check_sp(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.BLOCK) {
			return false;
		}
		final StringBuilder buff = new StringBuilder();
		box.getText(buff);
		final String text = buff.toString();
		if (text.startsWith("1. line001")) {
			// 前半: marker付きの先頭断片(callの次のnote-onlyページ)
			assertEquals("the head fragment must sit on the first note page", 2, pageNumber);
			assertFalse("the head must not contain the tail's last line", text.contains("line055"));
		} else {
			// 後半: markerを持たない継続断片がさらに次ページへ
			assertEquals("the tail fragment must continue on page 3", 3, pageNumber);
			assertTrue("the tail must carry the remaining lines: " + text.substring(0, Math.min(24, text.length())),
					text.contains("line055"));
			assertFalse("the marker must not be repeated on the tail", text.startsWith("1. "));
		}
		return true;
	}
}
