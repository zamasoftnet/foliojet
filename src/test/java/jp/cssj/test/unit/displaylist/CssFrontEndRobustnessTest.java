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
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * One malformed attribute, declaration or rule does not end the conversion: it is ignored alone and the rest of the
 * document is laid out. Every document here used to abort the whole conversion with an unexpected error
 * (OutOfMemoryError, NullPointerException, IllegalArgumentException, IllegalStateException or ClassCastException).
 *
 * <p>
 * Each document has the text KEEP in or next to the element with the malformed part and a paragraph AFTER behind it:
 * the conversion finishes and both are on the page.
 * </p>
 */
public class CssFrontEndRobustnessTest extends TestCase {
	private static final Pattern TEXT = Pattern.compile("Text\\[\"([^\"]*)\"");

	/** A column span past the 1000 of the HTML Standard is clamped; the builders allocate an entry per column. */
	public void testColumnSpanIsClamped() throws Exception {
		assertKeepAndAfter("<table style=\"table-layout:fixed;width:100px\"><colgroup><col span=\"2147483647\">"
				+ "</colgroup><tr><td>KEEP</td></tr></table><p>AFTER</p>");
		assertKeepAndAfter("<table><colgroup><col span=\"2147483647\"></colgroup><tr><td>KEEP</td></tr></table>"
				+ "<p>AFTER</p>");
		assertKeepAndAfter("<table style=\"table-layout:fixed;width:100px\"><colgroup span=\"2147483647\">"
				+ "</colgroup><tr><td>KEEP</td></tr></table><p>AFTER</p>");
		assertKeepAndAfter("<table><colgroup span=\"2147483647\"></colgroup><tr><td>KEEP</td></tr></table>"
				+ "<p>AFTER</p>");
	}

	/** A length inside calc() where a number is expected: the transform declaration is invalid. */
	public void testLengthCalcAsNumber() throws Exception {
		assertDeclarationIgnored("transform:scale(calc(1px))");
		assertDeclarationIgnored("transform:matrix(calc(1px),0,0,1,0,0)");
		assertDeclarationIgnored("scale:calc(1px)");
	}

	/** A length inside calc() where an angle is expected. */
	public void testLengthCalcAsAngle() throws Exception {
		assertDeclarationIgnored("transform:rotate(calc(1px))");
		assertDeclarationIgnored("transform:skewX(calc(1px))");
		assertDeclarationIgnored("rotate:calc(1px)");
		assertDeclarationIgnored("background-image:conic-gradient(from calc(1px),red,blue)");
		assertDeclarationIgnored("background-image:linear-gradient(calc(1px),red,blue)");
		assertDeclarationIgnored("filter:hue-rotate(calc(1px))");
	}

	/**
	 * Nested selector lists that repeat their parent: thirty levels of {@code &, &} are one selector, not 2<sup>30</sup>.
	 * The innermost rule still applies.
	 */
	public void testRepeatedNestedSelectorList() throws Exception {
		final List<String> texts = texts("<style>.x{" + "&,&{".repeat(30) + "display:none;" + "}".repeat(31)
				+ "</style><p class=\"x\">HIDDEN</p><p>KEEP</p><p>AFTER</p>");
		assertTrue("KEEP: " + texts, texts.contains("KEEP"));
		assertTrue("AFTER: " + texts, texts.contains("AFTER"));
		assertFalse("the innermost nested rule hides its element: " + texts, texts.contains("HIDDEN"));
	}

	/** Nested selector lists that double the number of selectors at every level: the rule over the budget is dropped. */
	public void testGrowingNestedSelectorList() throws Exception {
		assertKeepAndAfter("<style>.x,.y{" + "&.a,&.b{".repeat(30) + "color:green;" + "}".repeat(31)
				+ "</style><p class=\"x\">KEEP</p><p>AFTER</p>");
	}

	/** Nested selectors that double the length of the selector at every level. */
	public void testGrowingNestedParentReference() throws Exception {
		assertKeepAndAfter("<style>.x{" + "& &{".repeat(30) + "color:green;" + "}".repeat(31)
				+ "</style><p class=\"x\">KEEP</p><p>AFTER</p>");
	}

