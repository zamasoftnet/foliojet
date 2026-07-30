package jp.cssj.test.unit._0125_footnote;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * 脚注F1(call/marker合成と文書通番)のテストです
 * (consult-codex-2026-07-31-footnote.txt §5)。F3の配線までは本文は
 * その場に描かれるため、ここで固定するのは「本文先頭の::footnote-marker
 * (番号+区切り)」と「番号が文書通番で進むこと」。::footnote-callは
 * 親のインライン流に出るため本文ボックスのテキストには含まれない。
 */
public class FootnoteCallMarkerTest extends AbstractTestCase {
	public FootnoteCallMarkerTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0125-footnote/footnote-f1.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	private static String text(IBox box) {
		final StringBuilder buff = new StringBuilder();
		box.getText(buff);
		return buff.toString();
	}

	/** float:footnoteはblock化され、本文頭にmarker(番号+区切り)が付く。 */
	public boolean check_fn1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			final String text = text(box);
			assertTrue("marker must prefix the note body: " + text, text.startsWith("1. first note"));
			// F3: 本文はページ下端の脚注領域へ移る(3件が文書順に積まれる)
			assertEquals(724.87, y, 1);
			return true;
		}
		return false;
	}

	/** 番号は文書通番で進む。 */
	public boolean check_fn2(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			final String text = text(box);
			assertTrue("second note must be numbered 2: " + text, text.startsWith("2. second note"));
			assertEquals(739.88, y, 1);
			return true;
		}
		return false;
	}

	public boolean check_fn3(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			final String text = text(box);
			assertTrue("third note must be numbered 3: " + text, text.startsWith("3. third note"));
			assertEquals(754.88, y, 1);
			return true;
		}
		return false;
	}

	/** ::footnote-callは呼び出し位置(親のインライン流)に番号を残す。 */
	public boolean check_p1(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			final String text = text(box);
			assertTrue("call number must follow the reference text: " + text, text.startsWith("Alpha1"));
			assertTrue("call line must remain in the body flow: " + text, text.contains("beta."));
			assertTrue("the reference paragraph must stay near the page top: y=" + y, y < 100);
			return true;
		}
		return false;
	}

	/** 利用者の::footnote-call規則(content: counter(footnote))が上書きする。 */
	public boolean check_p3(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			final String text = text(box);
			assertTrue("custom call content must apply: " + text, text.startsWith("Custom[3]"));
			return true;
		}
		return false;
	}
}
