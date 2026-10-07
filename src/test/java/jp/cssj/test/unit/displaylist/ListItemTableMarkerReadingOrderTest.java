package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import junit.framework.TestCase;

/**
 * Regression preventing outside list markers from entering a table's first cell (reduced seed 455).
 *
 * <p>When the first row crossed a page boundary, the old implementation left only the marker
 * in the first fragment and sent T11 to the next page, while retaining T12/T13 from the same row
 * in the first fragment.</p>
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
