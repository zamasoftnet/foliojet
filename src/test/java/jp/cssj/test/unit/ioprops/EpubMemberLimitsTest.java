package jp.cssj.test.unit.ioprops;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import junit.framework.TestCase;

/**
 * The members of an EPUB count against the limits on the resources of a conversion.
 *
 * <p>
 * The members are served from the archive by a resolver of their own, in front of the resolver that applies
 * {@code input.resource-size-limit} and {@code input.resource-count-limit}. They used to bypass both limits (and the
 * operator's ceilings on them): {@code input.size-limit} bounds only the compressed book, so a style sheet of a few
 * kilobytes in the archive could be read into memory at any decompressed size.
 * </p>
 *
 * <p>
 * The book here has one item and a style sheet of about 200 KB (padding, then a rule that hides a paragraph). A
 * style sheet that is refused leaves the paragraph on the page.
 * </p>
 */
public class EpubMemberLimitsTest extends TestCase {
	private static final String PAGE = "<style type=\"text/css\">@page{size:200pt 150pt;margin:10pt}"
			+ "body{margin:0;font-size:10pt}p{margin:0}</style>";

	private static EpubBooks book() {
		final StringBuilder css = new StringBuilder(200_100);
		for (int i = 0; i < 200_000; ++i) {
			css.append(' ');
		}
		css.append(".styled { display: none }");
		return new EpubBooks()
				.item("c1", "c1.xhtml", EpubBooks.xhtml("t", PAGE + "<link rel=\"stylesheet\" href=\"s.css\"/>",
						"<p>KEEP</p><p class=\"styled\">UNSTYLED</p>"))
				.file("s.css", css.toString());
	}

	private static String text(final byte[] pdf) throws Exception {
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			return new PDFTextStripper().getText(doc).replaceAll("\\s+", " ").trim();
		}
	}

	/** Within the limits the style sheet is read and applied (the path is live). */
	public void testMemberWithinTheLimitsIsRead() throws Exception {
		final String text = text(book().convert("input.resource-size-limit", "16777216",
				"input.resource-count-limit", "16").first());
		assertTrue(text, text.contains("KEEP"));
		assertFalse("the style sheet was not applied: " + text, text.contains("UNSTYLED"));
	}

	/** A member larger than what is left of {@code input.resource-size-limit} is refused; the book is set without it. */
	public void testMemberOverTheResourceSizeLimitIsRefused() throws Exception {
		final String text = text(book().convert("input.resource-size-limit", "65536").first());
		assertTrue(text, text.contains("KEEP"));
		assertTrue("the style sheet over the limit was applied: " + text, text.contains("UNSTYLED"));
	}

	/** A member is a resource like any other for {@code input.resource-count-limit}. */
	public void testMemberCountsAgainstTheResourceCountLimit() throws Exception {
		final String text = text(book().convert("input.resource-count-limit", "0").first());
		assertTrue(text, text.contains("KEEP"));
		assertTrue("the style sheet over the limit was applied: " + text, text.contains("UNSTYLED"));
	}
}
