package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
 * A physical property and the logical property for the same side: the later one in the cascade wins (2026-10-08, EPUB
 * brush-up D-8; CSS Logical 1 §4, as in Chrome).
 *
 * <p>
 * An explicit physical property used to win whatever came after it, so the {@code margin: 0; padding: 0} reset of EPUB
 * style sheets silently cancelled every {@code margin-block}/{@code padding-inline} a print style sheet added, and the
 * UA's {@code body { margin: 8px }} beat an author's {@code body { margin-block: 0 }}.
 * </p>
 */
public class LogicalCascadeTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(\\w+)\"");

	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	public LogicalCascadeTest(final String name) {
		super(name);
	}

	private static String document(final String writingMode, final String style, final String body) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<style>
				@page { size: 300pt 300pt; margin: 0 }
				html { writing-mode: %s }
				body { font-size: 10pt; line-height: 20pt }
				p { margin: 0; padding: 0 }
				%s
				</style></head><body>%s</body></html>
				""".formatted(writingMode, style, body);
	}

	/** Horizontal writing: inline-start is the left side, block-start the top. */
	public void testHorizontal() throws Exception {
		final String page = convert("h", document("horizontal-tb", """
				body { margin-block: 0; margin-inline: 0 }
				p.a { margin-inline-start: 20pt }
				p.b { margin-left: 0; margin-inline-start: 30pt }
				p.c { margin-inline-start: 30pt; margin-left: 5pt }
				p.d { margin-inline-start: 30pt }
				p.d { margin-left: 7pt !important }
				p.e { padding-block-start: 10pt }
				""", "<p class=\"a\">A</p><p class=\"b\">B</p><p class=\"c\">C</p><p class=\"d\">D</p><p class=\"e\">E</p>"));
		assertEquals("より詳細な規則の論理が p { margin: 0 } に勝つ", 20, x(page, "A"), 0.01);
		assertEquals("同じ規則の後ろの論理が勝つ", 30, x(page, "B"), 0.01);
		assertEquals("同じ規則の後ろの物理が勝つ", 5, x(page, "C"), 0.01);
		assertEquals("!important の物理が後の論理に勝つ", 7, x(page, "D"), 0.01);
		// UA body { margin: 8px } loses to the author's margin-block/margin-inline: A starts at the paper's corner
		assertEquals("UA の body の余白に作者の margin-block が勝つ", 0, y(page, "A"), 0.01);
		assertEquals("padding-block-start", 4 * 20 + 10, y(page, "E"), 0.01);
	}

	/** Vertical writing (vertical-rl): inline-start is the top, block-start the right side. */
	public void testVertical() throws Exception {
		final String page = convert("v", document("vertical-rl", """
				body { margin: 0 }
				p.a { margin-inline-start: 20pt }
				p.b { margin-top: 0; margin-inline-start: 30pt }
				p.c { padding: 0 }
				p.c.d { padding-block-start: 10pt }
				""", "<p class=\"a\">A</p><p class=\"b\">B</p><p class=\"c d\">C</p>"));
		assertEquals("縦組みの inline-start は上", 20, y(page, "A"), 0.01);
		assertEquals("同じ規則の後ろの論理が勝つ(縦)", 30, y(page, "B"), 0.01);
		// block-start is the right side: C's line moves 10pt to the left of where it would be
		assertEquals("縦組みの padding-block-start は右", x(page, "B") - 20 - 10, x(page, "C"), 0.01);
	}

	/** width and inline-size, border-left-width and border-inline-start-width: the later declaration wins. */
	public void testSizesAndBorders() throws Exception {
		final String page = convert("sizes", document("horizontal-tb", """
				body { margin: 0 }
				div { height: 10pt; background: #ccc }
				div.a { width: 50pt; inline-size: 80pt }
				div.b { inline-size: 80pt; width: 50pt }
				div.c { border: 1pt solid; border-inline-start-width: 5pt; width: 50pt }
				""", "<div class=\"a\"></div><div class=\"b\"></div><div class=\"c\"></div>"));
		final Matcher m = FRAME.matcher(page);
		final double[] widths = new double[3];
		for (int i = 0; i < 3; ++i) {
			assertTrue("枠が足りない:\n" + page, m.find());
			widths[i] = Double.parseDouble(m.group(3));
		}
		assertEquals("後の inline-size が width に勝つ", 80, widths[0], 0.01);
		assertEquals("後の width が inline-size に勝つ", 50, widths[1], 0.01);
		assertEquals("後の border-inline-start-width が border に勝つ", 50 + 5 + 1, widths[2], 0.01);
	}

	/**
	 * Important declarations across {@code @layer} (codex review 2026-10-08, expected values measured in Chrome): the
	 * style attribute's outranks every layer's, and on a pseudo-element the earlier layer's wins, with a logical
	 * property against a physical one as with the same property.
	 */
	public void testImportantLayers() throws Exception {
		final String page = convert("important", document("horizontal-tb", """
				body { margin: 0 }
				@layer base, first, second;
				@layer base {
				  p.a { margin-inline-start: 30pt !important }
				  p.b { margin-left: 30pt !important }
				}
				@layer first {
				  p.c::before { margin-left: 10pt !important }
				  p.d::before { margin-left: 10pt !important }
				}
				@layer second {
				  p.c::before { margin-inline-start: 30pt !important }
				  p.d::before { margin-left: 30pt !important }
				}
				p.c::before { content: "C"; display: block }
				p.d::before { content: "D"; display: block }
				""", "<p class=\"a\" style=\"margin-left: 10pt !important\">A</p>"
				+ "<p class=\"b\" style=\"margin-left: 10pt !important\">B</p><p class=\"c\"></p><p class=\"d\"></p>"));
		assertEquals("style 属性の !important が層の論理の !important に勝つ", 10, x(page, "A"), 0.01);
		assertEquals("style 属性の !important が層の物理の !important に勝つ", 10, x(page, "B"), 0.01);
		assertEquals("::before でも前の層の !important が勝つ(論理と物理)", 10, x(page, "C"), 0.01);
		assertEquals("::before でも前の層の !important が勝つ(同じプロパティ)", 10, x(page, "D"), 0.01);
	}

	/**
	 * An explicit {@code inherit} takes the parent's value of the pair, not of the parent's losing slot with the same
	 * name; {@code all} sets the physical properties last, so they win as in Chrome.
	 */
	public void testExplicitInherit() throws Exception {
		final String page = convert("inherit", document("horizontal-tb", """
				body { margin: 0 }
				div.p1 { margin-left: 10pt; margin-inline-start: 30pt }
				div.c1 { margin-left: inherit }
				div.p2 { margin-inline-start: 30pt; margin-left: 10pt }
				div.c2 { margin-inline-start: inherit }
				div.p3 { margin-inline-start: 30pt }
				div.c3 { margin-left: inherit }
				div.p4 { margin: 10pt 20pt 30pt 40pt; margin-block: 10pt 30pt; margin-inline: 40pt 20pt; height: 60pt }
				div.c4 { all: inherit; writing-mode: vertical-rl }
				div.r4 { margin: 10pt 20pt 30pt 40pt; height: 60pt; writing-mode: vertical-rl }
				""", "<div class=\"p1\"><div class=\"c1\">A</div></div><div class=\"p2\"><div class=\"c2\">B</div></div>"
				+ "<div class=\"p3\"><div class=\"c3\">C</div></div><div class=\"p4\"><div class=\"c4\">D</div></div>"
				+ "<div class=\"p4\"><div class=\"r4\">E</div></div>"));
		assertEquals("margin-left: inherit は親の勝った論理の値", 30 + 30, x(page, "A"), 0.01);
		assertEquals("margin-inline-start: inherit は親の勝った物理の値", 10 + 10, x(page, "B"), 0.01);
		assertEquals("margin-left: inherit は親の論理だけの値も受け継ぐ", 30 + 30, x(page, "C"), 0.01);
		assertEquals("縦組みの子の all: inherit は親の物理の余白を受け継ぐ", x(page, "E"), x(page, "D"), 0.01);
	}

	/**
	 * A border shorthand resets what it leaves out (CSS Backgrounds 3 §3.4), which competes with the other half of the
	 * pair; the logical width's initial value is medium, as the physical one's.
	 */
	public void testBorderResets() throws Exception {
		final String page = convert("border-resets", document("horizontal-tb", """
				body { margin: 0 }
				""", "<p style=\"border-left: 8pt solid; border-inline-start: 2pt\">A</p>"
				+ "<p style=\"border-inline-start: 8pt solid; border-left: 2pt\">B</p>"
				+ "<p style=\"border-inline-start: 8pt solid; border: 2pt\">C</p>"
				+ "<p style=\"border: medium solid; border-inline-start-width: initial\">D</p>"
				+ "<p style=\"border-inline-start: solid\">E</p><p style=\"border-left: medium solid\">F</p>"));
		assertEquals("後の論理の短縮形が線種を none に戻す", 0, x(page, "A"), 0.01);
		assertEquals("後の物理の短縮形が線種を none に戻す", 0, x(page, "B"), 0.01);
		assertEquals("後の border が論理の線種を none に戻す", 0, x(page, "C"), 0.01);
		assertTrue("medium は 0 でない", x(page, "F") > 0);
		assertEquals("border-inline-start-width: initial は medium", x(page, "F"), x(page, "D"), 0.01);
		assertEquals("border-inline-start: solid の幅は medium", x(page, "F"), x(page, "E"), 0.01);
	}

	/** Images take the logical sizes too (they skipped them, a leftover of -cssj-direction-mode). */
	public void testImages() throws Exception {
		final File images = new File("local/logical-cascade/png");
		images.mkdirs();
		javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(40, 40, java.awt.image.BufferedImage.TYPE_INT_RGB),
				"png", new File(images, "square.png"));
		javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(400, 200,
				java.awt.image.BufferedImage.TYPE_INT_RGB), "png", new File(images, "wide.png"));
		final double[] h = frames(convert("images", document("horizontal-tb", """
				body { margin: 0 }
				""", "<p><img src=\"../png/square.png\" style=\"width: 15pt; inline-size: 60pt !important\"/></p>"
				+ "<div style=\"width: 75pt\"><img src=\"../png/wide.png\" style=\"max-inline-size: 100%;"
				+ " display: block\"/></div><p><img src=\"../png/wide.png\" style=\"inline-size: 45pt\"/></p>")), 3);
		assertEquals("画像の inline-size !important が width に勝つ", 60, h[0], 0.01);
		assertEquals("画像の max-inline-size: 100%", 75, h[2], 0.01);
		assertEquals("画像の max-inline-size: 100%(高さは比率)", 37.5, h[3], 0.01);
		assertEquals("画像の inline-size", 45, h[4], 0.01);
		assertEquals("画像の inline-size(高さは比率)", 22.5, h[5], 0.01);
		final double[] v = frames(convert("images-v", document("vertical-rl", """
				body { margin: 0 }
				""", "<p><img src=\"../png/wide.png\" style=\"inline-size: 45pt\"/></p>"
				+ "<p><img src=\"../png/wide.png\" style=\"height: 15pt; inline-size: 45pt\"/></p>")), 2);
		assertEquals("縦組みの画像の inline-size は高さ", 45, v[1], 0.01);
		assertEquals("縦組みの画像の inline-size(幅は比率)", 90, v[0], 0.01);
		assertEquals("縦組みの画像の後の inline-size が height に勝つ", 45, v[3], 0.01);
	}

	/** The widths and heights of the first {@code count} frames: w0, h0, w1, h1... */
	private static double[] frames(final String page, final int count) {
		final Matcher m = FRAME.matcher(page);
		final double[] sizes = new double[count * 2];
		for (int i = 0; i < count; ++i) {
			assertTrue("枠が足りない:\n" + page, m.find());
			sizes[i * 2] = Double.parseDouble(m.group(3));
			sizes[i * 2 + 1] = Double.parseDouble(m.group(4));
		}
		return sizes;
	}

	private static double x(final String page, final String text) {
		return coordinate(page, text, 1);
	}

	private static double y(final String page, final String text) {
		return coordinate(page, text, 2);
	}

	private static double coordinate(final String page, final String text, final int group) {
		final Matcher m = TEXT.matcher(page);
		while (m.find()) {
			if (m.group(3).equals(text)) {
				return Double.parseDouble(m.group(group));
			}
		}
		throw new AssertionError(text + " が無い:\n" + page);
	}

	/** Convert and return the first page's display list. */
	private static String convert(final String name, final String html) throws Exception {
		final File dir = new File("local/logical-cascade/" + name);
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
		}, "logical-cascade-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		Arrays.sort(pages);
		return java.nio.file.Files.readString(pages[0].toPath(), StandardCharsets.UTF_8);
	}
}
