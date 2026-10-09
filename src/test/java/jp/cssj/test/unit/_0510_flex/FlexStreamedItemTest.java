package jp.cssj.test.unit._0510_flex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.SourceReplayer;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.foliojet.ua.impl.pdf.PDFUserAgent;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * The single item of a row flex that fills its line is laid out in the flow and breaks across pages as a block does
 * (2026-10-09, closed-after C of copperpdf4/docs/design/retained-container-relayout-design.md). Bound whole, the item
 * was split page by page and what was left of it was laid out again on every page: an app shell with
 * {@code body { display: flex }} and one {@code main} (rustdoc-std: 427000 boxes laid out again over 163 pages).
 */
public class FlexStreamedItemTest extends TestCase {
	private static final int PARAGRAPHS = 240;

	private static final String TEXT = " lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor.";

	private static String html(final String container) {
		final StringBuilder paras = new StringBuilder();
		for (int i = 0; i < PARAGRAPHS; ++i) {
			paras.append("<p>P").append(1000 + i).append(TEXT).append("</p>");
		}
		return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>"
				+ "@page { size: 300pt 300pt; margin: 20pt } body { margin: 0; font: 10pt/15pt serif }"
				+ " p { margin: 0 0 6pt } main { padding: 0 10pt; background: #eee }"
				+ "</style></head><body><div style=\"" + container + "\"><main>" + paras
				+ "</main></div><p>END</p></body></html>";
	}

	/** The item comes out on the pages exactly as the same content in a block. */
	public void testBreaksLikeABlock() throws Exception {
		final Result flex = convert("flex", html("display: flex"));
		final Result block = convert("block", html(""));
		assertEquals("pages", block.pages.size(), flex.pages.size());
		for (int i = 0; i < block.pages.size(); ++i) {
			assertEquals("page " + (i + 1), block.pages.get(i), flex.pages.get(i));
		}
	}

	/**
	 * Breaking the item lays out again only what crosses each page break, as for a block: the closed paragraphs after
	 * the break were all replayed on every page before.
	 */
	public void testLaysOutTheRestOnce() throws Exception {
		final Result flex = convert("flex-replays", html("display: flex"));
		final Result block = convert("block-replays", html(""));
		assertTrue("replays " + flex.replays + " (block " + block.replays + ")", flex.replays <= block.replays + 2);
	}

	/** The text the item streams is not counted again as text the container holds back. */
	public void testRetainedTextCountedOnce() throws Exception {
		final Result flex = convert("flex-retained", html("display: flex"));
		final long payload = 2L * PARAGRAPHS * ("P1000" + TEXT).length();
		// Counted twice, it came to twice the text (once measuring the item, once laying it out)
		assertTrue("retained " + flex.retained + " for text " + payload, flex.retained < payload * 3 / 2);
	}

	private record Result(List<String> pages, long replays, long retained) {
	}

	private static Result convert(final String name, final String html) throws Exception {
		final File dir = Files.createTempDirectory("flex-streamed-" + name).toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		final PDFUserAgent ua = new PDFUserAgent() {
		};
		final long replays0 = SourceReplayer.SUBTREE_REPLAYS.get();
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setUserAgent(ua);
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.property("processing.retained-text-limit", "0");
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final long replays = SourceReplayer.SUBTREE_REPLAYS.get() - replays0;
		final File[] files = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(name + ": no pages", files);
		Arrays.sort(files);
		final List<String> pages = new ArrayList<>();
		for (final File f : files) {
			pages.add(Files.readString(f.toPath(), StandardCharsets.UTF_8));
		}
		return new Result(pages, replays, ua.getRetainedTextLimit().getHighWater());
	}
}
