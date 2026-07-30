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

	/** タグ付き出力を有効にして任意のfixtureを変換し、PDFバイト列を返す。 */
	private String transcodeTaggedAndRead(final String path) throws Exception {
		this.session.property("output.pdf.tagged", "true");
		this.session.property("output.pdf.tagged.lang", "ja");
		CTISessionHelper.transcodeFile(this.session, new File(path), "text/html", null);
		this.session.close();
		this.closed = true;
		return new String(Files.readAllBytes(this.file.toPath()), StandardCharsets.ISO_8859_1);
	}

	/** ページオブジェクト数(/Pagesノードを除く)。 */
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
	 * 改ページを跨ぐtagged出力の検証です(E-6増分3b-4、2026-07-24)。
	 * 改ページ残余のソース再生で作られたボックスの{@code Params.element}は
	 * {@code StructureToken}(CSSElementではない)になるため、複数ページに
	 * 割れたリストでも構造ロールとリンク注釈(どちらもelementの読み手)が
	 * 出続けることを固定する。
	 */
	public void testTaggedMultiPageStructure() throws Exception {
		this.session.property("output.pdf.hyperlinks", "true");
		final String pdf = this
				.transcodeTaggedAndRead("files/unittest/9500-PROFILE/structure-multipage.html");
		// 前提の検証: この文書は実際に複数ページへ割れている
		final int pageObjects = pageCount(pdf);
		assertTrue("this fixture must break across pages (pages=" + pageObjects + ")", pageObjects >= 2);
		// 構造ロール(Tagged PDF)とリンク注釈(atts読み手)が全ページ分出ている
		for (final String role : new String[] { "/S /H1", "/S /L", "/S /LI", "/S /Link" }) {
			assertTrue("missing structure element " + role, pdf.contains(role));
		}
		// LI開きはちょうど50項目分——StructureTokenのidentity intern(同じ
		// 論理要素=同じインスタンス)が壊れると、再生されたliのprincipal/
		// marker対で二重開きになり~2倍へ跳ねる。ページ跨ぎで割れたliも
		// 欠陥②の修正(2026-07-30)後は初出のStructElemへ内容を継ぎ足す
		// ため、+1されない
		assertEquals("LI structure elements must be one per list item", 50, count(pdf, "/S /LI"));
		final int links = count(pdf, "/Subtype /Link");
		assertTrue("link annotations must survive page continuation: " + links, links >= 50 && links <= 55);
	}

	/**
	 * 欠陥②(ページ跨ぎ要素のStructElem分裂)の回帰テストです(2026-07-30)。
	 * 1つの{@code <p>}が複数ページへ割れても、StructElemは1つのまま内容
	 * (MCID)がページを跨ぐ——別ページのMCIDは{@code /Type /MCR}
	 * (marked-content reference)で参照される。
	 */
	public void testContinuationParagraphSingleStructElem() throws Exception {
		final String pdf = this
				.transcodeTaggedAndRead("files/unittest/9500-PROFILE/structure-continuation.html");
		final int pageObjects = pageCount(pdf);
		assertTrue("this fixture must break across pages (pages=" + pageObjects + ")", pageObjects >= 2);
		// "/S /P /P"のうしろの/PはStructElemの親キー(/Partなどとの誤一致を防ぐ)
		assertEquals("a page-spanning <p> must stay one StructElem", 1, count(pdf, "/S /P /P"));
		assertTrue("cross-page content must be referenced via /Type /MCR", pdf.contains("/Type /MCR"));
	}

	/** 欠陥②: ページを跨ぐ単一のリスト項目はL/LI/LBodyとも1つずつ。 */
	public void testContinuationListItemSingleStructElems() throws Exception {
		final String pdf = this
				.transcodeTaggedAndRead("files/unittest/9500-PROFILE/structure-continuation-list.html");
		final int pageObjects = pageCount(pdf);
		assertTrue("this fixture must break across pages (pages=" + pageObjects + ")", pageObjects >= 2);
		assertEquals("one list, one StructElem", 1, count(pdf, "/S /L /P"));
		assertEquals("one item, one StructElem", 1, count(pdf, "/S /LI"));
		assertEquals("one item body, one StructElem", 1, count(pdf, "/S /LBody"));
	}

	/** 欠陥②: 行ごと割れた表のセル(TH)も1つのStructElemに保たれ、Scopeを失わない。 */
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
	 * 欠陥②の境界: 繰り返し表ヘッダは「同じ要素の反復表示」であって継続では
	 * ない。継続として1つのStructElemへ併合すると同じ見出しの内容がページ数
	 * ぶん重複するため、ページごとに独立したStructElemのままにする。
	 */
	public void testRepeatedTableHeaderDeclaresPerPage() throws Exception {
		final String pdf = this
				.transcodeTaggedAndRead("files/unittest/9500-PROFILE/structure-repeated-header.html");
		final int pageObjects = pageCount(pdf);
		assertTrue("this fixture must break across pages (pages=" + pageObjects + ")", pageObjects >= 2);
		// 表そのものは継続——1つに保たれる
		assertEquals("one table, one StructElem", 1, count(pdf, "/S /Table"));
		// 繰り返しヘッダのTHはページごとに1つずつ
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
	 * タグ付きPDF欠陥①(z-indexで別Drawerになると子の構造要素が親の兄弟に
	 * なる)の専用回帰テストです(B-3で解消済み・タスク#22で追加、
	 * 2026-07-31)。z-index:1の内側Divが外側Divの子のまま(Documentの
	 * 直接の子はDiv 1つだけ)であることをStructElemの/P参照から固定する。
	 */
	public void testZIndexKeepsStructureNesting() throws Exception {
		final String pdf = this.transcodeTaggedAndRead("files/unittest/9500-PROFILE/structure-z-index.html");
		// objnum -> (role, parent objnum) をStructElem辞書から抽出
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
		// 欠陥①が再発するとz-indexのDivがDocument直下の兄弟になる(=2/0)
		assertEquals("only the outer Div may sit under Document", 1, divsUnderDocument);
		assertEquals("the z-index Div must stay nested in the outer Div", 1, nestedDivs);
	}

	/** PDF/UA-2(2.0UA-2): PDF 2.0基底+part 2/rev+PDF 2.0構造名前空間。 */
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
