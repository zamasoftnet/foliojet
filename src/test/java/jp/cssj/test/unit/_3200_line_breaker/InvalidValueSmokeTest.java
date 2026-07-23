package jp.cssj.test.unit._3200_line_breaker;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code text.line-breaker}に不正な値を渡した場合、legacyとして扱われ
 * 例外なく変換完了することを確認します(M3c増分3)。
 */
public class InvalidValueSmokeTest extends AbstractTestCase {
	public InvalidValueSmokeTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		this.session.property("text.line-breaker", "no-such-strategy");
		File file = new File("files/unittest/3200-line-breaker/optimized.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}
}
