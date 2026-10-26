package jp.cssj.test.unit._0240_table;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Only the first header and the first footer group are the table's header and footer; later ones are row groups in
 * document order (CSS 2.1 §17.2), as in Chrome (2026-10-26: the content of the earlier ones was lost, two
 * {@code <thead>} in HTML too; found by AnonymousTableExhaustiveTest). Both table builders are covered: fixed layout
 * streams the rows (IncrementalTableBuilder), auto layout retains them (RetainedTableBuilder).
 */
public class RepeatedHeaderFooterTest extends TestCase {
	private static final Pattern TOKEN = Pattern.compile("Text\\[\"(T[0-9]+)\"");

	private static final String THEADS = "<thead><tr><td>T1</td></tr></thead><thead><tr><td>T2</td></tr></thead>"
			+ "<tbody><tr><td>T3</td></tr></tbody>";

	private static final String TFOOTS = "<tfoot><tr><td>T1</td></tr></tfoot><tfoot><tr><td>T2</td></tr></tfoot>"
			+ "<tbody><tr><td>T3</td></tr></tbody>";

	public void testTwoTheads() throws Exception {
		for (final String layout : new String[] { "auto", "fixed" }) {
			assertEquals(layout, "[T1, T2, T3]", drawn(layout, THEADS).toString());
		}
	}

	/** The first footer stays at the bottom; the second is a row group before the body that follows it. */
	public void testTwoTfoots() throws Exception {
		for (final String layout : new String[] { "auto", "fixed" }) {
			assertEquals(layout, "[T2, T3, T1]", drawn(layout, TFOOTS).toString());
		}
	}

	/** The tokens drawn, in display-list order (top to bottom here). */
	private static List<String> drawn(final String layout, final String rows) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 200pt 200pt; margin: 10pt } body { margin: 0; font: 10pt serif }"
				+ "table { table-layout: " + layout + "; width: 100pt }</style></head><body><table>" + rows
				+ "</table></body></html>";
		final File dir = Files.createTempDirectory("repeated-header").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final List<String> drawn = new ArrayList<>();
		final Matcher m = TOKEN.matcher(Files.readString(new File(dir, "page-0001.txt").toPath(), StandardCharsets.UTF_8));
		while (m.find()) {
			drawn.add(m.group(1));
		}
		return drawn;
	}
}
