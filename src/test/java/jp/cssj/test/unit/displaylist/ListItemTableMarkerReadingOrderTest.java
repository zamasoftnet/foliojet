package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * 外置きリストマーカーを表の第1セルへ混入させない回帰(seed 455の縮小形)。
 *
 * <p>先頭行がページ境界を跨ぐとき、旧実装はマーカーだけを先頭断片へ
 * 残してT11を次ページへ送り、同じ行のT12/T13を先頭断片へ残していた。</p>
 */
public class ListItemTableMarkerReadingOrderTest extends TestCase {
	public ListItemTableMarkerReadingOrderTest(final String name) {
		super(name);
	}

	public void testOutsideMarkerDoesNotEnterFirstTableCell() throws Exception {
		final File fixture = new File("files/fuzz-repro/vertical-table-first-cell-reading-order.html");
		final String html = Files.readString(fixture.toPath(), StandardCharsets.UTF_8);
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(html);
		assertNotNull(generated);
		RandomDocumentFuzzTest.checkDocument(generated,
				new File("build/fuzz-regressions/vertical-table-first-cell-reading-order.html"),
				new File("build/fuzz-regressions/vertical-table-first-cell-reading-order-dl"), true,
				"vertical-table-first-cell-reading-order-regression");
	}
}
