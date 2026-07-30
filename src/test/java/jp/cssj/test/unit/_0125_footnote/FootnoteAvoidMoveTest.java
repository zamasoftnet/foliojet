package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 脚注F4(call移動検出)のテストです
 * (consult-codex-2026-07-31-footnote-f4.txt 検証fixture 2)。
 * page-break-inside:avoidのブロックは脚注予約前なら残量(75.9pt)に
 * 収まるが予約(≈21pt)後は収まらないため丸ごとpage 2へ移る。callが
 * page 1に残らないことを確定木の走査で検出し、noteもpage 2へ送られる
 * (page 1の予約は返さない=保守的確保)。
 */
public class FootnoteAvoidMoveTest extends AbstractTestCase {
	public FootnoteAvoidMoveTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0125-footnote/footnote-avoidmove.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	/** callと共にnoteもpage 2へ(callの無いページに孤立させない)。 */
	public boolean check_mv(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals("the note must follow its call to page 2", 2, pageNumber);
			return true;
		}
		return false;
	}
}
