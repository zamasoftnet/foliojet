package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * Numbers in scientific notation ({@code 1e2px}, as CSS minifiers write 100px) are read as numbers. The exponent
 * used to be taken for the start of the unit ({@code e2px}), so the declaration was dropped.
 *
 * <p>
 * Each declaration is compared with the same one written without an exponent: the pages are laid out alike.
 * </p>
 */
public class CssExponentNumberTest extends TestCase {
	private static final String HEAD = "<style>@page{size:400pt 300pt;margin:0}body{margin:0;font:10pt/12pt serif}"
			+ "p{margin:0}</style>";

	/** A dimension with an exponent: lower and upper case, a fraction as the mantissa, a negative exponent. */
	public void testDimension() throws Exception {
		final String indent = pages(paragraph("margin-left:100px"));
		assertTrue("KEEP is indented by 100px: " + indent,
				Pattern.compile("x=75\\.00 y=[-\\d.]+ Text\\[\"KEEP\"").matcher(indent).find());
		assertEquals("1e2px", indent, pages(paragraph("margin-left:1e2px")));
		assertEquals("1E2px", indent, pages(paragraph("margin-left:1E2px")));
		assertEquals(".1e3px", indent, pages(paragraph("margin-left:.1e3px")));
		assertEquals("1000e-1px", indent, pages(paragraph("margin-left:1000e-1px")));
	}

	/** A number with an exponent. */
	public void testNumber() throws Exception {
		assertEquals("2e1", pages(paragraph("line-height:20")), pages(paragraph("line-height:2e1")));
	}

	/** What only looks like an exponent stays what it is: the units em and ex, and a hex color with an e. */
	public void testNotAnExponent() throws Exception {
		assertEquals(pages(paragraph("margin-left:10pt") + paragraph("border-left:75pt solid #11ee33")),
				pages(paragraph("margin-left:1em") + paragraph("border-left:75pt solid #1e3")));
		assertFalse("1ex is a length",
				pages(paragraph("margin-left:0")).equals(pages(paragraph("margin-left:1ex"))));
	}

	/** A number too large for a double is not a number: the declaration is ignored alone. */
	public void testNumberOutOfRange() throws Exception {
		assertEquals(pages(paragraph("margin-left:10pt")),
				pages(paragraph("margin-left:10pt;margin-left:1e999px;width:1e999px;line-height:1e999")));
	}

	private static String paragraph(final String declarations) {
		return "<p style=\"" + declarations + "\">KEEP</p>";
	}

	/** Converts the document and returns the display lists of its pages, one after the other. */
	private static String pages(final String body) throws Exception {
		final File dir = Files.createTempDirectory("css-exponent").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html>" + HEAD + body, StandardCharsets.UTF_8);
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
