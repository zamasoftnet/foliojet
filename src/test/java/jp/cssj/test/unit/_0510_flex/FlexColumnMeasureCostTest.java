package jp.cssj.test.unit._0510_flex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.builder.impl.FlexBuilder;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * What measuring the content of retained column flex items costs (codex review 2026-10-08).
 *
 * <ul>
 * <li>The measurement copy is counted apart from the live container, as table Pass B does: a centered column holding
 * one paragraph retains no more text than the same paragraph with a fixed height, which is not measured (the copy was
 * charged to the container and the bind charged the text again, so a limit twice the text was exceeded).</li>
 * <li>Nested centered columns reuse the measurements of the same ranges: 12 levels bind a few hundred items, not
 * 2^12, and retain about as much text as one level (20 levels exceeded the 16 MiB limit with 9 characters).</li>
 * <li>A centered column with a long body is not retained whole (its per-page relayout grew with pages times
 * content), and the remainder of a retained row flex relaid after each break is not counted again.</li>
 * </ul>
 *
 * <p>
 * The first two use {@code column-reverse}, which is still retained and measured; a column with only
 * {@code align-items: center} stays in normal flow since 2026-10-09.
 * </p>
 */
public class FlexColumnMeasureCostTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final String HEAD = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body>";

	private static final String CENTERED = "<div style=\"display:flex;flex-direction:column;align-items:center;width:200pt\">";

	/** Retained and measured: column-reverse places its items as a whole. */
	private static final String RETAINED = "<div style=\"display:flex;flex-direction:column-reverse;align-items:center;width:200pt\">";

	public void testMeasurementCopyIsNotChargedToTheContainer() throws Exception {
		final StringBuilder text = new StringBuilder();
		for (int i = 0; i < 120; ++i) {
			text.append("word").append(i).append(' ');
		}
		final long fixed = highWater(HEAD + RETAINED + "<div style=\"height:200pt\">" + text + "</div></div></body></html>");
		final long measured = highWater(HEAD + RETAINED + "<div>" + text + "</div></div></body></html>");
		assertTrue("fixed " + fixed, fixed > 1000);
		assertTrue("measured " + measured + " vs fixed " + fixed, measured <= fixed * 1.1);
	}

	public void testNestedColumnsReuseMeasurements() throws Exception {
		final long before = FlexBuilder.FLEX_ITEM_BINDS.get();
		final long one = highWater(nested(1));
		final long binds = FlexBuilder.FLEX_ITEM_BINDS.get();
		assertTrue("one level binds " + (binds - before), binds > before);
		final long twelve = highWater(nested(12));
		final long nestedBinds = FlexBuilder.FLEX_ITEM_BINDS.get() - binds;
		assertTrue("binds " + nestedBinds, nestedBinds < 500);
		assertTrue("retained " + twelve + " vs one level " + one, twelve <= one * 2);
	}

	/**
	 * A centered column holding a long body stays in normal flow (2026-10-09): retained whole, every page relaid all
	 * the remaining content, so the retained text grew with pages times content and 2000 paragraphs exceeded the
	 * default 16 MiB limit (19124, 19125). It stays near one copy of the body now.
	 */
	public void testLongCenteredColumnIsNotRetainedWhole() throws Exception {
		final StringBuilder html = new StringBuilder(HEAD).append(CENTERED).append("<div>");
		for (int i = 0; i < 2000; ++i) {
			html.append("<p>段落 ").append(i)
					.append("。印刷向けの組版で中央寄せの列が長い本文を持つときの保持量を確かめる本文です。The quick brown fox jumps over the lazy dog.</p>");
		}
		html.append("</div></div></body></html>");
		final long retained = highWater(html.toString());
		assertTrue("retained " + retained, retained < 4L * 1024 * 1024);
	}

	/**
	 * A row flex holding a long body is retained whole and its remainder relaid after every break; the relaid text was
	 * counted when first laid out, so it is not counted again (2026-10-09). 1800 paragraphs went over the default
	 * 16 MiB limit with the count growing with pages times content; the high water is about twice the body now.
	 * The relayout time still grows with pages times content (PLAN §6).
	 */
	public void testLongRowFlexRelayIsNotCountedAgain() throws Exception {
		final StringBuilder html = new StringBuilder(HEAD).append("<div style=\"display:flex\"><div>");
		for (int i = 0; i < 1800; ++i) {
			html.append("<p>").append(i).append(
					" Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.</p>");
		}
		html.append("</div></div></body></html>");
		final long retained = highWater(html.toString());
		assertTrue("retained " + retained, retained < 4L * 1024 * 1024);
	}

	private static String nested(final int depth) {
		return HEAD + RETAINED.repeat(depth) + "leaf text" + "</div>".repeat(depth) + "</body></html>";
	}

	/** The retained-text high water of one conversion (no limit). */
	private static long highWater(final String html) throws Exception {
		final File file = File.createTempFile("flex-measure-cost-", ".html");
		try {
			Files.writeString(file.toPath(), html, StandardCharsets.UTF_8);
			final PDFUserAgent ua = new PDFUserAgent() {
			};
			try (final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
					final ByteArrayOutputStream out = new ByteArrayOutputStream()) {
				session.setUserAgent(ua);
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.property("processing.retained-text-limit", "0");
				CTISessionHelper.transcodeFile(session, file, "text/html", null);
				return ua.getRetainedTextLimit().getHighWater();
			}
		} finally {
			Files.deleteIfExists(file.toPath());
		}
	}
}
