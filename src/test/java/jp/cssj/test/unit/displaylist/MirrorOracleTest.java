package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import jp.cssj.test.unit.displaylist.MirrorDocumentGenerator.Family;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;

/**
 * The sweep's writing-mode mirror check (mirror-v1, 2026-10-27, {@link MirrorDocumentGenerator},
 * {@link MirrorComparison}): its mapping, its generator, and the defects it was made to see, each found by code review
 * and never by the sweep (docs/design/2026-10-27-sweep-writing-mode-mirror.md).
 */
public class MirrorOracleTest extends TestCase {
	/** A quarter turn and a transpose of one box, and a box off by more than the tolerance. */
	public void testMapping() {
		final String h = "drawer z=0\n  x=10.00 y=20.00 AbsoluteRectFrame[w=30.00 h=40.00]\n"
				+ "  x=10.00 y=20.00 Text[\"T1\" asc=8.00 desc=2.00] w=12.00 h=10.00 clip=[0.00 0.00 100.00 50.00]\n";
		final String r = "drawer z=0\n  x=240.00 y=10.00 AbsoluteRectFrame[w=40.00 h=30.00]\n"
				+ "  x=270.00 y=10.00 Text[\"T1\" asc=5.00 desc=5.00] w=10.00 h=12.00 clip=[250.00 0.00 50.00 100.00]\n";
		assertNull(MirrorComparison.compare(Family.ROTATE, 300, List.of(h), List.of(r)));
		final String t = "drawer z=0\n  x=20.00 y=10.00 AbsoluteRectFrame[w=40.00 h=30.00]\n"
				+ "  x=20.00 y=10.00 Text[\"T1\" asc=5.00 desc=5.00] w=10.00 h=12.00 clip=[0.00 0.00 50.00 100.00]\n";
		assertNull(MirrorComparison.compare(Family.TRANSPOSE, 300, List.of(h), List.of(t)));
		assertNotNull(MirrorComparison.compare(Family.ROTATE, 300, List.of(h), List.of(r.replace("240.00", "240.10"))));
		assertNotNull(MirrorComparison.compare(Family.ROTATE, 300, List.of(h), List.of(r, r)));
	}

	/** The same seed gives the same documents, and each variant has every token. */
	public void testGeneratorIsDeterministic() {
		for (int seed = 1; seed <= 40; ++seed) {
			final MirrorDocumentGenerator.Document a = MirrorDocumentGenerator.generate(seed),
					b = MirrorDocumentGenerator.generate(seed);
			assertEquals(a, b);
			for (final String token : a.tokens()) {
				assertTrue(seed + " " + token, a.horizontal().contains(token) && a.vertical().contains(token));
			}
		}
	}

	/**
	 * A cell's first line aligned on its baseline counted the frame of the line's end side in vertical writing: a
	 * padding at the cell's block start left the cells unaligned, one at its block end moved the other cell
	 * (FlowContainer.getFirstAscent).
	 */
	public void testCellBaselineFrameSide() throws Exception {
		for (final String side : new String[] { "start", "end" }) {
			assertMirrored("<table style=\"border-spacing: 0\"><tr><td style=\"vertical-align: baseline; "
					+ "padding-block-" + side + ": 20pt\">T1<br>T2</td><td style=\"vertical-align: baseline\">T3</td>"
					+ "</tr></table>");
		}
	}

	/**
	 * The margin a table or a block image starts with collapsed with the preceding one by its physical top in vertical
	 * writing too (BlockBuilder.addFlowBound): margin-block: 10pt was added to the paragraph's (20pt), and a physical
	 * margin: 10pt 0, written for horizontal writing, took the paragraph's away (0pt).
	 */
	public void testTableAndImageMarginCollapse() throws Exception {
		final String img = "<img src=\"" + RandomDocumentFuzzTest.RED_PNG_URI + "\" style=\"display: block; "
				+ "inline-size: 10pt; block-size: 10pt; ";
		for (final String margin : new String[] { "margin-block: 10pt", "margin-block-start: 10pt", "margin-inline: 10pt" }) {
			assertMirrored("<p style=\"margin-block: 10pt\">T1</p><table style=\"" + margin
					+ "\"><tr><td>T2</td></tr></table><p style=\"margin-block: 10pt\">T3</p>" + img + margin
					+ "\"><p>T4</p>");
		}
	}

