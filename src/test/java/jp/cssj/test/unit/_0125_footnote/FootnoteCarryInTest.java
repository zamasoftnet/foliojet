package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 脚注F4(容量送り=carry-in)のテストです
 * (consult-codex-2026-07-31-footnote-f4.txt 検証fixture 3)。400ptの脚注
 * 2件は合計が最大脚注領域(≈755.9pt)を超えるため、両callがpage 1に
 * あってもnote 2はFIFOのままpage 2へ送られ、page 2にcallが無くても
 * 最優先で配置される(「call数だけ先頭からattach」が誤りであることの
 * 固定)。page 2はnote-onlyページ(EOF送り経路)。
 */
public class FootnoteCarryInTest extends AbstractTestCase {
	public FootnoteCarryInTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0125-footnote/footnote-carryin.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_n1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals("note 1 must stay on the calling page", 1, pageNumber);
			return true;
		}
		return false;
	}

	/** 累積超過は例外でなくFIFO送り(callの無いページへのcarry-in配置)。 */
	public boolean check_n2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertEquals("note 2 must be carried to the next page", 2, pageNumber);
			return true;
		}
		return false;
	}
}
