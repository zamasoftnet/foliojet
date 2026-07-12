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

	private void validateUa(final String path) throws Exception {
		CTISessionHelper.transcodeFile(this.session, new File(path), "text/html", null);
		this.session.close();
		this.closed = true;

		try (final var parser = Foundries.defaultInstance().createParser(new FileInputStream(this.file),
				PDFAFlavour.PDFUA_1);
				final var validator = Foundries.defaultInstance().createValidator(PDFAFlavour.PDFUA_1, false)) {
			final var result = validator.validate(parser);
			if (!result.isCompliant()) {
				final String failures = result.getTestAssertions().stream()
						.filter(a -> a.getStatus() == TestAssertion.Status.FAILED)
						.map(a -> a.getRuleId() + " " + a.getMessage() + " @ " + a.getLocation().getContext())
						.distinct().collect(Collectors.joining("\n"));
				fail("veraPDF PDF/UA-1 failures:\n" + failures);
			}
		}
	}
}