	/**
	 * border-spacing's two values are the inline and block spacings, as in Chrome; a vertical table took them as
	 * horizontal and vertical (cells 20pt apart and rows 2pt with 2pt 20pt; found by the mirror check).
	 */
	public void testBorderSpacingAxes() throws Exception {
		assertMirrored("<table style=\"border-spacing: 2pt 20pt\"><tr><td style=\"border: 1pt solid\">T1</td><td>T2</td>"
				+ "</tr><tr><td>T3</td><td style=\"border: 1pt solid\">T4</td></tr></table>");
	}

	/**
	 * A block of vertical writing with margin-inline-end: auto took its undetermined margin for its height in resolving
	 * it (FlowBlockBox.calculateSize, since 2026-05): its frame was drawn -1.7e308pt high (found by the mirror check).
	 */
	public void testAutoInlineEndMargin() throws Exception {
		assertMirrored("<div style=\"margin-inline-end: auto; border: 1pt solid; inline-size: 100pt\"><p>T1</p></div>"
				+ "<div style=\"margin-inline: auto; border: 1pt solid; inline-size: 80%\"><p>T2</p></div>");
	}

	/**
	 * A cell of the other axis was as thick as one line of its content (triage §113, RetainedTableBuilder and
	 * IntrinsicMeasurer.fitBlock): the first line of a vertical heading of two lines ran into the next cell, the second
	 * line of a horizontal cell of a vertical table out of the table.
	 */
	public void testOrthogonalCellContent() throws Exception {
		assertConfined("horizontal-tb", "<table><tr><th style=\"writing-mode: vertical-rl\">T1<br>T2</th><td>T3 T4</td>"
				+ "</tr></table>", "T1 T2", "T3 T4");
		assertConfined("horizontal-tb", "<table><tr><th><div style=\"writing-mode: vertical-lr\">T1<br>T2</div></th>"
				+ "<td>T3 T4</td></tr></table>", "T1 T2", "T3 T4");
		assertConfined("vertical-rl", "<table><tr><td>T1</td><td style=\"writing-mode: horizontal-tb\">T2<br>T3</td>"
				+ "</tr></table>", "T1", "T2 T3");
	}

	/** The body laid out horizontal-tb on 200x300pt and vertical-rl on 300x200pt draws the same, turned. */
	private static void assertMirrored(final String body) throws Exception {
		final String difference = MirrorComparison.compare(Family.ROTATE, 300,
				dumps(document("horizontal-tb", 200, 300, body)), dumps(document("vertical-rl", 300, 200, body)));
		assertNull(body + ": " + difference, difference);
	}

	/** Each cell's tokens, space-separated, in its own cell. */
	private static void assertConfined(final String writingMode, final String body, final String... cells)
			throws Exception {
		final List<Set<String>> groups = new ArrayList<>();
		for (final String cell : cells) {
			groups.add(Set.of(cell.split(" ")));
		}
		final String outside = MirrorComparison.confinement(dumps(document(writingMode, 300, 300,
				body.replace("<td", "<td style=\"border: 1pt solid\"").replace("<th", "<th style=\"border: 1pt solid\"")
						.replace("\" style=\"", "; "))),
				groups);
		assertNull(body + ": " + outside, outside);
	}

	private static String document(final String writingMode, final int width, final int height, final String body) {
		return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><style>@page { size: " + width + "pt " + height
				+ "pt; margin: 0 } html { writing-mode: " + writingMode + "; text-orientation: sideways } "
				+ "body { margin: 0; font: 10pt/12pt serif }</style></head><body>" + body + "</body></html>";
	}

	private static List<String> dumps(final String html) throws Exception {
		final File dir = Files.createTempDirectory("mirror-oracle").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath());
				AutoCloseable geometry = DisplayListDumper.scopedDetailedGeometry(true)) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] pages = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		Arrays.sort(pages);
		final List<String> texts = new ArrayList<>();
		for (final File page : pages) {
			texts.add(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return texts;
	}
}
