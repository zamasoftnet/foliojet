package jp.cssj.test.unit._1070_STYLE;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;
import net.zamasoft.zstream.resolver.util.SimpleSourceMetadata;

/**
 * Verifies the rules for stylesheet selection via {@code input.stylesheet.titles}
 * (2026-10-05, jigensha report).
 *
 * <p>
 * Stylesheets without a title (HTML persistent stylesheets) apply regardless of selection,
 * and alternatives with selected titles are added. Titles match exactly, and multiple titles can
 * be separated by spaces or commas. Previously, a link without a title failed the whole conversion
 * (4001, {@code String.indexOf(null)}), and titles were compared by substring.
 * </p>
 */
public class StylesheetTitlesTest extends TestCase {
	private static final String DOC = """
			<?xml version="1.0" encoding="UTF-8"?>
			<html xmlns="http://www.w3.org/1999/xhtml"><head><title>titles</title>
			<link rel="stylesheet" href="data:text/css,.c%3A%3Aafter%7Bcontent%3A%22%20%5Bpersistent%5D%22%7D"/>
			<link rel="alternate stylesheet" title="tate" href="data:text/css,.a%3A%3Aafter%7Bcontent%3A%22%20%5Btate%5D%22%7D"/>
			<link rel="alternate stylesheet" title="cols" href="data:text/css,.b%3A%3Aafter%7Bcontent%3A%22%20%5Bcols%5D%22%7D"/>
			</head><body><p class="a">a</p><p class="b">b</p><p class="c">c</p></body></html>
			""";

	private static String text(final String titles) throws Exception {
		final ByteArrayOutputStream pdf = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			if (titles != null) {
				session.property("input.stylesheet.titles", titles);
			}
			session.setResults(new SingleResult(new StreamFragmentedOutput(pdf)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			try (OutputStream out = session.transcode(
					new SimpleSourceMetadata(URI.create("file:///titles.xhtml"), "application/xhtml+xml", "UTF-8", -1L))) {
				out.write(DOC.getBytes(StandardCharsets.UTF_8));
			}
		} finally {
			session.close();
		}
		try (PDDocument doc = Loader.loadPDF(pdf.toByteArray())) {
			return new PDFTextStripper().getText(doc);
		}
	}

	public void testWithoutSelectionOnlyPersistentApplies() throws Exception {
		final String text = text(null);
		assertTrue(text, text.contains("[persistent]"));
		assertFalse(text, text.contains("[tate]"));
	}

	public void testSelectedAlternateAddsToPersistent() throws Exception {
		final String text = text("tate");
		assertTrue(text, text.contains("[persistent]"));
		assertTrue(text, text.contains("[tate]"));
		assertFalse(text, text.contains("[cols]"));
	}

	public void testSeveralTitlesWithCommaOrSpace() throws Exception {
		for (final String titles : new String[] { "tate,cols", "tate cols", " cols , tate " }) {
			final String text = text(titles);
			assertTrue(titles + ": " + text, text.contains("[persistent]"));
			assertTrue(titles + ": " + text, text.contains("[tate]"));
			assertTrue(titles + ": " + text, text.contains("[cols]"));
		}
	}

	public void testTitlesMatchWhole() throws Exception {
		// "ta" is part of "tate" but not its title
		final String text = text("ta");
		assertTrue(text, text.contains("[persistent]"));
		assertFalse(text, text.contains("[tate]"));
	}
}
