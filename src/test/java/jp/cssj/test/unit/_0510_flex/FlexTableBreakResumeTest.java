package jp.cssj.test.unit._0510_flex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * The text after a flex or grid container still breaks into pages when a table row of its item broke the page inside the
 * container (2026-10-09, stripe-docs). Both are page-atomic: inside them, automatic page breaks are held
 * back by a depth count, which the table row bypasses. Resume opened the boxes again and counted them on top of the
 * old count, so the count never came back and the 120 paragraphs below ran thousands of points past the bottom of the
 * last page.
 */
public class FlexTableBreakResumeTest extends TestCase {
	private static final Pattern Y = Pattern.compile(" y=(-?[\\d.]+) Text\\[");
	private static final String JP1 = "Billing、Tax、Adaptive Pricing、Stripe Managed Payments、Link、動的な決済手段、追加手数料、"
			+ "支払い方法の分割に対応する UI を標準搭載";

	public void testTextAfterFlexBreaks() throws Exception {
		assertTextAfterTheContainerBreaks("height: 100%; display: flex");
	}

	/** A grid container is page-atomic too, with any height. */
	public void testTextAfterGridBreaks() throws Exception {
		assertTextAfterTheContainerBreaks("display: grid");
	}

	private static void assertTextAfterTheContainerBreaks(final String container) throws Exception {
		final StringBuilder body = new StringBuilder("<div class=\"c\"><div>"
				+ "<div style=\"height: 393.82pt\"></div><div style=\"height: 393.82pt\"></div>"
				+ "<table><tr><td class=\"w\"></td><td>" + JP1 + "</td><td>" + JP1 + "</td>"
				+ "<td>Adaptive Pricing、Link、動的な決済手段に対応する UI を標準搭載</td></tr>"
				+ "<tr><td>小計 (税金と配送料を含む)、クロスセルとアップセル、無料トライアル、割引、プロモーションコードを含む、"
				+ "完全な注文概要を提供</td></tr></table></div></div>");
		for (int i = 0; i < 120; ++i) {
			body.append("<p>Filler paragraph ").append(i)
					.append(" lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor.</p>");
		}
		final File dir = Files.createTempDirectory("flex-table-break-resume").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ ".c { " + container + " } table { width: 100%; table-layout: fixed }"
				+ " td { box-sizing: border-box; padding: 16px } td.w { width: 15% }</style></head><body>" + body
				+ "</body></html>", StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] pages = dir.listFiles((d, name) -> name.startsWith("page-") && name.endsWith(".txt"));
		Arrays.sort(pages);
		double maxY = 0;
		boolean last = false;
		for (final File page : pages) {
			final String text = Files.readString(page.toPath(), StandardCharsets.UTF_8);
			final Matcher m = Y.matcher(text);
			while (m.find()) {
				maxY = Math.max(maxY, Double.parseDouble(m.group(1)));
			}
			last |= text.contains("Text[\"119\"");
		}
		assertTrue("the last paragraph is drawn", last);
		assertTrue("no text below the page: " + maxY + " on " + pages.length + " pages", maxY < 800);
	}
}
