package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.message.MessageHandler;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * URI に書けない字(空白・{@code %})を含む id を固定します(2026-10-06、jigensha の報告: {@code id="with space"}
 * に警告 10252「Illegal character in fragment」が出た)。リンクの側は {@code href="#with%20space"} と符号化して
 * 書くので、id の側も同じ形にして照合する。
 */
public class FragmentIdEncodingTest extends TestCase {
	private static final String HTML = """
			<!DOCTYPE html>
			<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
			<style>@page{size:100mm 60mm;margin:5mm} .t{page-break-before:always}
			a::after{content:" p" target-counter(attr(href), page)}</style></head><body>
			<p><a href="#with%20space">space</a></p>
			<p><a href="#a%25b">percent</a></p>
			<p><a href="#plain">plain</a></p>
			<p class="t" id="with space">target with space</p>
			<p class="t" id="a%b">target a%b</p>
			<p class="t" id="plain">target plain</p>
			</body></html>
			""";

	public void testNoWarningWithoutPageReferences() throws Exception {
		final List<String> warnings = new ArrayList<>();
		convert(false, warnings);
		assertEquals(warnings.toString(), 0, warnings.size());
	}

	public void testPageReferencesResolve() throws Exception {
		final List<String> warnings = new ArrayList<>();
		final byte[] pdf = convert(true, warnings);
		assertEquals(warnings.toString(), 0, warnings.size());
		try (PDDocument doc = Loader.loadPDF(pdf)) {
			final PDFTextStripper stripper = new PDFTextStripper();
			stripper.setStartPage(1);
			stripper.setEndPage(1);
			final String text = stripper.getText(doc);
			assertTrue(text, text.contains("space p2"));
			assertTrue(text, text.contains("percent p3"));
			assertTrue(text, text.contains("plain p4"));
		}
	}

	/** 変換して PDF を返し、警告 10252(0x280C)を集めます。 */
	private static byte[] convert(final boolean pageReferences, final List<String> warnings) throws Exception {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(new MessageHandler() {
				@Override
				public void message(final short code, final String[] args, final String message) {
					if (code == 0x280C) {
						warnings.add(message);
					}
				}
			});
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			if (pageReferences) {
				session.property("processing.page-references", "true");
				session.property("processing.pass-count", "2");
			}
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(HTML.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///fragment-id.xhtml"), "application/xhtml+xml", null);
		} finally {
			session.close();
		}
		return out.toByteArray();
	}
}
