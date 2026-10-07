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
import org.apache.pdfbox.text.TextPosition;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * Verify that justification of Japanese text ({@code text-justify: auto}) does not space out letters within Latin words
 * (2026-10-06, jigensha report: a line ending early due to a long Latin word expanded "T o r B r o w s e r").
 * JLREQ 3.8.4 d distributes space equally between characters where breaking is allowed. JIS X 4051 leaves inclusion
 * of gaps between Latin characters to the implementation; Copper uses them only when the line has no other
 * distribution points, such as Japanese character gaps or word spaces. {@code inter-character} still distributes
 * space between Latin letters as before.
 */
public class JustifyWesternLettersTest extends TestCase {
	private static final String TEXT = "あいうえおかきくけこさしすせそたちつてと。TorBrowser に、abcdefghijklmnopqrstuvwxyz0123456789 のような。";

	public void testAutoKeepsLettersSolid() throws Exception {
		assertEquals(0, gap("auto"), 0.01);
	}

	public void testInterCharacterSpreadsLetters() throws Exception {
		assertTrue(gap("inter-character") > 0.1);
	}

	/** Maximum gap (pt) between letters in "TorBrowser". */
	private static double gap(final String justify) throws Exception {
		final String html = """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml" lang="ja"><head><meta charset="UTF-8"/>
				<style>@page{size:100mm 120mm;margin:5mm}
				body{font-size:10pt;line-height:1.7;text-align:justify;text-justify:%s} p{margin:0;width:80mm}</style>
				</head><body><p>%s</p></body></html>
				""".formatted(justify, TEXT);
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			CTISessionHelper.transcodeStream(session, new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///justify-western.xhtml"), "application/xhtml+xml", null);
		} finally {
			session.close();
		}
		final List<TextPosition> chars = new ArrayList<>();
		try (PDDocument doc = Loader.loadPDF(out.toByteArray())) {
			final PDFTextStripper stripper = new PDFTextStripper() {
				@Override
				protected void processTextPosition(final TextPosition text) {
					chars.add(text);
				}
			};
			stripper.setSuppressDuplicateOverlappingText(false);
			stripper.getText(doc);
		}
		final StringBuilder all = new StringBuilder();
		for (final TextPosition t : chars) {
			all.append(t.getUnicode());
		}
		final int start = all.indexOf("TorBrowser");
		assertTrue(all.toString(), start >= 0);
		double max = 0;
		for (int i = start; i < start + "TorBrowser".length() - 1; ++i) {
			final TextPosition a = chars.get(i), b = chars.get(i + 1);
			max = Math.max(max, b.getXDirAdj() - (a.getXDirAdj() + a.getWidthDirAdj()));
		}
		return max;
	}
}
