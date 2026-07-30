package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 脚注F3(本文短縮)のテストです。脚注の予約(高さ+gap)が有効ページ容量
 * ({@code RootBuilder.getPageLimit()})を縮め、脚注が無ければ1ページに
 * 収まる本文が2ページへ割れることを固定する。53行×14.4pt=763.2ptは
 * A4版面(≈775.9pt)には収まるが、脚注1件の予約(≈21pt)を引いた容量には
 * 収まらない。
 */
public class FootnotePageLimitTest extends AbstractTestCase {
	public FootnotePageLimitTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0125-footnote/footnote-pagelimit.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** 脚注本文は呼び出しのあるページ(1ページ目)の下端へ。 */
	public boolean check_fnp(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals("the note must sit on the calling page", 1, pageNumber);
			return true;
		}
		return false;
	}

	/** 最終行は予約に押し出されて2ページ目へ(脚注が無ければ1ページ)。 */
	public boolean check_last(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals("the last line must be pushed to page 2 by the reservation", 2, pageNumber);
			return true;
		}
		return false;
	}
}
