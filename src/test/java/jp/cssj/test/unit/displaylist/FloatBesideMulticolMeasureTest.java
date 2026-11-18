package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.draw.DisplayListDumper;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * A float beside a multicol widens a shrink-to-fit box by its own width, once (2026-11-18, fit seed 130904989, triage
 * §241).
 *
 * <p>
 * The max-content size of a line adds the floats before it and is then multiplied by the column count, as the line is
 * in each column. The floats before a multicol were still in that sum inside the multicol, so they were counted once per
 * column: a 300pt float beside a four-column multicol of 20pt words made the box 86 + 4 x 300 = 1286pt wide, where
 * Chrome makes it 300 + 86 = 386pt. In a grid's max-content track in vertical writing the track went 900pt past the
 * paper. The multicol is a formatting-context root: the floats lie beside it once, and its columns start without them.
 * </p>
 */
public class FloatBesideMulticolMeasureTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"([^\"]*)\"");

	/** A word of the given length along the line, horizontal or vertical. */
	private static String word(final String text, final double length, final boolean vertical) {
		return "<span style='display:inline-block;" + (vertical ? "height:" + length + "pt;width:10pt" : "width:" + length
				+ "pt;height:10pt") + "'>" + text + "</span>";
	}

	/** A float of the given side holding a 300pt word, then a four-column multicol of three 20pt words. */
	private static String content(final String side, final boolean vertical) {
		return "<div style='float:" + side + "'><p>" + word("T0Z", 300, vertical) + "</p></div>"
				+ "<div style='column-count:4;column-gap:2pt'><p>" + word("T1Z", 20, vertical) + "</p><p>"
				+ word("T2Z", 20, vertical) + "</p><p>" + word("T3Z", 20, vertical) + "</p></div>";
	}

	private static String document(final String body, final boolean vertical) {
		return "<!DOCTYPE html><html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en\"><head><meta charset=\"UTF-8\"/><style>"
				+ "@page { size: " + (vertical ? "400pt 1000pt" : "1000pt 400pt") + "; margin: 0 } body { margin: 0; "
				+ "font: 10pt/12pt serif" + (vertical ? "; writing-mode: vertical-rl" : "") + " } p { margin: 0 }"
				+ "td { padding: 0 } table { border-spacing: 0 }</style></head><body>" + body + "</body></html>";
	}

	/** The shrink-to-fit hosts: each is 386pt along the line, so a right float starts at 86pt. */
	private static final String[][] HOSTS = { //
			{ "inline-block", "<div style='display:inline-block'>", "</div>" }, //
			{ "cell", "<table><tr><td>", "</td></tr></table>" }, //
			{ "float", "<div style='float:left'>", "</div>" }, //
			{ "grid-max-content", "<div style='display:grid;grid-template-columns:max-content'><div>", "</div></div>" } };

	public void testRightFloatStartsPastTheColumns() throws Exception {
		final List<String> wrong = new ArrayList<>();
		for (final boolean vertical : new boolean[] { false, true }) {
			for (final String[] host : HOSTS) {
				final String name = (vertical ? "v-" : "h-") + host[0];
				final List<String> pages = convertPages(name, document(host[1] + content("right", vertical) + host[2], vertical));
				final double[] t0 = position(pages, "T0Z");
				final double start = vertical ? t0[1] : t0[0];
				if (Math.abs(start - 86) > 0.5) {
					wrong.add(name + ": 浮動の字の位置 " + start + "(86 のはず)");
				}
			}
		}
		assertTrue(wrong.toString(), wrong.isEmpty());
	}

	/** The columns beside a left float are as wide as their words, not stretched by the float. */
	public void testColumnsBesideLeftFloatKeepTheirWidth() throws Exception {
		final String name = "h-left";
		final List<String> pages = convertPages(name,
				document("<div style='display:inline-block'>" + content("left", false) + "</div>", false));
		assertEquals(name + ": 1 段目", 300, position(pages, "T1Z")[0], 0.5);
		assertEquals(name + ": 段の間隔", 22, position(pages, "T2Z")[0] - position(pages, "T1Z")[0], 0.5);
	}

	/** Without a multicol nothing changes: the float and the block's words add up once, as before. */
	public void testPlainBlockBesideFloatUnchanged() throws Exception {
		final String name = "h-plain";
		final List<String> pages = convertPages(name, document("<div style='display:inline-block'>"
				+ content("right", false).replace("column-count:4;column-gap:2pt", "") + "</div>", false));
		assertEquals(name + ": 浮動の字の位置", 20, position(pages, "T0Z")[0], 0.5);
	}

	/** A max-width inside the multicol caps its columns, and the float beside the multicol still adds its width. */
	public void testMaxWidthInColumnsKeepsTheFloat() throws Exception {
		final String name = "h-max-width-inside";
		final String columns = "<div style='column-count:4;column-gap:2pt'>";
		final String content = content("right", false);
		final int end = content.lastIndexOf("</div>");
		final List<String> pages = convertPages(name, document("<div style='display:inline-block'>"
				+ content.substring(0, end).replace(columns, columns + "<div style='max-width:10pt'>") + "</div></div></div>",
				false));
		assertEquals(name + ": 浮動の字の位置", 46, position(pages, "T0Z")[0], 0.5);
	}

	private static double[] position(final List<String> pages, final String text) {
		for (final String page : pages) {
			final Matcher m = TEXT.matcher(page);
			while (m.find()) {
				if (m.group(3).equals(text)) {
					return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
				}
			}
		}
		throw new AssertionError(text + " が描かれていない: " + pages);
	}

	private static List<String> convertPages(final String name, final String html) throws Exception {
		final File dir = new File("local/float-beside-multicol/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html);
		}
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try (OutputStream out = new FileOutputStream(new File(dir, "out.pdf"));
					AutoCloseable scope = DisplayListDumper.scopedDir(dir.getPath())) {
				final DirectSession session = (DirectSession) new DirectDriver()
						.getSession(URI.create("copper:direct:"), null);
				try {
					session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
					session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
					session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
					CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "float-beside-multicol-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		assertTrue(name + ": ページが1枚も出ていない", pages.length > 0);
		Arrays.sort(pages);
		final List<String> result = new ArrayList<>();
		for (final File page : pages) {
			result.add(java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8));
		}
		return result;
	}
}
