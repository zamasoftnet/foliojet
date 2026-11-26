package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * A fixed-position box inside an ancestor with a transform belongs to that ancestor, not to the paper (CSS Transforms 1
 * §2; 2026-11-25, a test with real web documents, copperpdf4 docs/history/2026-11-25-realweb-test.md). go.dev hides
 * its navigation drawer off the page with {@code position:fixed; transform:translateX(100%)}; the submenus inside it,
 * fixed boxes again, were drawn on every page over the text.
 */
public class FixedInTransformedAncestorTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	/** A box frame of the display list dump, with its transform (page-area coordinates). */
	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\] tf=\\[([-\\d. ]+)\\]");

	private static final String HEAD = "<!DOCTYPE html><html><head><meta charset='utf-8'><style>"
			+ "@page{size:A5;margin:20pt} body{margin:0;font:12pt sans-serif}";

	/**
	 * The drawer and its submenu, both {@code position:fixed} with {@code transform:translateX(100%)}: both lie
	 * right of the page area (379.53pt wide). Before, the submenu was placed on the paper at x=322.6.
	 */
	public void testSubmenuOfOffCanvasDrawerStaysOffThePaper() throws Exception {
		final File[] pages = convert("drawer", HEAD
				+ ".D{background:#eee;height:100%;left:auto;max-width:27rem;position:fixed;right:0;top:0;"
				+ "transform:translateX(100%);width:85%} .S{width:100%}</style></head><body><p>BODY</p>"
				+ "<nav class='D'><ul><li>OUTER<div class='D S'><ul><li>INNER</li></ul></div></li></ul></nav>"
				+ "<p>MORE</p></body></html>");
		assertEquals(1, pages.length);
		final List<double[]> drawers = new ArrayList<>();
		for (final double[] f : frames(pages[0])) {
			// the two drawers: 85% of the page area wide, as tall as the page area
			if (Math.abs(f[2] - 322.60) < 0.05 && Math.abs(f[3] - 555.28) < 0.05) {
				drawers.add(f);
			}
		}
		assertEquals("外と入れ子の引き出し", 2, drawers.size());
		for (final double[] f : drawers) {
			assertTrue("引き出しが紙の上にある: x=" + f[0] + " 移動=" + f[4], f[0] + f[4] >= 379.5);
		}
	}

	/** A fixed box inside a static ancestor moved away by a transform goes with it. */
	public void testFixedBoxGoesWithTransformedStaticAncestor() throws Exception {
		final File[] pages = convert("static", HEAD + ".f{position:fixed;top:0;left:0;width:50pt;height:20pt;"
				+ "background:#eee}</style></head><body><p>BODY</p><div style='transform:translateX(-1000pt)'>"
				+ "<div class='f'>FIXED</div></div><p>MORE</p></body></html>");
		final List<double[]> boxes = new ArrayList<>();
		for (final double[] f : frames(pages[0])) {
			if (Math.abs(f[2] - 50) < 0.05 && Math.abs(f[3] - 20) < 0.05) {
				boxes.add(f);
			}
		}
		assertEquals(1, boxes.size());
		assertTrue("移動した祖先と一緒に紙の左の外へ: " + Arrays.toString(boxes.get(0)), boxes.get(0)[0] + boxes.get(0)[4] < 0);
	}

	/**
	 * A fixed box inside an ancestor with a clip-path is clipped by it (2026-11-26, batch 6 of the real-site tests,
	 * Shopify's Dawn theme: the image of a "fixed" banner, {@code position:fixed; height:100vh} in a
	 * {@code clip-path: inset(0)} box). Fixed to the paper it covered the whole page, the heading above the banner and
	 * the text below it too. Chrome shows it inside the banner only.
	 */
	public void testFixedBoxInsideClipPathIsClipped() throws Exception {
		final File[] pages = convert("clip-path", HEAD + ".b{position:relative;height:100pt;clip-path:inset(0)}"
				+ ".m{position:absolute;top:0;left:0;width:100%;height:100%} .i{position:fixed;top:0;left:0;"
				+ "width:100%;height:100vh;background:#00f}</style></head><body><div style='height:60pt;background:#ccc'>"
				+ "HEAD</div><div class='b'><div class='m'><div class='i'></div></div></div><p>BELOW</p>"
				+ "<div style='height:600pt'></div><p>NEXT</p></body></html>");
		assertTrue(pages.length >= 2);
		try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader
				.loadPDF(new File(pages[0].getParentFile(), "out.pdf"))) {
			// 1 px = 1 pt; the page area starts 20pt from the edge
			final java.awt.image.BufferedImage p1 = new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImage(0);
			final java.awt.image.BufferedImage p2 = new org.apache.pdfbox.rendering.PDFRenderer(doc).renderImage(1);
			assertFalse("見出しが固定の箱に覆われた", blue(p1.getRGB(100, 50)));
			assertTrue("帯の中に固定の箱が無い", blue(p1.getRGB(100, 120)));
			assertFalse("帯の下が固定の箱に覆われた", blue(p1.getRGB(100, 300)));
			assertFalse("次の頁が固定の箱に覆われた", blue(p2.getRGB(100, 300)));
		}
	}

	private static boolean blue(final int rgb) {
		return (rgb & 0xff) > 200 && (rgb >> 16 & 0xff) < 60 && (rgb >> 8 & 0xff) < 60;
	}

	/** Without a transformed ancestor a fixed box is still drawn on every page. */
	public void testFixedBoxWithoutTransformRepeatsOnEveryPage() throws Exception {
		final File[] pages = convert("header", HEAD + ".h{position:fixed;top:0;left:0}</style></head><body>"
				+ "<div class='h'>HEADER</div><p>P1</p><div style='height:600pt'></div><p>P2</p></body></html>");
		assertTrue(pages.length >= 2);
		for (final File page : pages) {
			assertTrue(page.getName() + " に固定の見出しが無い",
					Files.readString(page.toPath(), StandardCharsets.UTF_8).contains("Text[\"HEADER\""));
		}
	}

	/** {x, y, w, h, tx, ty} of each box frame of a page dump. */
	private static List<double[]> frames(final File page) throws Exception {
		final List<double[]> out = new ArrayList<>();
		final Matcher m = FRAME.matcher(Files.readString(page.toPath(), StandardCharsets.UTF_8));
		while (m.find()) {
			final String[] tf = m.group(5).trim().split("\\s+");
			out.add(new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
					Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)), Double.parseDouble(tf[4]),
					Double.parseDouble(tf[5]) });
		}
		return out;
	}

	private static File[] convert(final String name, final String html) throws Exception {
		final File dir = new File("local/fixed-in-transformed/" + name);
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
					CTISessionHelper.transcodeFile(session, input, "text/html", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "fixed-in-transformed-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.startsWith("page-") && n.endsWith(".txt"));
		assertNotNull(pages);
		Arrays.sort(pages);
		return pages;
	}
}
