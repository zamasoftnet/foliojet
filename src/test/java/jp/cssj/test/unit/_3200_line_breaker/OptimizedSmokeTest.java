package jp.cssj.test.unit._3200_line_breaker;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * {@code text.line-breaker=optimized}(Knuth-Plass行分割、M3c増分3)の
 * smokeテストです。欧文・和文justify・text-indent・明示改行・空行・
 * float横の段落(フォールバック経路)を含む文書が、例外なく変換完了
 * することを確認します。
 */
public class OptimizedSmokeTest extends AbstractTestCase {
	public OptimizedSmokeTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		this.session.property("text.line-breaker", "optimized");
		File file = new File("files/unittest/3200-line-breaker/optimized.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}
}
