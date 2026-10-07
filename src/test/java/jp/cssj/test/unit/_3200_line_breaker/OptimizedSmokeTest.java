package jp.cssj.test.unit._3200_line_breaker;

import java.io.File;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Smoke test for CSS {@code text-wrap-style: pretty} (Knuth-Plass line breaking, M3c increment 3).
 * Verifies that a document containing Latin text, justified Japanese text, text-indent, explicit
 * line breaks, blank lines, and paragraphs beside floats (fallback path) completes conversion
 * without exceptions. Opt-in is provided by fixture CSS
 * (migrated from the proprietary {@code text.line-breaker} property on 2026-07-25).
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
