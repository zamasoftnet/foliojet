package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.fragment.FragmentationAudit;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * {@link FragmentationAudit}(2026-07-21新設、M6d-0.5)を実文書経由で
 * 有効化し、(1)実際に改ページ判定のイベントが記録されること、
 * (2)観測の有効/無効で出力ページ数が変わらないこと(=観測が既存の
 * 分岐に一切影響しないこと)を固定するsmoke test。
 */
public class FragmentationAuditSmokeTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	protected void tearDown() throws Exception {
		FragmentationAudit.setEnabled(false);
		FragmentationAudit.reset();
	}

	public void testAuditRecordsEventsAndDoesNotAffectOutput() throws Exception {
		final File doc = this.generateMultiPageDocument("fragmentation-audit-smoke");

		final File pdfWithoutAudit = new File("local/unittest/display-list/fragmentation-audit-off.pdf");
		FragmentationAudit.setEnabled(false);
		this.transcode(doc, pdfWithoutAudit);
		final int pagesWithoutAudit = countPages(pdfWithoutAudit);

		FragmentationAudit.setEnabled(true);
		FragmentationAudit.reset();
		final File pdfWithAudit = new File("local/unittest/display-list/fragmentation-audit-on.pdf");
		this.transcode(doc, pdfWithAudit);
		final int pagesWithAudit = countPages(pdfWithAudit);

		assertTrue("観測有効時に少なくとも1件のイベントが記録されるはずです(複数ページの文書のため)",
				FragmentationAudit.current().events().size() > 0);
		assertTrue("複数ページの文書のはずです(観測対象のsplitPageAxis呼び出しが複数回発生する前提)",
				pagesWithoutAudit > 1);
		assertEquals("観測の有効/無効でPDFの出力ページ数が変わらないはずです(挙動に影響しない証拠)",
				pagesWithoutAudit, pagesWithAudit);
	}

	private static int countPages(final File pdf) throws IOException {
		final String content = new String(java.nio.file.Files.readAllBytes(pdf.toPath()), StandardCharsets.ISO_8859_1);
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("/Count (\\d+)").matcher(content);
		assertTrue("PDFに/Countフィールドが見つかりません", m.find());
		return Integer.parseInt(m.group(1));
	}

	private void transcode(final File doc, final File pdf) throws Exception {
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, doc, "text/html", null);
			} finally {
				session.close();
			}
		}
	}

	private File generateMultiPageDocument(final String name) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"300pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 10pt serif;margin:0}"
					+ "div{margin:0;padding:0}</style>\n");
			w.write("</head><body>\n");
			w.write("<div>\n");
			for (int i = 0; i < 200; ++i) {
				w.write("LEAF-" + String.format("%06d", i) + "<br/>\n");
			}
			w.write("</div>\n");
			w.write("</body></html>\n");
		}
		return file;
	}
}
