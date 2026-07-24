package jp.cssj.test.unit._0390_writing_mode;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * 縦書きでブロック({@code div#a})とインライン({@code span#b})が
 * ページをまたぐときの幾何です。
 *
 * <p>
 * 2026-07-25: ルビが注釈付きテキストになり(仕様裁定
 * docs/history/2026-07-25-ruby-annotation-spec-decision.md)、ルビを
 * 含む行が行送りを広げなくなったため文書全体が詰まり、期待値が
 * 陳腐化した。{@code span#b}がページ境界をまたぐという本テストの
 * 主旨を保つため、フィクスチャの{@code span#b}直前に2行分の地の文を
 * 足して再基準化した。またぎ位置の直前にはルビ単位(曲者/くせもの)が
 * あり、切断段落の再開位置がルビ単位の途中へ落ちないこと(単位の
 * ソース終端で再開すること)も同時に押さえている。
 * </p>
 */
public class FlowInlinePagebreakTest extends AbstractTestCase {
	public FlowInlinePagebreakTest(String name) {
		super(name);
	}

	/** {@code span#b}の断片の出現順です(文書順)。 */
	private int bFragment = 0;

	protected void transcode() throws Exception {
		File file = new File(
				"files/unittest/0390-writing-mode/flow-inline-pagebreak.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	public boolean check_a(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			if (pageNumber == 1) {
				// 1ページ目の残り(行進行=右→左なので左端側)
				assertEquals(0, x, 0);
				assertEquals(6, y, 0);
				assertEquals(67, box.getWidth(), 1);
			} else if (pageNumber == 2) {
				// 2ページ目は丸ごと
				assertEquals(0, x, 0);
				assertEquals(6, y, 0);
				assertEquals(243, box.getWidth(), 1);
			} else if (pageNumber == 3) {
				// 3ページ目の頭(右端側)
				assertEquals(183, x, 1);
				assertEquals(6, y, 1);
				assertEquals(60, box.getWidth(), 1);
			}
			return true;
		}
		return false;
	}

	public boolean check_b(IBox box, int pageNumber, double x, double y) {
		if (box.getType() != BoxType.INLINE) {
			return false;
		}
		++this.bFragment;
		assertEquals(12, box.getWidth(), 0);
		switch (this.bFragment) {
		case 1:
			// 2ページ目の途中の行から始まる
			assertEquals(2, pageNumber);
			assertEquals(12, x, 1);
			assertEquals(116, y, 1);
			break;
		case 2:
			// 2ページ目の最終行(ここにルビ単位が乗り、行末で切れる)
			assertEquals(2, pageNumber);
			assertEquals(2, x, 1);
			assertEquals(16, y, 1);
			break;
		case 3:
			// 3ページ目の先頭行へ継続する
			assertEquals(3, pageNumber);
			assertEquals(232, x, 1);
			assertEquals(16, y, 1);
			break;
		default:
			fail("span#bの断片が想定より多い: " + this.bFragment);
		}
		return this.bFragment >= 3;
	}
}
