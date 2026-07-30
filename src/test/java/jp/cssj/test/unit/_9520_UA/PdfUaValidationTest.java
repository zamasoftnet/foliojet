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
	 * ページを跨ぐ段落・リスト項目・繰り返しヘッダ付きの表を含む文書の
	 * PDF/UA-1検証です(欠陥②=StructElem分裂の修正、2026-07-30)。継続を
	 * 1つのStructElemへ併合した構造(複数ページのMCIDを/Type /MCRで持つ)が
	 * veraPDFの構造検査(L→LI→LBody、Table→TR→TH/TD等)を通ることを固定する。
	 */
	public void testPdfUa1MultiPage() throws Exception {
		this.session.property("output.pdf.version", "1.7UA-1");
		this.session.property("output.pdf.tagged.lang", "en");
		this.validateUa("files/unittest/9520-UA/ua-multipage.html");
	}

	/**
	 * PDF/UA-2(2.0UA-2、タスク#21——2026-07-31)の検証です。PDF 2.0基底+
	 * pdfuaid:part 2/rev+PDF 2.0標準構造名前空間(/Namespaces、各要素の/NS)。
	 */
	public void testPdfUa2Compliant() throws Exception {
		this.session.property("output.pdf.version", "2.0UA-2");
		this.session.property("output.pdf.tagged.lang", "en");
		this.validate("files/unittest/9520-UA/ua.html", PDFAFlavour.PDFUA_2, "PDF/UA-2");
	}

	/** UA-2でPDF 1.7専用ロール(Sect/BlockQuote等)を含む文書も通ること。 */
	public void testPdfUa2LegacyRoles() throws Exception {
		this.session.property("output.pdf.version", "2.0UA-2");
		this.session.property("output.pdf.tagged.lang", "en");
		this.validate("files/unittest/9520-UA/ua2-roles.html", PDFAFlavour.PDFUA_2, "PDF/UA-2");
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
