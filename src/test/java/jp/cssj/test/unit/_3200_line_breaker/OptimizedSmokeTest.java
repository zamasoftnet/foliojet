package jp.cssj.test.unit._3200_line_breaker;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * CSS {@code text-wrap-style: pretty}(Knuth-Plass行分割、M3c増分3)の
 * smokeテストです。欧文・和文justify・text-indent・明示改行・空行・
 * float横の段落(フォールバック経路)を含む文書が、例外なく変換完了
 * することを確認します。オプトインはfixture側のCSSで与えます
 * (2026-07-25、独自プロパティ{@code text.line-breaker}から移行)。
 */
public class OptimizedSmokeTest extends AbstractTestCase {
	public OptimizedSmokeTest(String name) {
		super(name);
	}

	protected void transcode() throws Exception {
		File file = new File("files/unittest/3200-line-breaker/optimized.html");
		CTISessionHelper.transcodeFile(this.session, file, "text/html", null);
	}
}
