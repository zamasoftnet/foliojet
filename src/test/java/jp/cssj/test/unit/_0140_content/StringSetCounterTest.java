package jp.cssj.test.unit._0140_content;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code string-set}の値が文字列/カウンタのみ(build時に即座に解決)の
 * 場合の{@code string()}の4モード({@code first}/{@code last}/
 * {@code first-except}/{@code start})を、代入元と同じページからの
 * 参照も含めて検証する。book.cssのローマ数字/算用数字切替と同型
 * (#s1/#s2が代入元、その直後の同ページ参照と、間に代入の無いページを
 * 跨いだ参照の両方を見る)。
 */
public class StringSetCounterTest extends AbstractTestCase {
	public StringSetCounterTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/0140-content/string-set-counter.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}

	private void assertBlockText(IBox box, String expected) {
		StringBuilder text = new StringBuilder();
		box.getText(text);
		assertEquals(expected, text.toString());
	}

	// ページ1: #s1がv="one"を代入。同じページからの参照。
	public boolean check_p1first(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "one");
			return true;
		}
		return false;
	}

	public boolean check_p1last(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "one");
			return true;
		}
		return false;
	}

	// first-exceptは「まさに今のページで新規代入された」場合は空文字列。
	public boolean check_p1except(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "");
			return true;
		}
		return false;
	}

	// ページ2: このページでの代入は無い(前ページからentry valueを引き継ぐ)。
	public boolean check_p2first(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "one");
			return true;
		}
		return false;
	}

	public boolean check_p2last(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "one");
			return true;
		}
		return false;
	}

	// このページでの代入が無いので、first-exceptはfirstと同じ(空文字列にならない)。
	public boolean check_p2except(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "one");
			return true;
		}
		return false;
	}

	public boolean check_p2start(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "one");
			return true;
		}
		return false;
	}

	// ページ3: #s2がv="two"を代入。同じページからの参照。
	public boolean check_p3first(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "two");
			return true;
		}
		return false;
	}

	public boolean check_p3last(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "two");
			return true;
		}
		return false;
	}

	public boolean check_p3except(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "");
			return true;
		}
		return false;
	}

	// startは簡略化してentry value固定(このページの新規代入"two"は反映されない)。
	public boolean check_p3start(IBox box, int pageNumber, double x, double y) {
		if (box.getType() == BoxType.BLOCK) {
			assertBlockText(box, "one");
			return true;
		}
		return false;
	}
}
