package jp.cssj.test.unit._9500_PROFILE;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;

/**
 * Verifies that the extended {@code output.pdf.*} user-agent properties reach
 * the pdfg2d writer: newer PDF versions/profiles (PDF 2.0, PDF/A-2, PDF/UA-1),
 * tagged PDF, and AES-256 encryption. These are metadata/output-config
 * settings and do not change page rendering.
 */
public class OutputPdfProfileTest extends AbstractTestCase {

	public OutputPdfProfileTest(String name) {
		super(name);
	}

	private static final File SIMPLE = new File("files/unittest/9500-PROFILE/simple.html");

	private boolean closed = false;

	private String transcodeAndRead() throws Exception {
		CTISessionHelper.transcodeFile(this.session, SIMPLE, "text/html", null);
		this.session.close();
		this.closed = true;
		return new String(Files.readAllBytes(this.file.toPath()), StandardCharsets.ISO_8859_1);
	}

	@Override
	protected void tearDown() throws Exception {
		// The session was already closed to flush the PDF before reading it.
		if (!this.closed) {
			super.tearDown();
		}
	}

	protected void transcode() throws Exception {
		// Not used; each test drives its own transcode.
	}

	public void testPdf20Version() throws Exception {
		this.session.property("output.pdf.version", "2.0");
		final String pdf = this.transcodeAndRead();
		assertTrue("output.pdf.version=2.0 must emit a PDF 2.0 header", pdf.startsWith("%PDF-2.0"));
	}

	public void testPdfA2Profile() throws Exception {
		this.session.property("output.pdf.version", "1.7A-2");
		final String pdf = this.transcodeAndRead();
		assertTrue("output.pdf.version=1.7A-2 must declare PDF/A part 2",
				pdf.contains("pdfaid:part") && pdf.contains(">2<"));
		assertTrue("PDF/A-2b conformance must be B",
				pdf.contains("pdfaid:conformance") && pdf.contains(">B<"));
	}

	public void testTaggedProperty() throws Exception {
		this.session.property("output.pdf.tagged", "true");
		this.session.property("output.pdf.tagged.lang", "ja");
		final String pdf = this.transcodeAndRead();
		assertTrue("output.pdf.tagged=true must emit a structure tree", pdf.contains("/StructTreeRoot"));
		assertTrue("MarkInfo must declare the file as tagged", pdf.contains("/Marked true"));
	}

	public void testPdfUa1AutoEnablesTagging() throws Exception {
		this.session.property("output.pdf.version", "1.7UA-1");
		this.session.property("output.pdf.tagged.lang", "ja"); // PDF/UA requires a language
		final String pdf = this.transcodeAndRead();
		assertTrue("PDF/UA-1 must be tagged", pdf.contains("/StructTreeRoot"));
		assertTrue("PDF/UA-1 must carry the pdfuaid identifier", pdf.contains("pdfuaid:part"));
	}

	public void testAes256Encryption() throws Exception {
		this.session.property("output.pdf.version", "2.0");
		this.session.property("output.pdf.encryption", "v5");
		this.session.property("output.pdf.encryption.user-password", "user");
		final String pdf = this.transcodeAndRead();
		assertTrue("output.pdf.encryption=v5 must use the AESV3 crypt filter", pdf.contains("/AESV3"));
		assertTrue("AES-256 uses security handler revision 6", pdf.contains("/R 6"));
	}

	// Override the geometry-based driver: this suite checks PDF bytes instead.
	public void testDocument() throws Exception {
		// no-op
	}
}
