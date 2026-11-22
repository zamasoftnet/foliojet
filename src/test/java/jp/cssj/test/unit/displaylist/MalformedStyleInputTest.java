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
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Style input that is not what a property or the CSS reader expects is ignored alone: the declaration, the
 * attribute or the rule, not the conversion. Each document here used to end the conversion with an unexpected error
 * ({@code ClassCastException}, {@code IllegalArgumentException}, the lexer's {@code TokenMgrException}) or lost its
 * whole style sheet.
 *
 * <p>
 * Each document has the text KEEP in the element with the malformed part and a paragraph AFTER behind it.
 * </p>
 */
public class MalformedStyleInputTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	/** font-family takes no attr(): the declaration is invalid. */
	public void testFontFamilyAttr() throws Exception {
		assertKeepAndAfter("<p data-font=\"serif\" style=\"font-family:attr(data-font)\">KEEP</p><p>AFTER</p>");
		assertKeepAndAfter("<p style=\"font-family:attr(data-font);font-weight:bold\">KEEP</p><p>AFTER</p>");
	}

	/** The same in @font-face, and the rules after it are still read. */
	public void testFontFaceWithAttr() throws Exception {
		final List<String> texts = convert("<style>@font-face{font-family:attr(data-font);src:local(serif)}"
				+ " p.h{display:none}</style><p class=\"h\">HIDDEN</p><p>KEEP</p><p>AFTER</p>", null);
		assertTrue("KEEP: " + texts, texts.contains("KEEP"));
		assertFalse("the rule after the @font-face applies: " + texts, texts.contains("HIDDEN"));
	}

	/** A font-weight attribute between two of the nine weights. */
	public void testFontWeightAttribute() throws Exception {
		assertKeepAndAfter("<font font-weight=\"450\">KEEP</font><p>AFTER</p>");
		assertKeepAndAfter("<font font-weight=\"1\">KEEP</font><font font-weight=\"999999\">b</font>"
				+ "<font font-weight=\"-450\">c</font><font font-weight=\"x\">d</font><p>AFTER</p>");
	}

	/**
	 * A style attribute that ends in a comment left open after an unclosed url(: the attribute is reported as bad
	 * inline CSS and the paragraph is printed.
	 */
	public void testInlineCommentAfterUnclosedUrl() throws Exception {
		final List<Short> codes = new ArrayList<>();
		final List<String> texts = convert("<p style=\"background:url(/*\">KEEP</p><p>AFTER</p>", codes);
		assertTrue("KEEP: " + texts, texts.contains("KEEP"));
		assertTrue("AFTER: " + texts, texts.contains("AFTER"));
		assertTrue("the bad inline CSS is reported: " + codes,
				codes.contains(Short.valueOf(MessageCodes.WARN_BAD_INLINE_CSS)));
	}

	/** The same in a style sheet: the rules before the broken one apply. */
	public void testSheetCommentAfterUnclosedUrl() throws Exception {
		final List<String> texts = convert("<style>p.h{display:none} a{background:url(x.png /* c</style>"
				+ "<p class=\"h\">HIDDEN</p><p>KEEP</p><p>AFTER</p>", null);
		assertTrue("KEEP: " + texts, texts.contains("KEEP"));
		assertTrue("AFTER: " + texts, texts.contains("AFTER"));
		assertFalse("the rule before the broken one applies: " + texts, texts.contains("HIDDEN"));
	}

	private static void assertKeepAndAfter(final String body) throws Exception {
		final List<String> texts = convert(body, null);
		assertTrue("KEEP is on the page: " + body + " -> " + texts, texts.contains("KEEP"));
		assertTrue("AFTER is on the page: " + body + " -> " + texts, texts.contains("AFTER"));
	}

	/**
	 * Converts the document and returns the texts drawn, in the order of the display lists of its pages.
	 *
	 * @param codes receives the codes of the messages of the conversion, if not null
	 */
	private static List<String> convert(final String body, final List<Short> codes) throws Exception {
		final File dir = Files.createTempDirectory("malformed-style").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html>" + body, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			if (codes != null) {
				session.setMessageHandler((code, args, mes) -> codes.add(Short.valueOf(code)));
			}
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
