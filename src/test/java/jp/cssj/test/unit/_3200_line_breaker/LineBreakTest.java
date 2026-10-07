package jp.cssj.test.unit._3200_line_breaker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * Tests kinsoku (line-breaking rules) strictness for CSS {@code line-break} (css-text-3 §5.2)
 * (added on 2026-08-29).
 *
 * <p>
 * Place the target character at position 11 in 10 pt Japanese text with width 100 pt (10 characters
 * per line). If it is prohibited at line start, it moves to the next line with character 10,
 * leaving 9 characters on line 1. If the prohibition is relaxed, line 1 has 10 characters.
 * Read the first line's character count from the display list's first {@code Text[...]}.
 * </p>
 * <ul>
 * <li>{@code strict} (also the default {@code auto}): prolonged sound marks, small kana,
 * iteration marks, and middle dots cannot start a line.</li>
 * <li>{@code normal}: prolonged sound marks, small kana, and 〜 can start a line;
 * iteration marks and middle dots cannot.</li>
 * <li>{@code loose}: additionally, iteration marks, middle dots, ‐, and suffixes (％) can start a line,
 * and breaks are allowed immediately after prefixes (￥). Per the specification,
 * Japanese commas/periods (、。) cannot start a line even with loose.</li>
 * <li>{@code anywhere}: breaks even inside Latin words (strict overflows on one line).</li>
 * </ul>
 */
