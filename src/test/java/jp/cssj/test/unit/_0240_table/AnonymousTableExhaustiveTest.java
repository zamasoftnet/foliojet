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
 * Small-scope exhaustive check of the anonymous table boxes (CSS 2.1 §17.2.1), opt-in with
 * {@code -Dfoliojet.anonTableExhaustive} (2026-10-26, design-pattern review: the insertion and the closing of the
 * anonymous boxes are written as separate tables, and they disagreed once, 2026-10-09).
 *
 * <p>
 * Every parent display × every sequence of up to three children of the listed displays, each child holding one
 * token, must convert without an exception and draw every token exactly once. Tokens in table-column and
 * table-column-group boxes are not expected (browsers do not draw them; Copper does, a known limitation) and only
 * must not be drawn twice. It does not judge where the tokens go; the 36M sweep and the display-list goldens look at
 * the layout. The first run found two header (or footer) groups losing the first one's content (82 cases).
 * </p>
 */
public class AnonymousTableExhaustiveTest extends TestCase {
	private static final String[] PARENTS = { "table", "table-row-group", "table-row", "block", "inline" };

	private static final String[] CHILDREN = { "table", "inline-table", "table-row-group", "table-header-group",
			"table-footer-group", "table-row", "table-cell", "table-column", "table-column-group", "table-caption",
			"block", "inline", "inline-block", "flex" };

	private static final int MAX_CHILDREN = 3;

	private static final Pattern TOKEN = Pattern.compile("Text\\[\"(T[0-9]+)\"");

	public void testEverySmallTree() throws Exception {
		if (System.getProperty("foliojet.anonTableExhaustive") == null) {
			return;
		}
		final List<String> failures = new ArrayList<>();
		int cases = 0;
		for (final String parent : PARENTS) {
			for (int count = 1; count <= MAX_CHILDREN; ++count) {
				final int[] index = new int[count];
				do {
					++cases;
					final String failure = check(parent, index);
					if (failure != null) {
						failures.add(failure);
					}
				} while (next(index));
			}
		}
		System.out.println("[anon-table] cases=" + cases + " failures=" + failures.size());
		final File report = new File("local/anon-table-failures.txt");
		report.getParentFile().mkdirs();
		Files.write(report.toPath(), failures, StandardCharsets.UTF_8);
		assertTrue(failures.size() + " / " + cases + " cases failed:\n"
				+ String.join("\n", failures.subList(0, Math.min(failures.size(), 40))), failures.isEmpty());
	}

	/** The next sequence of child displays (odometer order); false after the last. */
	private static boolean next(final int[] index) {
		for (int i = index.length - 1; i >= 0; --i) {
			if (++index[i] < CHILDREN.length) {
				return true;
			}
			index[i] = 0;
		}
		return false;
	}

	/** Converts one case; returns null when it holds, or a description of the failure. */
	private static String check(final String parent, final int[] index) {
		final StringBuilder body = new StringBuilder("<div style=\"display:" + parent + "\">");
		final List<String> expected = new ArrayList<>();
		final StringBuilder label = new StringBuilder(parent).append(" >");
		for (int i = 0; i < index.length; ++i) {
			final String display = CHILDREN[index[i]];
			final String token = "T" + (i + 1);
			body.append("<div style=\"display:").append(display).append("\">").append(token).append("</div>");
			if (!display.equals("table-column") && !display.equals("table-column-group")) {
				expected.add(token);
			}
			label.append(' ').append(display);
		}
		body.append("</div>");
		try {
			final List<String> drawn = drawn(body.toString());
			// Every expected token once. Tokens of column boxes are tolerated: browsers do not draw them, Copper does
			// (a known limitation; only CSS can put text in a column).
			for (final String token : expected) {
				if (java.util.Collections.frequency(drawn, token) != 1) {
					return label + ": drawn " + drawn + ", expected " + expected;
				}
			}
			for (final String token : drawn) {
				if (!expected.contains(token) && java.util.Collections.frequency(drawn, token) != 1) {
					return label + ": drawn " + drawn + ", expected " + expected;
				}
			}
			return null;
		} catch (final Exception | Error e) {
			return label + ": " + e;
		}
	}

	/** The tokens drawn, in display-list order. */
	private static List<String> drawn(final String body) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 400pt 600pt; margin: 20pt } body { margin: 0; font: 10pt serif }"
				+ "</style></head><body>" + body + "</body></html>";
		final File dir = Files.createTempDirectory("anon-table").toFile();
		try {
			final File input = new File(dir, "input.html");
			Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
			try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null); ByteArrayOutputStream out = new ByteArrayOutputStream();
					AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				CTISessionHelper.transcodeFile(session, input, "text/html", null);
			}
			final List<String> drawn = new ArrayList<>();
			for (int page = 1;; ++page) {
				final File f = new File(dir, String.format("page-%04d.txt", page));
				if (!f.exists()) {
					break;
				}
				final Matcher m = TOKEN.matcher(Files.readString(f.toPath(), StandardCharsets.UTF_8));
				while (m.find()) {
					drawn.add(m.group(1));
				}
			}
			return drawn;
		} finally {
			final File[] files = dir.listFiles();
			if (files != null) {
				for (final File f : files) {
					f.delete();
				}
			}
			dir.delete();
		}
	}
}
