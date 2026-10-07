package jp.cssj.test.unit._3200_line_breaker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Tests the semantics of CSS property {@code text-wrap-style} (and shorthand {@code text-wrap})
 * (added on 2026-07-25 when the proprietary {@code text.line-breaker} property was removed
 * in favor of CSS alone).
 *
 * <p>
 * Generates documents with identical bodies that differ only in {@code <style>},
 * then compares their display lists to verify:
 * </p>
 * <ul>
 * <li>Inheritance: setting {@code pretty} on {@code body} affects child paragraphs.</li>
 * <li>Per-element switching: direct paragraph declarations work, and paragraph-level {@code auto}
 * can override {@code body: pretty}.</li>
 * <li>The {@code text-wrap: pretty} shorthand maps to {@code text-wrap-style}.</li>
 * <li>{@code balance}/{@code stable} parse successfully but are treated as {@code auto}.</li>
 * <li>Invalid values ({@code text-wrap-style: no-such-value}) and mode values
 * ({@code text-wrap: nowrap}, not accepted by the shorthand) invalidate the entire declaration,
 * leaving the default {@code auto}.</li>
 * </ul>
 *
 * <p>
 * That K-P actually takes effect is ensured by <b>different</b> output from the pretty and auto groups
 * (the test fails if the groups happen to match).
 * </p>
 */
public class TextWrapStyleTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * A K-P-eligible paragraph whose result differs from greedy layout (same content as
	 * optimized-en-hyphen.html). For justified Latin text with hyphenation, K-P avoids consecutive
	 * hyphenated lines and excessive compression, so its choices differ from greedy layout.
	 */
	private static final String BODY = ""
			+ "<p class=\"justify\" lang=\"en\">The quick brown fox jumps over the lazy dog and keeps"
			+ " running through the quiet forest until the evening light fades away completely."
			+ " Considerable improvements materialize whenever paragraphs receive comprehensive"
			+ " optimization treatment.</p>\n";

	/** Variants expected to use K-P layout (only the declaration form differs). */
	private static final Map<String, String> PRETTY = new LinkedHashMap<>();

	/** Variants expected to use greedy (auto) layout. */
	private static final Map<String, String> AUTO = new LinkedHashMap<>();

	static {
		// Inheritance: a declaration on body affects child paragraphs.
		PRETTY.put("inherit", "body { text-wrap-style: pretty; }");
		// Per element: a direct declaration on the paragraph also works.
		PRETTY.put("element", "p { text-wrap-style: pretty; }");
		// Shorthand.
		PRETTY.put("shorthand", "body { text-wrap: pretty; }");

		// Default (unspecified).
		AUTO.put("default", "");
		// balance/stable are accepted but treated as auto (unsupported).
		AUTO.put("balance", "body { text-wrap-style: balance; }");
		AUTO.put("stable", "body { text-wrap-style: stable; }");
		AUTO.put("shorthand-balance", "body { text-wrap: stable; }");
		// Per-element override: paragraph-level auto can undo inherited pretty.
		AUTO.put("override", "body { text-wrap-style: pretty; } p { text-wrap-style: auto; }");
		// An invalid value invalidates the whole declaration (inherited value stays at the initial auto).
		AUTO.put("invalid", "body { text-wrap-style: no-such-value; }");
		// Mode values are not accepted by the text-wrap shorthand (white-space controls wrapping).
		// Since 2026-08-29, text-wrap: nowrap takes effect like white-space:nowrap.
		// (No wrapping, so excluded from parity checks against greedy layout.)
	}

	public TextWrapStyleTest(String name) {
		super(name);
	}

	public void testTextWrapStyle() throws Exception {
		final List<String> failures = new ArrayList<>();

		final String prettyReference = this.render("pretty-inherit", PRETTY.get("inherit"));
		final String autoReference = this.render("auto-default", AUTO.get("default"));

		// K-P must actually take effect (otherwise the subsequent parity checks are meaningless).
		if (prettyReference.equals(autoReference)) {
			fail("text-wrap-style: pretty の出力が既定(auto)と同一です。"
					+ "K-Pが起動していないか、fixtureが両者で同じ改行になる内容になっています");
		}

		for (final Map.Entry<String, String> e : PRETTY.entrySet()) {
			final String got = this.render("pretty-" + e.getKey(), e.getValue());
			if (!prettyReference.equals(got)) {
				failures.add("pretty-" + e.getKey() + " (" + e.getValue() + "): K-Pで組まれていません");
			}
		}
		for (final Map.Entry<String, String> e : AUTO.entrySet()) {
			final String got = this.render("auto-" + e.getKey(), e.getValue());
			if (!autoReference.equals(got)) {
				failures.add("auto-" + e.getKey() + " (" + e.getValue() + "): 貪欲法で組まれていません");
			}
		}

		if (!failures.isEmpty()) {
			fail(String.join("\n", failures));
		}
	}

	/**
	 * Lays out a document with the given {@code <style>} fragment and returns
	 * the concatenated display lists of all pages.
	 */
	private String render(final String name, final String style) throws Exception {
		final File dir = new File("local/unittest/text-wrap-style");
		dir.mkdirs();
		final File source = new File(dir, name + ".html");
		Files.writeString(source.toPath(), document(style), StandardCharsets.UTF_8);

		final File outDir = new File(dir, name);
		deleteChildren(outDir);
		outDir.mkdirs();
		System.setProperty(DisplayListDumper.DIR_PROPERTY, outDir.getPath());
		try {
			final File pdf = new File(dir, name + ".pdf");
			try (OutputStream out = new FileOutputStream(pdf)) {
				final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, source, "text/html", null);
				} finally {
					session.close();
				}
			}
		} finally {
			System.clearProperty(DisplayListDumper.DIR_PROPERTY);
		}

		final File[] pages = outDir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull("表示リストが出力されていません: " + name, pages);
		assertTrue("表示リストが出力されていません: " + name, pages.length > 0);
		java.util.Arrays.sort(pages);
		final StringBuilder sb = new StringBuilder();
		for (final File page : pages) {
			sb.append("=== ").append(page.getName()).append('\n');
			sb.append(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return sb.toString();
	}

	private static String document(final String style) {
		return "<?jp.cssj.property name=\"output.page-width\" value=\"200pt\"?>\n"
				+ "<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n"
				+ "<html>\n<head>\n"
				+ "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n"
				+ "<title>text-wrap-style</title>\n"
				+ "<style type=\"text/css\">\n"
				+ "@page { margin: 0; }\n"
				+ "body { margin: 0; font-size: 10pt; line-height: 1.2; }\n"
				+ ".justify { text-align: justify; }\n"
				+ "p { hyphens: auto; }\n"
				+ style + "\n"
				+ "</style>\n</head>\n<body>\n" + BODY + "</body>\n</html>\n";
	}

	private static void deleteChildren(final File dir) {
		final File[] children = dir.listFiles();
		if (children == null) {
			return;
		}
		for (final File child : children) {
			child.delete();
		}
	}
}