public class LineBreakTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	/**
	 * First-line character count (in a 10-character-wide box: 9 if character 11 is prohibited at line start,
	 * otherwise 10).
	 */
	private int firstLineLength(final String name, final String lineBreak, final String body) throws Exception {
		final String dump = this.render(name, lineBreak, body);
		final Matcher m = TEXT.matcher(dump);
		assertTrue(name + ": Text[] がありません:\n" + dump, m.find());
		return m.group(1).length();
	}

	private int lineCount(final String name, final String lineBreak, final String body) throws Exception {
		final String dump = this.render(name, lineBreak, body);
		final Matcher m = TEXT.matcher(dump);
		int count = 0;
		while (m.find()) {
			++count;
		}
		return count;
	}

	public void testProlongedSoundMark() throws Exception {
		final String body = "あいうえおかきくけこーさしすせそ";
		assertEquals(9, this.firstLineLength("strict-choon", "strict", body));
		assertEquals(9, this.firstLineLength("auto-choon", "auto", body));
		assertEquals(10, this.firstLineLength("normal-choon", "normal", body));
		assertEquals(10, this.firstLineLength("loose-choon", "loose", body));
	}

	public void testSmallKana() throws Exception {
		final String body = "あいうえおかきくけこっさしすせそ";
		assertEquals(9, this.firstLineLength("strict-small", "strict", body));
		assertEquals(10, this.firstLineLength("normal-small", "normal", body));
	}

	public void testIterationMark() throws Exception {
		final String body = "あいうえおかきくけこ々さしすせそ";
		assertEquals(9, this.firstLineLength("strict-iter", "strict", body));
		assertEquals(9, this.firstLineLength("normal-iter", "normal", body));
		assertEquals(10, this.firstLineLength("loose-iter", "loose", body));
	}

	public void testCenteredPunctuation() throws Exception {
		final String body = "あいうえおかきくけこ・さしすせそ";
		assertEquals(9, this.firstLineLength("strict-nakaguro", "strict", body));
		assertEquals(9, this.firstLineLength("normal-nakaguro", "normal", body));
		assertEquals(10, this.firstLineLength("loose-nakaguro", "loose", body));
	}

	public void testIdeographicCommaStaysForbidden() throws Exception {
		// Japanese commas/periods remain prohibited at line start even with loose (not in css-text-3's relaxation table).
		final String body = "あいうえおかきくけこ、さしすせそ";
		assertEquals(9, this.firstLineLength("strict-touten", "strict", body));
		assertEquals(9, this.firstLineLength("loose-touten", "loose", body));
	}

	public void testPrefixAndSuffix() throws Exception {
		// Suffix ％: may start a line only with loose.
		final String suffix = "あいうえおかきくけこ％さしすせそ";
		assertEquals(9, this.firstLineLength("normal-suffix", "normal", suffix));
		assertEquals(10, this.firstLineLength("loose-suffix", "loose", suffix));
		// Prefix $: if character 10 is $, strict/normal keep $12345 unbreakable and move $ to the next line with it;
		// loose allows a break immediately after $ (JLREQ rules already do not bind fullwidth ￥ to following text).
		final String prefix = "あいうえおかきくけ$12345";
		assertEquals(9, this.firstLineLength("normal-prefix", "normal", prefix));
		assertEquals(10, this.firstLineLength("loose-prefix", "loose", prefix));
	}

	public void testAnywhere() throws Exception {
		// A single Latin word cannot split and overflows on one line. anywhere wraps between characters.
		final String body = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";
		assertEquals(1, this.lineCount("strict-anywhere", "strict", body));
		assertTrue(this.lineCount("anywhere-anywhere", "anywhere", body) >= 2);
		// Can also break before punctuation (strict moves "け。" together, leaving 9 characters).
		final String punct = "あいうえおかきくけこ。さしすせそ";
		assertEquals(10, this.firstLineLength("anywhere-punct", "anywhere", punct));
	}

	public void testWordBreakCombination() throws Exception {
		// line-break relaxation also works with word-break: break-all (break-all preserves kinsoku
		// between CJK characters; keep-all does not split CJK sequences, so it is not a meaningful comparison).
		final String body = "あいうえおかきくけこーさしすせそ";
		assertEquals(10, this.firstLineLength("normal-breakall", "normal; word-break: break-all", body));
		assertEquals(9, this.firstLineLength("strict-breakall", "strict; word-break: break-all", body));
	}

	/**
	 * Even with word-break: break-all, halfwidth punctuation (! ? , . )) cannot start a line
	 * (2026-10-06, jigensha report). Breaks are allowed within Latin words.
	 */
	public void testBreakAllKeepsHalfWidthPunctuationOffLineStart() throws Exception {
		final String breakAll = "strict; word-break: break-all";
		assertEquals(9, this.firstLineLength("breakall-exclamation", breakAll, "あいうえおかきくけこ!?さしすせそ"));
		assertEquals(9, this.firstLineLength("breakall-comma", breakAll, "あいうえおかきくけこ, so"));
		assertEquals(9, this.firstLineLength("breakall-period-paren", breakAll, "あいうえおかきくけこ.) so"));
		final String word = "abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz";
		assertTrue(this.lineCount("breakall-latin", breakAll, word) >= 2);
	}

	private String render(final String name, final String lineBreak, final String body) throws Exception {
		final File dir = new File("local/unittest/line-break");
		dir.mkdirs();
		final File source = new File(dir, name + ".html");
		Files.writeString(source.toPath(), document(lineBreak, body), StandardCharsets.UTF_8);

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
		final List<String> texts = new ArrayList<>();
		for (final File page : pages) {
			texts.add(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return String.join("\n", texts);
	}

	private static String document(final String lineBreak, final String body) {
		return "<?jp.cssj.property name=\"output.page-width\" value=\"200pt\"?>\n"
				+ "<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n"
				+ "<html lang=\"ja\">\n<head>\n"
				+ "<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n"
				+ "<title>line-break</title>\n"
				+ "<style type=\"text/css\">\n"
				+ "@page { margin: 0; }\n"
				+ "body { margin: 0; font-size: 10pt; line-height: 1.2; }\n"
				+ "p { margin: 0; width: 100pt; line-break: " + lineBreak + "; }\n"
				+ "</style>\n</head>\n<body>\n<p>" + body + "</p>\n</body>\n</html>\n";
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
