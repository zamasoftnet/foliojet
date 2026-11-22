package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * An HTML presentational attribute whose value the property it maps to does not take is ignored: the property gets
 * the fallback of its {@code attr()} in the user agent style sheet, or behaves as unset when there is none. These
 * attributes are mapped with typed {@code attr()}, and a value that did not parse, a percentage where the property
 * takes a length only, or an empty attribute used to reach the property as a value of another type, which ended
 * the conversion with {@code ClassCastException}.
 *
 * <p>
 * The documents are compared by the display lists of their pages: one with attributes that do not parse is laid out
 * exactly as the one with the attributes empty (which always took the fallback) or absent.
 * </p>
 */
public class HtmlAttributeFallbackTest extends TestCase {
	/** border, cellspacing and cellpadding of a table take lengths, not percentages and not negative ones. */
	public void testTableLengths() throws Exception {
		final String fallback = pages(table("border=\"\" cellspacing=\"\" cellpadding=\"\"").repeat(4));
		assertKeepAndAfter(fallback);
		assertEquals("percentages, negative numbers, words and numbers that are not finite take the fallback",
				fallback,
				pages(table("border=\"5%\" cellspacing=\"5%\" cellpadding=\"5%\"")
						+ table("border=\"-3\" cellspacing=\"-3\" cellpadding=\"-3\"")
						+ table("border=\"x\" cellspacing=\"x\" cellpadding=\"x\"")
						+ table("border=\"1e999\" cellspacing=\"Infinity\" cellpadding=\"1e999px\"")));
		// The attributes are still read
		assertFalse("lengths apply",
				fallback.equals(pages(table("border=\"5\" cellspacing=\"5\" cellpadding=\"5\"").repeat(4))));
	}

	/** The size of a rule: its border width with noshade, its height without. */
	public void testRuleSize() throws Exception {
		final String plain = pages("<p>KEEP</p>" + "<hr noshade>".repeat(4) + "<hr>".repeat(2) + "<p>AFTER</p>");
		assertKeepAndAfter(plain);
		assertEquals("a size that is no length is as no size", plain,
				pages("<p>KEEP</p><hr noshade size><hr noshade size=\"big\"><hr noshade size=\"-2\">"
						+ "<hr noshade size=\"5%\"><hr size><hr size=\"big\"><p>AFTER</p>"));
		assertFalse("a size applies", plain.equals(pages("<p>KEEP</p>" + "<hr noshade size=\"9\">".repeat(4)
				+ "<hr size=\"9\">".repeat(2) + "<p>AFTER</p>")));
	}

	/** The other attributes the user agent style sheet maps with attr(), each with values that do not parse. */
	public void testOtherAttributes() throws Exception {
		assertKeepAndAfter(pages("<html><body text=\"zzz\" bgcolor=\"\" leftmargin=\"x\" topmargin=\"5%\""
				+ " rightmargin=\"\" bottommargin=\"1e999\" marginwidth=\"-4\" marginheight=\"Infinity\">"
				+ "<p>KEEP</p>"
				+ "<table width=\"x\" height=\"-5\" hspace=\"x\" vspace=\"5%\" border=\"2\" bordercolor=\"zzz\""
				+ " bgcolor=\"zzz\"><colgroup width=\"x\"><col width=\"-1\" bgcolor=\"\"></colgroup>"
				+ "<tr height=\"x\" bgcolor=\"none\"><td width=\"-5\" height=\"1e999\" bgcolor=\"#ggg\">a</td>"
				+ "<th width=\"Infinity\" height=\"\">b</th></tr></table>"
				+ "<hr color=\"zzz\" width=\"x\"><hr noshade color=\"\" width=\"-1\">"
				+ "<pre cols=\"x\">c</pre><pre width=\"-1\">d</pre>"
				+ "<select size=\"x\"><option>e</option></select><select size=\"-2\"><option>f</option></select>"
				+ "<p>AFTER</p></body></html>"));
	}

	/** attr() in a style sheet: a type or a fallback the property does not take makes the declaration invalid. */
	public void testAuthorAttr() throws Exception {
		// A color where a length is expected, a number where a color is expected, auto for a border width
		assertKeepAndAfter(pages("<p data-x=\"red\" style=\"width:attr(data-x type(color))\">KEEP</p>"
				+ "<p data-x=\"3\" style=\"color:attr(data-x type(number))\">a</p>"
				+ "<p data-x=\"3\" style=\"width:attr(data-x type(number))\">b</p>"
				+ "<p style=\"border:solid;border-top-width:attr(data-x px, auto)\">c</p>"
				+ "<p style=\"border:solid;border-left-width:attr(data-x px, 5%)\">d</p>"
				+ "<p data-x=\"5%\" style=\"border:solid;border-left-width:attr(data-x px)\">e</p>"
				+ "<p style=\"padding-left:attr(data-x px, -5px)\">f</p>"
				+ "<div style=\"display:flex\"><p style=\"flex-basis:attr(data-x px)\">g</p></div>"
				+ "<p>AFTER</p>"));
	}

	private static String table(final String attributes) {
		return "<table " + attributes + "><tr><td>KEEP</td><td>b</td></tr><tr><td>c</td><td>d</td></tr></table>"
				+ "<p>AFTER</p>";
	}

	private static void assertKeepAndAfter(final String pages) {
		assertTrue("KEEP is on the page: " + pages, pages.contains("Text[\"KEEP\""));
		assertTrue("AFTER is on the page: " + pages, pages.contains("Text[\"AFTER\""));
	}

	/** Converts the document and returns the display lists of its pages, one after the other. */
	private static String pages(final String body) throws Exception {
		final File dir = Files.createTempDirectory("html-attribute").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html>" + body, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final StringBuilder pages = new StringBuilder();
		for (int i = 1;; ++i) {
			final File page = new File(dir, String.format("page-%04d.txt", i));
			if (!page.exists()) {
				break;
			}
			pages.append(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return pages.toString();
	}
}
