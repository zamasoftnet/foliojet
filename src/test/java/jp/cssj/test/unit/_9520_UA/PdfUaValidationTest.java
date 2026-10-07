package jp.cssj.test.unit._9520_UA;

import java.io.File;
import java.io.FileInputStream;
import java.util.stream.Collectors;

import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Validates that a tagged PDF/UA-1 document produced from HTML by foliojet
 * passes the veraPDF accessibility checks.
 */
public class PdfUaValidationTest extends AbstractTestCase {

	static {
		VeraGreenfieldFoundryProvider.initialise();
	}

	public PdfUaValidationTest(String name) {
		super(name);
	}

	private boolean closed = false;

	@Override
	protected void tearDown() throws Exception {
		if (!this.closed) {
			super.tearDown();
		}
	}

	protected void transcode() throws Exception {
		// driven per test
	}

	public void testDocument() throws Exception {
		// no-op: this suite validates PDF bytes, not geometry
	}

	public void testPdfUa1Compliant() throws Exception {
		this.session.property("output.pdf.version", "1.7UA-1");
		this.session.property("output.pdf.tagged.lang", "ja");
		this.validateUa("files/unittest/9520-UA/ua.html");
	}

	public void testPdfUa1WithForm() throws Exception {
		this.session.property("output.pdf.version", "1.7UA-1");
		this.session.property("output.pdf.tagged.lang", "ja");
		this.session.property("output.pdf.forms", "true");
		this.validateUa("files/unittest/9520-UA/ua-form.html");
	}

	public void testPdfUa1WithMedia() throws Exception {
		this.session.property("output.pdf.version", "1.7UA-1");
		this.session.property("output.pdf.tagged.lang", "ja");
		this.session.property("output.pdf.hyperlinks", "true");
		this.validateUa("files/unittest/9520-UA/ua-media.html");
		final String pdf = new String(java.nio.file.Files.readAllBytes(this.file.toPath()),
				java.nio.charset.StandardCharsets.ISO_8859_1);
		assertTrue("the image must become a Figure structure element", pdf.contains("/S /Figure"));
		assertTrue("the link must become a Link structure element", pdf.contains("/S /Link"));
	}

	/**
	 * PDF/UA-1 validation of a document containing paragraphs, list items, and a table with repeated
	 * headers spanning pages (fix for defect ②, StructElem splitting, 2026-07-30).
	 * Verifies that structure merging continuations into one StructElem (with MCIDs from multiple pages
	 * via /Type /MCR) passes veraPDF structure checks (L→LI→LBody, Table→TR→TH/TD, etc.).
	 */
	public void testPdfUa1MultiPage() throws Exception {
		this.session.property("output.pdf.version", "1.7UA-1");
		this.session.property("output.pdf.tagged.lang", "en");
		this.validateUa("files/unittest/9520-UA/ua-multipage.html");
	}

	/**
	 * Validates PDF/UA-2 (2.0UA-2, task #21, 2026-07-31).
	 * PDF 2.0 base + pdfuaid:part 2/rev + PDF 2.0 standard structure namespace
	 * (/Namespaces and /NS on each element).
	 */
	public void testPdfUa2Compliant() throws Exception {
		this.session.property("output.pdf.version", "2.0UA-2");
		this.session.property("output.pdf.tagged.lang", "en");
		this.validate("files/unittest/9520-UA/ua.html", PDFAFlavour.PDFUA_2, "PDF/UA-2");
	}

	/** Documents containing PDF 1.7-only roles (Sect/BlockQuote, etc.) must also pass UA-2. */
	public void testPdfUa2LegacyRoles() throws Exception {
		this.session.property("output.pdf.version", "2.0UA-2");
		this.session.property("output.pdf.tagged.lang", "en");
		this.validate("files/unittest/9520-UA/ua2-roles.html", PDFAFlavour.PDFUA_2, "PDF/UA-2");
	}

	/** A document containing filtered elements (rasterized layers) also conforms to PDF/UA-1 (2026-09-03). */
	public void testPdfUa1WithFilteredElement() throws Exception {
		this.session.property("output.pdf.version", "1.7UA-1");
		this.session.property("output.pdf.tagged.lang", "en");
		this.session.property("output.pdf.hyperlinks", "true");
		this.validateUa("files/unittest/9520-UA/ua-filter.html");
	}

	/** A reordered bidi fixture with logical /K order remains PDF/UA-1 without ActualText. */
	public void testPdfUa1WithLogicalBidiOutput() throws Exception {
		this.session.property("output.pdf.version", "1.7UA-1");
		this.session.property("output.pdf.tagged.lang", "en");
		// Hebrew (ua-logical-output.html) lacks embedded-font glyphs and fails 7.21.8 on .notdef references;
		// so validate PDF/UA with the Latin bidi-override version (visual order 321 CBA / logical order ABC 123).
		this.validateUa("files/unittest/3090-bidi/ua-logical-output-latin.html");
	}

	/** The opt-in line ActualText path also remains PDF/UA-1. */
	public void testPdfUa1WithLogicalBidiOutputAndActualText() throws Exception {
		this.session.property("output.pdf.version", "1.7UA-1");
		this.session.property("output.pdf.tagged.lang", "en");
		this.session.property("output.pdf.bidi.actual-text", "true");
		this.validateUa("files/unittest/3090-bidi/ua-logical-output-latin.html");
	}

	/**
	 * The single-pass table of contents (later page numbers written into a component afterward,
	 * 2026-10-04) also conforms to PDF/UA-1. Components are drawn in the same marked content as text,
	 * and contain no MCIDs inside them.
	 */
	public void testPdfUa1WithOnePassTableOfContents() throws Exception {
		this.session.property("output.pdf.version", "1.7UA-1");
		this.session.property("output.pdf.tagged.lang", "en");
		this.validateUa("files/unittest/9520-UA/ua-toc.html");
		final String pdf = new String(java.nio.file.Files.readAllBytes(this.file.toPath()),
				java.nio.charset.StandardCharsets.ISO_8859_1);
		assertTrue("the forward page numbers must be deferred forms", pdf.contains("/Subtype /Form"));
	}

	/** PDF/UA-2 version of the same table of contents. */
	public void testPdfUa2WithOnePassTableOfContents() throws Exception {
		this.session.property("output.pdf.version", "2.0UA-2");
		this.session.property("output.pdf.tagged.lang", "en");
		this.validate("files/unittest/9520-UA/ua-toc.html", PDFAFlavour.PDFUA_2, "PDF/UA-2");
	}

	private void validateUa(final String path) throws Exception {
		this.validate(path, PDFAFlavour.PDFUA_1, "PDF/UA-1");
	}

	private void validate(final String path, final PDFAFlavour flavour, final String label) throws Exception {
		CTISessionHelper.transcodeFile(this.session, new File(path), "text/html", null);
		this.session.close();
		this.closed = true;

		try (final var parser = Foundries.defaultInstance().createParser(new FileInputStream(this.file), flavour);
				final var validator = Foundries.defaultInstance().createValidator(flavour, false)) {
			final var result = validator.validate(parser);
			if (!result.isCompliant()) {
				final String failures = result.getTestAssertions().stream()
						.filter(a -> a.getStatus() == TestAssertion.Status.FAILED)
						.map(a -> a.getRuleId() + " " + a.getMessage() + " @ " + a.getLocation().getContext())
						.distinct().collect(Collectors.joining("\n"));
				fail("veraPDF " + label + " failures:\n" + failures);
			}
		}
	}
}
