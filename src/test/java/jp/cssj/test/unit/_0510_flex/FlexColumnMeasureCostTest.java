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
 * </ul>
 */
public class FlexColumnMeasureCostTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");

	private static final String HEAD = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"></head><body>";

	private static final String CENTERED = "<div style=\"display:flex;flex-direction:column;align-items:center;width:200pt\">";

	public void testMeasurementCopyIsNotChargedToTheContainer() throws Exception {
		final StringBuilder text = new StringBuilder();
		for (int i = 0; i < 120; ++i) {
			text.append("word").append(i).append(' ');
		}
		final long fixed = highWater(HEAD + CENTERED + "<div style=\"height:200pt\">" + text + "</div></div></body></html>");
		final long measured = highWater(HEAD + CENTERED + "<div>" + text + "</div></div></body></html>");
		assertTrue("fixed " + fixed, fixed > 1000);
		assertTrue("measured " + measured + " vs fixed " + fixed, measured <= fixed * 1.1);
	}

	public void testNestedColumnsReuseMeasurements() throws Exception {
		final long one = highWater(nested(1));
		final long binds = FlexBuilder.FLEX_ITEM_BINDS.get();
		final long twelve = highWater(nested(12));
		final long nestedBinds = FlexBuilder.FLEX_ITEM_BINDS.get() - binds;
		assertTrue("binds " + nestedBinds, nestedBinds < 500);
		assertTrue("retained " + twelve + " vs one level " + one, twelve <= one * 2);
	}

	private static String nested(final int depth) {
		return HEAD + CENTERED.repeat(depth) + "leaf text" + "</div>".repeat(depth) + "</body></html>";
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
