package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
 * 縦組みの行の中の MathML を、欧文と同じく横倒しにして組むことを固定します(2026-10-05、jigensha の報告 2)。
 *
 * <p>
 * それまでは正立の横組みの箱のまま置かれ、行の進む向きには箱の高さ(約 1 字)しか進まず、式の幅が隣の行へ
 * はみ出した。横倒しにすると、行の向き(縦)の長さが式の幅になり、行の幅(横)が式の高さになる。
 * {@code text-orientation: upright} は正立のまま。
 * </p>
 */
public class MathVerticalTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	/** 数式の画像は表示リストでは枠(AbsoluteRectFrame)として出る。 */
	private static final Pattern FRAME_IN_DUMP = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static final String MATH = "<math xmlns=\"http://www.w3.org/1998/Math/MathML\">"
			+ "<mn>3</mn><mo>×</mo><mn>5</mn><mo>≡</mo><mn>15</mn></math>";

	public MathVerticalTest(String name) {
		super(name);
	}

	private static String document(final String writingMode, final String orientation) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml"><head><meta charset="UTF-8"/>
				<?jp.cssj.property name="output.page-width" value="300pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<style>
				@page{margin:10pt}
				html{writing-mode:%s;text-orientation:%s}
				body{margin:0;font:12pt/1.8 serif}
				p{margin:0}
				</style></head><body>
				<p>一:%s の余り</p>
				</body></html>
				""".formatted(writingMode, orientation, MATH);
	}

	public void testSidewaysInVerticalLines() throws Exception {
		final double[] horizontal = frame(convert("horizontal", document("horizontal-tb", "mixed")));
		final double[] vertical = frame(convert("vertical", document("vertical-rl", "mixed")));
		// 横組みでは横長の箱
		assertTrue("横組みの式は横長: w=" + horizontal[2] + " h=" + horizontal[3], horizontal[2] > 2 * horizontal[3]);
		// 縦組みでは横倒し: 縦横が入れ替わる
		assertEquals("縦組みの式の縦は横組みの式の幅", horizontal[2], vertical[3], 0.01);
		assertEquals("縦組みの式の横は横組みの式の高さ", horizontal[3], vertical[2], 0.01);
	}

	public void testUprightStaysUpright() throws Exception {
		final double[] horizontal = frame(convert("horizontal-u", document("horizontal-tb", "upright")));
		final double[] upright = frame(convert("upright", document("vertical-rl", "upright")));
		assertEquals("正立の式の幅", horizontal[2], upright[2], 0.01);
		assertEquals("正立の式の高さ", horizontal[3], upright[3], 0.01);
	}

	private static double[] frame(final String dump) {
		final List<double[]> frames = new ArrayList<>();
		final Matcher m = FRAME_IN_DUMP.matcher(dump);
		while (m.find()) {
			frames.add(new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)),
					Double.parseDouble(m.group(3)), Double.parseDouble(m.group(4)) });
		}
		assertEquals("数式 1 つ: " + dump, 1, frames.size());
		return frames.get(0);
	}

	/** 1 頁の文書を変換して、その頁の表示リストを返します。 */
	private static String convert(final String name, final String html) throws Exception {
		final File dir = new File("local/math-vertical/" + name);
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
					session.property("input.include", "**");
					session.property("input.property-pi", "true");
					CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "math-vertical-" + name, 64L * 1024 * 1024);
		worker.setDaemon(true);
		worker.start();
		worker.join(WATCHDOG_MS);
		assertFalse(name + ": 変換が" + WATCHDOG_MS / 1000 + "秒で終わらない", worker.isAlive());
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わった", failure[0]);
		}
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		assertNotNull(name + ": ページが1枚も出ていない", pages);
		assertEquals(name + ": 頁数", 1, pages.length);
		return java.nio.file.Files.readString(pages[0].toPath(), StandardCharsets.UTF_8);
	}
}
