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

	/** Converts any fixture with tagged output enabled and returns the PDF bytes. */
	private String transcodeTaggedAndRead(final String path) throws Exception {
		this.session.property("output.pdf.tagged", "true");
		this.session.property("output.pdf.tagged.lang", "ja");
		CTISessionHelper.transcodeFile(this.session, new File(path), "text/html", null);
		this.session.close();
		this.closed = true;
		return new String(Files.readAllBytes(this.file.toPath()), StandardCharsets.ISO_8859_1);
	}

	/** Number of page objects (excluding /Pages nodes). */
	private static int pageCount(final String pdf) {
		return count(pdf, "/Type /Page") - count(pdf, "/Type /Pages");
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
		// The HTML structure (h1, p) must reach the tag tree.
		assertTrue("the <h1> must become an H1 structure element", pdf.contains("/S /H1"));
		assertTrue("the <p> must become a P structure element", pdf.contains("/S /P"));
	}

	public void testTaggedStructureRoles() throws Exception {
		final String pdf = this.transcodeTaggedAndRead("files/unittest/9500-PROFILE/structure.html");
		// Headings, lists and tables must all reach the structure tree.
		for (final String role : new String[] { "/S /H1", "/S /P", "/S /L", "/S /LI", "/S /Table", "/S /TR",
				"/S /TH", "/S /TD" }) {
			assertTrue("missing structure element " + role, pdf.contains(role));
		}
	}

	/**
	 * Tests tagged output across page breaks (E-6 increment 3b-4, 2026-07-24).
	 * Boxes created by source replay of page-break remainders have a {@code StructureToken}
	 * (not a CSSElement) as {@code Params.element}. Verifies that structure roles and link annotations
	 * (both readers of element) continue to be emitted for lists split across multiple pages.
	 */
	public void testTaggedMultiPageStructure() throws Exception {
		this.session.property("output.pdf.hyperlinks", "true");
		final String pdf = this
				.transcodeTaggedAndRead("files/unittest/9500-PROFILE/structure-multipage.html");
		// Verify the premise: this document actually spans multiple pages.
		final int pageObjects = pageCount(pdf);
		assertTrue("this fixture must break across pages (pages=" + pageObjects + ")", pageObjects >= 2);
		// Structure roles (Tagged PDF) and link annotations (atts readers) are emitted for all pages.
		for (final String role : new String[] { "/S /H1", "/S /L", "/S /LI", "/S /Link" }) {
			assertTrue("missing structure element " + role, pdf.contains(role));
		}
		// Exactly 50 LI items are opened. If StructureToken identity interning (same logical
		// element = same instance) breaks, replayed li principal/marker pairs
		// open twice, jumping to roughly double the count. A li split across pages
		// also appends content to its first StructElem after defect ② was fixed (2026-07-30),
		// so it does not add 1 to the count.
		assertEquals("LI structure elements must be one per list item", 50, count(pdf, "/S /LI"));
		final int links = count(pdf, "/Subtype /Link");
		assertTrue("link annotations must survive page continuation: " + links, links >= 50 && links <= 55);
	}

	/**
	 * Regression test for defect ② (StructElem splitting for elements spanning pages) (2026-07-30).
	 * Even when one {@code <p>} spans multiple pages, it retains one StructElem with content (MCIDs)
	 * across pages. MCIDs on other pages are referenced through {@code /Type /MCR}
	 * (marked-content reference).
	 */
	public void testContinuationParagraphSingleStructElem() throws Exception {
		final String pdf = this
				.transcodeTaggedAndRead("files/unittest/9500-PROFILE/structure-continuation.html");
		final int pageObjects = pageCount(pdf);
		assertTrue("this fixture must break across pages (pages=" + pageObjects + ")", pageObjects >= 2);
		// The final /P in "/S /P /P" is the StructElem parent key (prevents false matches with /Part, etc.).
		assertEquals("a page-spanning <p> must stay one StructElem", 1, count(pdf, "/S /P /P"));
		assertTrue("cross-page content must be referenced via /Type /MCR", pdf.contains("/Type /MCR"));
	}

	/** Defect ②: a single list item spanning pages has exactly one each of L/LI/LBody. */
	public void testContinuationListItemSingleStructElems() throws Exception {
		final String pdf = this
				.transcodeTaggedAndRead("files/unittest/9500-PROFILE/structure-continuation-list.html");
		final int pageObjects = pageCount(pdf);
		assertTrue("this fixture must break across pages (pages=" + pageObjects + ")", pageObjects >= 2);
		assertEquals("one list, one StructElem", 1, count(pdf, "/S /L /P"));
		assertEquals("one item, one StructElem", 1, count(pdf, "/S /LI"));
		assertEquals("one item body, one StructElem", 1, count(pdf, "/S /LBody"));
	}

	/** Defect ②: table cells (TH) split with their rows also retain one StructElem and preserve Scope. */
	public void testContinuationTableRowSingleStructElems() throws Exception {
		final String pdf = this
				.transcodeTaggedAndRead("files/unittest/9500-PROFILE/structure-continuation-table.html");
		final int pageObjects = pageCount(pdf);
		assertTrue("this fixture must break across pages (pages=" + pageObjects + ")", pageObjects >= 2);
		assertEquals("one table, one StructElem", 1, count(pdf, "/S /Table"));
		assertEquals("one row, one StructElem", 1, count(pdf, "/S /TR"));
		assertEquals("one header cell, one StructElem", 1, count(pdf, "/S /TH"));
		assertEquals("the scope=row attribute must survive the split", 1, count(pdf, "/Scope /Row"));
	}

	/**
	 * Boundary of defect ②: repeated table headers are repeated displays of the same element,
	 * not continuations. Merging them into one StructElem as continuations would duplicate the same
	 * heading content once per page, so each page keeps its own independent StructElem.
	 */
	public void testRepeatedTableHeaderDeclaresPerPage() throws Exception {
		final String pdf = this
				.transcodeTaggedAndRead("files/unittest/9500-PROFILE/structure-repeated-header.html");
		final int pageObjects = pageCount(pdf);
		assertTrue("this fixture must break across pages (pages=" + pageObjects + ")", pageObjects >= 2);
		// The table itself is a continuation and remains a single element.
		assertEquals("one table, one StructElem", 1, count(pdf, "/S /Table"));
		// One TH for each repeated header on each page.
		assertEquals("the repeated header must declare one TH per page", pageObjects, count(pdf, "/S /TH"));
	}

	private static int count(final String s, final String needle) {
		int count = 0;
		for (int i = s.indexOf(needle); i >= 0; i = s.indexOf(needle, i + needle.length())) {
			++count;
		}
		return count;
	}

	public void testPdfUa1AutoEnablesTagging() throws Exception {
		this.session.property("output.pdf.version", "1.7UA-1");
		this.session.property("output.pdf.tagged.lang", "ja"); // PDF/UA requires a language
		final String pdf = this.transcodeAndRead();
		assertTrue("PDF/UA-1 must be tagged", pdf.contains("/StructTreeRoot"));
		assertTrue("PDF/UA-1 must carry the pdfuaid identifier", pdf.contains("pdfuaid:part"));
	}

	/**
	 * Dedicated regression test for tagged-PDF defect ① (a separate Drawer due to z-index made
	 * child structure elements siblings of their parent). Fixed in B-3; test added in task #22
	 * (2026-07-31). Uses StructElem /P references to verify that the inner Div with z-index:1
	 * remains a child of the outer Div (Document has only one direct Div child).
	 */
	public void testZIndexKeepsStructureNesting() throws Exception {
		final String pdf = this.transcodeTaggedAndRead("files/unittest/9500-PROFILE/structure-z-index.html");
		// Extract objnum -> (role, parent objnum) from StructElem dictionaries.
		final java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("(\\d+) 0 obj\\s*<<\\s*/Type /StructElem\\s*/S /(\\w+)\\s*/P (\\d+) 0 R")
				.matcher(pdf);
		final java.util.Map<Integer, String> roles = new java.util.HashMap<>();
		final java.util.Map<Integer, Integer> parents = new java.util.HashMap<>();
		while (m.find()) {
			roles.put(Integer.parseInt(m.group(1)), m.group(2));
			parents.put(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(3)));
		}
		final Integer documentElem = roles.entrySet().stream().filter(e -> e.getValue().equals("Document"))
				.map(java.util.Map.Entry::getKey).findFirst().orElse(null);
		assertNotNull("a Document structure element must exist", documentElem);
		final long divsUnderDocument = roles.entrySet().stream()
				.filter(e -> e.getValue().equals("Div") && documentElem.equals(parents.get(e.getKey()))).count();
		final long nestedDivs = roles.entrySet().stream().filter(e -> e.getValue().equals("Div")
				&& "Div".equals(roles.get(parents.get(e.getKey())))).count();
		// If defect ① recurs, the z-index Div becomes a sibling directly under Document (=2/0).
		assertEquals("only the outer Div may sit under Document", 1, divsUnderDocument);
		assertEquals("the z-index Div must stay nested in the outer Div", 1, nestedDivs);
	}

	/** PDF/UA-2 (2.0UA-2): PDF 2.0 base + part 2/rev + PDF 2.0 structure namespace. */
	public void testPdfUa2Profile() throws Exception {
		this.session.property("output.pdf.version", "2.0UA-2");
		this.session.property("output.pdf.tagged.lang", "ja");
		final String pdf = this.transcodeAndRead();
		assertTrue("PDF/UA-2 must use a PDF 2.0 header", pdf.startsWith("%PDF-2.0"));
		assertTrue("PDF/UA-2 must be tagged", pdf.contains("/StructTreeRoot"));
		assertTrue("pdfuaid part must be 2", pdf.contains("<pdfuaid:part>2</pdfuaid:part>"));
		assertTrue("pdfuaid rev must identify the 2024 revision",
				pdf.contains("<pdfuaid:rev>2024</pdfuaid:rev>"));
		assertTrue("the structure tree must declare the PDF 2.0 namespace",
				pdf.contains("/Namespaces") && pdf.contains("http://iso.org/pdf2/ssn"));
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
