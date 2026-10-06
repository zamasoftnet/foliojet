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
 * 和文の両端揃え({@code text-justify: auto})で、欧文の語の字間を空けないことを固定します(2026-10-06、jigensha の報告:
 * 長い欧文の語で行末が空いた行で「T o r B r o w s e r」と字間まで空いた)。JLREQ 3.8.4 の d は分割禁止でない字間を
 * 均等に空ける。欧文用文字の字間を含めるかは JIS X 4051 で処理系定義で、Copper は和字間・語間など配る所が無い行
 * だけ欧文の字間へ配る。{@code inter-character} は従来どおり欧文の字間にも配る。
 */
public class JustifyWesternLettersTest extends TestCase {
	private static final String TEXT = "あいうえおかきくけこさしすせそたちつてと。TorBrowser に、abcdefghijklmnopqrstuvwxyz0123456789 のような。";

	public void testAutoKeepsLettersSolid() throws Exception {
		assertEquals(0, gap("auto"), 0.01);
	}

	public void testInterCharacterSpreadsLetters() throws Exception {
		assertTrue(gap("inter-character") > 0.1);
	}

	/** 「TorBrowser」の字と字の間のアキの最大(pt)。 */
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
