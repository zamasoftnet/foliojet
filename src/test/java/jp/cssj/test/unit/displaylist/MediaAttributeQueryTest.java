package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import junit.framework.TestCase;

/**
 * The {@code media} attribute of {@code link} and {@code style}, and the media of {@code @import}, are evaluated as
 * {@code @media} is (2026-11-25, a test with real web pages, copperpdf4 docs/history/2026-11-25-realweb-test.md).
 * Before, only the names of media types were looked for in the text: Toyo Keizai links its stylesheets with
 * {@code media="(min-width: 768px)"} and {@code media="(max-width: 767px)"}, neither applied and the article printed
 * without style; {@code not print} applied.
 */
public class MediaAttributeQueryTest extends TestCase {
	public void testMediaQueriesInAttributes() throws Exception {
		final File dir = new File("local/media-attribute-query-in");
		dir.mkdirs();
		final String[][] sheets = { { "a.css", ".a{display:none}" }, { "b.css", ".b{display:none}" },
				{ "c.css", ".c{display:none}" }, { "d.css", ".d{display:none}" }, { "e.css", ".e{display:none}" },
				{ "f.css", ".f{display:none}" }, { "g.css", ".g{display:none}" }, { "h.css", ".h{display:none}" } };
		for (final String[] s : sheets) {
			Files.writeString(new File(dir, s[0]).toPath(), s[1], StandardCharsets.UTF_8);
		}
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset='utf-8'>"
				// applies: features alone that hold, a type and features, no type with "only"
				+ "<link rel='stylesheet' href='a.css' media='(min-width: 1px)'>"
				+ "<link rel='stylesheet' href='b.css' media='print and (min-width: 1px)'>"
				+ "<link rel='stylesheet' href='c.css' media='screen, (orientation: portrait)'>"
				// does not apply: features that do not hold, another medium, a negated medium
				+ "<link rel='stylesheet' href='d.css' media='(max-width: 1px)'>"
				+ "<link rel='stylesheet' href='e.css' media='screen'>"
				+ "<link rel='stylesheet' href='f.css' media='not print'>"
				+ "<style media='(min-width: 1px)'>.s{display:none}</style>"
				+ "<style media='(max-width: 1px)'>.t{display:none}</style>"
				+ "<style>@import 'g.css' (min-width: 1px); @import 'h.css' (max-width: 1px);</style>"
				// a list that does not parse is not true; names separated by spaces, as older documents write them
				+ "<style media='not print and ('>.m{display:none}</style>"
				+ "<style media='tty print'>.n{display:none}</style>"
				+ "</head><body><p>BODY</p><p class='a'>HIDDEN-A</p><p class='b'>HIDDEN-B</p>"
				+ "<p class='c'>HIDDEN-C</p><p class='d'>D</p><p class='e'>E</p><p class='f'>F</p>"
				+ "<p class='s'>HIDDEN-S</p><p class='t'>T</p><p class='g'>HIDDEN-G</p><p class='h'>H</p>"
				+ "<p class='m'>M</p><p class='n'>HIDDEN-N</p></body></html>",
				StandardCharsets.UTF_8);
		final Map<String, double[]> texts = EndTagReopenTest.texts(input.getPath(), "media-attribute-query");
		for (final String shown : new String[] { "BODY", "D", "E", "F", "T", "H", "M" }) {
			assertTrue(shown + " is not drawn: " + texts.keySet(), texts.containsKey(shown));
		}
		for (final String hidden : new String[] { "HIDDEN-A", "HIDDEN-B", "HIDDEN-C", "HIDDEN-S", "HIDDEN-G",
				"HIDDEN-N" }) {
			assertFalse(hidden + " is drawn", texts.containsKey(hidden));
		}
	}
}
