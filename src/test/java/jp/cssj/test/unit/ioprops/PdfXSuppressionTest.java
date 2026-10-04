package jp.cssj.test.unit.ioprops;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * PDF/X・PDF/A で使えない機能(リンク・添付・JavaScript)を警告して落とし、変換を失敗させないことの試験です
 * (2026-09-30、PDF/X-3 対応)。
 *
 * <p>
 * pdfg2d はこれらを PDF/X・PDF/A で例外にするので、foliojet が渡すと変換が途中で失敗する。PDF/X-4 に
 * {@code output.pdf.hyperlinks=true} で本文のリンクがあると、実際に変換が失敗していた。
 * </p>
 */
public class PdfXSuppressionTest extends TestCase {
	private static final String HTML = "<html><head><title>links</title></head><body>"
			+ "<p><a href=\"https://example.com/\">external</a> and <a href=\"#end\">internal</a></p>"
			+ "<p id=\"end\">end</p></body></html>";

	public void testPdfX3DropsHyperlinksWithWarning() throws Exception {
		final Conversion result = convert(Map.of("output.pdf.version", "1.4X-3", "output.pdf.hyperlinks", "true"));
		assertSucceededWithWarning(result);
		assertFalse("PDF/X-3にリンク注釈を書かないこと", result.pdf().contains("/Annots"));
	}

	public void testPdfX4DropsHyperlinksInsteadOfFailing() throws Exception {
		final Conversion result = convert(Map.of("output.pdf.version", "1.6X-4", "output.pdf.hyperlinks", "true"));
		assertSucceededWithWarning(result);
		assertFalse("PDF/X-4の本文にリンク注釈を書かないこと", result.pdf().contains("/Annots"));
	}

	public void testPdfX4DropsAttachmentsInsteadOfFailing() throws Exception {
		final Path attachment = Files.createTempFile("foliojet-pdfx-attachment-", ".txt");
		try {
			Files.writeString(attachment, "attachment");
			final Conversion result = convert(Map.of("output.pdf.version", "1.6X-4",
					"output.pdf.attachments.0.uri", attachment.toUri().toString()));
			assertSucceededWithWarning(result);
			assertFalse("PDF/X-4に添付を書かないこと", result.pdf().contains("/EmbeddedFiles"));
		} finally {
			Files.deleteIfExists(attachment);
		}
	}

	public void testPdfX3DropsOpenActionJavaScript() throws Exception {
		final Conversion result = convert(Map.of("output.pdf.version", "1.4X-3",
				"output.pdf.open-action.java-script", "app.alert('x');"));
		assertSucceededWithWarning(result);
		assertFalse("PDF/X-3にJavaScriptを書かないこと", result.pdf().contains("/JavaScript"));
	}

	public void testPlainPdfKeepsHyperlinks() throws Exception {
		final Conversion result = convert(Map.of("output.pdf.version", "1.7", "output.pdf.hyperlinks", "true"));
		assertNull("通常PDFの変換は成功すること", result.failure());
		assertTrue("通常PDFはURIリンクを書くこと", result.pdf().contains("/URI"));
	}

	private static void assertSucceededWithWarning(final Conversion result) {
		assertNull("変換が失敗しないこと: " + result.failure(), result.failure());
		assertTrue("WARN_UNSUPPORTED_PDF_CAPABILITYで報告すること",
				result.messages().contains(Short.valueOf(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY)));
	}

	private static Conversion convert(final Map<String, String> properties) throws Exception {
		final Path html = Files.createTempFile("foliojet-pdfx-suppression-", ".html");
		try {
			Files.writeString(html, HTML);
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final List<Short> messages = new ArrayList<>();
			Exception failure = null;
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(new MessageHandler() {
					@Override
					public void message(final short code, final String[] args, final String message) {
						messages.add(Short.valueOf(code));
					}
				});
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("output.type", "application/pdf");
				session.property("output.pdf.compression", "none");
				for (final Map.Entry<String, String> e : properties.entrySet()) {
					session.property(e.getKey(), e.getValue());
				}
				CTISessionHelper.transcodeFile(session, new File(html.toString()), "text/html", null);
			} catch (final TranscoderException e) {
				failure = e;
			} finally {
				session.close();
			}
			return new Conversion(new String(out.toByteArray(), StandardCharsets.ISO_8859_1), List.copyOf(messages),
					failure);
		} finally {
			Files.deleteIfExists(html);
		}
	}

	private record Conversion(String pdf, List<Short> messages, Exception failure) {
	}
}
