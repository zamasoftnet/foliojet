package jp.cssj.test.unit.displaylist;

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
import net.zamasoft.foliojet.css.util.GeneratedValueUtils;
import net.zamasoft.foliojet.css.value.ListStyleTypeValue;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * The built-in alphabetic counter styles go on past their last letter with two and more letters (Z, AA, AB, ...), as
 * the alphabetic system of CSS Counter Styles does. They printed a blank instead, so the 27th and later items of an
 * {@code upper-alpha} list had only the period as their label, and {@code counter(n, upper-alpha)} was empty.
 */
public class AlphabeticCounterTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	public void testUpperAlpha() {
		assertEquals("A", GeneratedValueUtils.format(1, ListStyleTypeValue.UPPER_ALPHA));
		assertEquals("Z", GeneratedValueUtils.format(26, ListStyleTypeValue.UPPER_ALPHA));
		assertEquals("AA", GeneratedValueUtils.format(27, ListStyleTypeValue.UPPER_ALPHA));
		assertEquals("AB", GeneratedValueUtils.format(28, ListStyleTypeValue.UPPER_ALPHA));
		assertEquals("AZ", GeneratedValueUtils.format(52, ListStyleTypeValue.UPPER_ALPHA));
		assertEquals("BA", GeneratedValueUtils.format(53, ListStyleTypeValue.UPPER_ALPHA));
		assertEquals("ZZ", GeneratedValueUtils.format(702, ListStyleTypeValue.UPPER_ALPHA));
		assertEquals("AAA", GeneratedValueUtils.format(703, ListStyleTypeValue.UPPER_ALPHA));
		assertEquals("FXSHRXW", GeneratedValueUtils.format(Integer.MAX_VALUE, ListStyleTypeValue.UPPER_ALPHA));
	}

	/** Every built-in style that counts with a list of letters: the letter after the last one is the first one twice. */
	public void testOtherAlphabeticStyles() {
		assertPastLastLetter(ListStyleTypeValue.LOWER_ALPHA, 26, "aa", "ab");
		assertPastLastLetter(ListStyleTypeValue.LOWER_LATIN, 26, "aa", "ab");
		assertPastLastLetter(ListStyleTypeValue.UPPER_LATIN, 26, "AA", "AB");
		assertPastLastLetter(ListStyleTypeValue.LOWER_GREEK, 24, "αα", "αβ");
		assertPastLastLetter(ListStyleTypeValue.HIRAGANA, 46, "ああ", "あい");
		assertPastLastLetter(ListStyleTypeValue.KATAKANA, 46, "アア", "アイ");
		assertPastLastLetter(ListStyleTypeValue.HIRAGANA_IROHA, 48, "いい", "いろ");
		assertPastLastLetter(ListStyleTypeValue.KATAKANA_IROHA, 48, "イイ", "イロ");
	}

	private static void assertPastLastLetter(final short style, final int letters, final String next,
			final String afterNext) {
		assertEquals("the last single letter of style " + style, 1,
				GeneratedValueUtils.format(letters, style).trim().length());
		assertEquals("style " + style, next, GeneratedValueUtils.format(letters + 1, style));
		assertEquals("style " + style, afterNext, GeneratedValueUtils.format(letters + 2, style));
		assertEquals("three letters of style " + style, 3,
				GeneratedValueUtils.format(letters * letters + letters + 1, style).length());
	}

	/** Zero and negative numbers, which an alphabetic system does not represent, print the blank they always did. */
	public void testZeroAndNegativeStayBlank() {
		assertEquals(" ", GeneratedValueUtils.format(0, ListStyleTypeValue.UPPER_ALPHA));
		assertEquals(" ", GeneratedValueUtils.format(-1, ListStyleTypeValue.LOWER_ALPHA));
		assertEquals(" ", GeneratedValueUtils.format(Integer.MIN_VALUE, ListStyleTypeValue.LOWER_GREEK));
		assertEquals("　", GeneratedValueUtils.format(0, ListStyleTypeValue.HIRAGANA));
		// And the numbers past the alphabet no longer do
		assertEquals("AA", GeneratedValueUtils.format(27, ListStyleTypeValue.UPPER_ALPHA));
	}

	/** The labels of a list that starts at its 26th item, and a counter in generated content. */
	public void testListLabelsAndCounter() throws Exception {
		final List<String> texts = texts("<style>ol{list-style-type:upper-alpha}p{counter-reset:n 27}"
				+ "p::before{content:\"[\" counter(n,upper-alpha) \"]\"}</style>"
				+ "<ol start=\"26\"><li>First</li><li>Second</li><li>Third</li></ol><p>Reference</p>");
		final List<String> trimmed = new ArrayList<>();
		for (final String text : texts) {
			trimmed.add(text.trim());
		}
		assertTrue("label of the 26th item: " + texts, trimmed.contains("Z."));
		assertTrue("label of the 27th item: " + texts, trimmed.contains("AA."));
		assertTrue("label of the 28th item: " + texts, trimmed.contains("AB."));
		assertTrue("counter(n, upper-alpha) of 27: " + texts, String.join("", texts).contains("[AA]"));
	}

	/** Converts the document and returns the texts drawn, in the order of the display lists of its pages. */
	private static List<String> texts(final String body) throws Exception {
		final File dir = Files.createTempDirectory("alphabetic-counter").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html>" + body, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final List<String> texts = new ArrayList<>();
		for (int i = 1;; ++i) {
			final File page = new File(dir, String.format("page-%04d.txt", i));
			if (!page.exists()) {
				break;
			}
			final Matcher m = TEXT.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				texts.add(m.group(1));
			}
		}
		return texts;
	}
}
