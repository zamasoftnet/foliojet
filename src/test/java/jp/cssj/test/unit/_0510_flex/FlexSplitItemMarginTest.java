package jp.cssj.test.unit._0510_flex;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

/**
 * An item of a row broken across pages keeps its margin-right outside its width on the next page (2026-10-15, the
 * faces of a textbook). The 40pt image does not fit below the spacer and goes on with the rest of its row, whose text
 * starts on the first page. Rebuilt there, the block rules for an over-constrained box zeroed its margin-right and the
 * outer width the row had given it went into the image: drawn 50 x 40pt, an oval instead of a circle. A block item,
 * which breaks there instead of moving on whole, kept its width before as after.
 */
public class FlexSplitItemMarginTest extends TestCase {
	private static final String PNG = "data:image/png;base64,"
			+ "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";
	private static final Pattern SHADOWED = Pattern.compile("x=([-\\d.]+) y=[-\\d.]+ AbsoluteRectFrame\\[w=([\\d.]+) "
			+ "h=([\\d.]+)\\] shadow");

	public void testImage() throws Exception {
		// The image's frame is its border box: the wrapper holds its margins
		this.check("<img src=\"" + PNG + "\" style=\"flex: none; width: 40pt; height: 40pt; margin-right: 10pt;"
				+ " box-shadow: 2pt 2pt 0 red\">", 40, 2);
	}

	public void testBlock() throws Exception {
		// A block's frame takes in its margins (50pt); it breaks, 30pt on the first page and 10pt on the next
		this.check("<div style=\"flex: none; width: 40pt; height: 40pt; margin-right: 10pt; background: #ccc;"
				+ " box-shadow: 2pt 2pt 0 red\"></div>", 50, 3);
	}

	private void check(final String item, final double frameWidth, final int frames) throws Exception {
		final String line = "<div style=\"display: flex\">" + item
				+ "<div>Name<p style=\"margin: 0\">This is a line of text.</p></div></div>";
		final String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><style>"
				+ "@page { size: 200pt 200pt; margin: 0 } body { margin: 0; font: 10pt/14pt serif }</style></head><body>"
				+ "<div style=\"height: 170pt; background: #eee\"></div>" + line + line + "</body></html>";
		final File dir = Files.createTempDirectory("flex-split-item-margin").toFile();
		final File input = new File(dir, "input.html");
		Files.writeString(input.toPath(), html, StandardCharsets.UTF_8);
		try (DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"), null);
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				AutoCloseable dump = DisplayListDumper.scopedDir(dir.getPath())) {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			CTISessionHelper.transcodeFile(session, input, "text/html", null);
		}
		final File[] pages = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		java.util.Arrays.sort(pages);
		assertEquals("pages", 2, pages.length);
		final List<String> items = new ArrayList<>();
		for (final File page : pages) {
			final Matcher m = SHADOWED.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
			while (m.find()) {
				items.add(page.getName() + " x=" + m.group(1) + " " + m.group(2) + "x" + m.group(3));
				assertEquals(page.getName() + " width", frameWidth, Double.parseDouble(m.group(2)), 0.01);
			}
		}
		assertEquals("items " + items, frames, items.size());
	}
}
