package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
 * A box of {@code width: fit-content} moved whole to the next page keeps its width (2026-10-09, shadcn's docs: a tab
 * list came out the width of the page). Replayed from its source, it lost the intrinsic width keyword (the replayed
 * params did not carry it); restyled as a box (an absolutely positioned descendant keeps it from replaying),
 * startFlowBlock gave it the block width.
 */
public class MovedBoxWidthTest extends TestCase {
	private static final Pattern FRAME = Pattern.compile("AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static final String STYLE = "@page { size: A4; margin: 36pt } body { margin: 0; font: 12pt/1.5 sans-serif }"
			+ " .tabs { margin-top: 18pt } .list { width: fit-content; height: 27pt; padding: 2.25pt 0;"
			+ " box-sizing: border-box; align-items: center; gap: 12pt; background: #ddd } .tab { display: inline-flex;"
			+ " height: calc(100% - 1px); align-items: center; padding: 3pt 0 9pt; border: 0;"
			+ " border-bottom: 2px solid #000; font: 12pt sans-serif; background: #fff }";

	private static final String AFTER = " .tab { position: relative } .tab::after { content: \"\"; position: absolute;"
			+ " left: 0; right: 0; bottom: -5px; height: 2px; background: #000 }";

	/** A flex list restyled as a box (its buttons have absolutely positioned pseudo-elements). */
	public void testFlexRestyledAsABox() throws Exception {
		assertSameWidths("flex-box", " .list { display: flex }" + AFTER);
	}

	/** A flex list replayed from its source. */
	public void testFlexReplayed() throws Exception {
		assertSameWidths("flex-replay", " .list { display: flex }");
	}

	/** A block replayed from its source. */
	public void testBlockReplayed() throws Exception {
		assertSameWidths("block-replay", " .list { display: block }");
	}

	private static void assertSameWidths(final String name, final String style) throws Exception {
		final List<String> top = widths(convert(name + "-top", document(style, 0)));
		final List<String> moved = widths(convert(name + "-moved", document(style, 745)));
		assertEquals("widths of the boxes moved to the next page", top, moved);
	}

	private static String document(final String style, final int before) {
		return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>" + STYLE + style
				+ "</style></head><body><div style=\"height: " + before + "pt\"></div><div class=\"tabs\">"
				+ "<div class=\"list\"><button class=\"tab\">Command</button><button class=\"tab\">Manual</button>"
				+ "</div></div><p>END</p></body></html>";
	}

	/** The widths of the frames drawn on the page with the list, in order. */
	private static List<String> widths(final List<String> pages) {
		final String page = pages.stream().filter(p -> p.contains("Text[\"Command\"")).findFirst().orElseThrow();
		final List<String> widths = new ArrayList<>();
		final Matcher m = FRAME.matcher(page);
		while (m.find()) {
			widths.add(m.group(1));
		}
		return widths;
	}

	private static List<String> convert(final String name, final String html) throws Exception {
		final File dir = Files.createTempDirectory("moved-box-" + name).toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] files = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(name + ": no pages", files);
		Arrays.sort(files);
		final List<String> pages = new ArrayList<>();
		for (final File f : files) {
			pages.add(Files.readString(f.toPath(), StandardCharsets.UTF_8));
		}
		return pages;
	}
}