	/** attr() with a unit that is no length unit, and with length units that are not absolute. */
	public void testAttrUnit() throws Exception {
		assertKeepAndAfter("<p data-w=\"1\" style=\"width:attr(data-w bogus);color:green\">KEEP</p><p>AFTER</p>");
		assertKeepAndAfter("<p data-w=\"1\" style=\"width:attr(data-w deg);color:green\">KEEP</p><p>AFTER</p>");
		assertKeepAndAfter("<p data-w=\"1\" style=\"width:attr(data-w vw);color:green\">KEEP</p><p>AFTER</p>");
		assertKeepAndAfter("<p data-w=\"1\" style=\"width:attr(data-w cap);color:green\">KEEP</p><p>AFTER</p>");
		assertKeepAndAfter("<p data-w=\"1\" style=\"width:attr(data-w rlh);color:green\">KEEP</p><p>AFTER</p>");
		assertKeepAndAfter("<p data-w=\"1\" style=\"width:attr(data-w cqw);color:green\">KEEP</p><p>AFTER</p>");
	}

	/** calc() that evaluates to an angle where a length is expected. */
	public void testAngleCalcAsLength() throws Exception {
		assertDeclarationIgnored("width:calc(1deg)");
		assertDeclarationIgnored("margin-left:calc(1deg);padding:calc(1deg);line-height:calc(1deg)");
		assertDeclarationIgnored("margin-left:calc(1deg)");
		assertDeclarationIgnored("padding:calc(1deg)");
		assertDeclarationIgnored("line-height:calc(1deg)");
		assertDeclarationIgnored("font:10pt/calc(1deg) serif");
	}

	/** calc() that is no length in a gap, which takes lengths only: an angle, and a percentage. */
	public void testNonLengthCalcAsGap() throws Exception {
		assertKeepAndAfter("<div style=\"display:flex;gap:calc(1deg)\"><p>KEEP</p></div><p>AFTER</p>");
		assertKeepAndAfter("<div style=\"display:flex;gap:calc(10%)\"><p>KEEP</p></div><p>AFTER</p>");
		assertKeepAndAfter("<div style=\"display:grid;row-gap:calc(10% + 5px)\"><p>KEEP</p></div><p>AFTER</p>");
		assertKeepAndAfter("<div style=\"column-count:2;column-gap:calc(1deg)\"><p>KEEP</p></div><p>AFTER</p>");
	}

	/** A color attr() that does not resolve, with no fallback: color inherits, background-color is transparent. */
	public void testUnresolvedColorAttr() throws Exception {
		assertKeepAndAfter("<p style=\"color:attr(data-ink type(color));font-weight:bold\">KEEP</p><p>AFTER</p>");
		assertKeepAndAfter("<p data-ink=\"invalid\" style=\"color:attr(data-ink type(color))\">KEEP</p><p>AFTER</p>");
		assertKeepAndAfter("<p style=\"background-color:attr(data-ink type(color))\">KEEP</p><p>AFTER</p>");
		// The same through the user agent style sheet, which maps the HTML attributes with attr()
		assertKeepAndAfter("<table><tr><td bgcolor=\"\">KEEP</td></tr></table><p>AFTER</p>");
		assertKeepAndAfter("<table bgcolor=\"no-color\"><tr><td>KEEP</td></tr></table><p>AFTER</p>");
		assertKeepAndAfter("<html><body text=\"no-color\"><p>KEEP</p><p>AFTER</p></body></html>");
		assertKeepAndAfter("<html><body bgcolor=\"\"><p>KEEP</p><p>AFTER</p></body></html>");
	}

	/** A paragraph KEEP with the declarations, followed by a paragraph AFTER. */
	private static void assertDeclarationIgnored(final String declarations) throws Exception {
		assertKeepAndAfter("<p style=\"" + declarations + ";color:green\">KEEP</p><p>AFTER</p>");
	}

	private static void assertKeepAndAfter(final String body) throws Exception {
		final List<String> texts = texts(body);
		assertTrue("KEEP is on the page: " + body + " -> " + texts, texts.contains("KEEP"));
		assertTrue("AFTER is on the page: " + body + " -> " + texts, texts.contains("AFTER"));
	}

	/** Converts the document and returns the texts drawn, in the order of the display lists of its pages. */
	private static List<String> texts(final String body) throws Exception {
		final File dir = Files.createTempDirectory("css-front-end").toFile();
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
