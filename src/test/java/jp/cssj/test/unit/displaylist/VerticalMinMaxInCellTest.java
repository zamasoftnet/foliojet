package jp.cssj.test.unit.displaylist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * In vertical writing, a block's {@code min-width} and {@code max-width} constrain its block axis (2026-10-26, code review
 * of css-second-wave §4).
 *
 * <p>
 * The trial layout of a block in a vertical container ({@code AbstractBlockBox.firstPassLayout}) took the line-axis
 * (physical height) limits from the width components, so the {@code min-width:4em} of a box in a vertical table cell
 * made the box 4em tall too and stretched the column. {@code FlowBlockBox.calculateSize} read the height components.
 * </p>
 */
public class VerticalMinMaxInCellTest extends TestCase {
	private static final Pattern FRAME = Pattern.compile("AbsoluteRectFrame\\[w=([-0-9.]+) h=([-0-9.]+)\\]");

	public void testMinWidthOfABoxInAVerticalCellDoesNotStretchItsHeight() throws Exception {
		final String table = "<table><tr><th>品目</th><th>評価</th></tr><tr><td>米</td><td><div class='%s'>良</div></td></tr>"
				+ "<tr><td>麦</td><td>可</td></tr></table>";
		final Path dir = convert("<!DOCTYPE html><html lang='ja'><head><meta charset='UTF-8'><style>"
				+ "@page{size:A5;margin:15mm} html{writing-mode:vertical-rl;font:10pt/1.6 serif} body{margin:0}"
				+ "table{border-collapse:collapse;margin-left:12pt} th,td{border:0.5pt solid #444;padding:2pt 4pt}"
				+ ".mark{min-width:4em;border:0.5pt solid #c00} .mark0{border:0.5pt solid #c00}"
				+ "</style></head><body>" + String.format(table, "mark") + String.format(table, "mark0") + "</body></html>");
		final String dump = Files.readString(dir.resolve("page-0001.txt"));
		final List<double[]> frames = new ArrayList<>();
		final Matcher m = FRAME.matcher(dump);
		while (m.find()) {
			frames.add(new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) });
		}
		assertEquals(dump, 2, frames.size());
		// The min-width widens the box in the block axis (physical width) only.
		assertTrue(dump, frames.get(0)[0] >= 40);
		assertEquals(dump, frames.get(1)[1], frames.get(0)[1], 0.01);
	}

	private static Path convert(final String html) throws Exception {
		final Path dir = Files.createTempDirectory("vertical-min-max-cell");
		try (AutoCloseable d = DisplayListDumper.scopedDir(dir.toString())) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
					null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(new ByteArrayOutputStream())));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				CTISessionHelper.transcodeStream(session,
						new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)), URI.create("file:///a.html"),
						"text/html", null);
			} finally {
				session.close();
			}
		}
		return dir;
	}
}
