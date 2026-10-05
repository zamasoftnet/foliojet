package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
 * 下端のページフロートの帯に、grid・表(中の行を別に組む箱)が入っても図版に重ならないことを固定します
 * (2026-10-05、jigensha の報告: 縦組みの本で、図のあとの吹き出しの grid の字が図に重なった)。
 *
 * <p>
 * 帯の中で始まる grid・flex は、始まりで図版のぶん狭める(CSS 2.1 §9.5 の、独立整形文脈の箱がフロートを避ける規則を
 * ページフロートにも)。帯の手前で始まって帯へ入る箱は、その頁を一次元の予約へ切り替えて帯の手前で割る。
 * </p>
 */
public class BottomFloatBesideBoxesTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final double FONT = 9.2;

	private static final Pattern FRAME = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) AbsoluteRectFrame\\[w=([\\d.]+) h=([\\d.]+)\\]");

	private static final Pattern TEXT = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) (?:artifact )?Text\\[\"([^\"]*)\" asc=([\\d.]+) desc=([\\d.]+)\\]");

	public BottomFloatBesideBoxesTest(String name) {
		super(name);
	}

	private static final String SAY = "吹き出しの字。".repeat(12);

	private static String document(final String writingMode, final String figSize, final boolean before,
			final String box) {
		final String text = before ? "" : "<p>本文。本文。本文。本文。本文。本文。本文。本文。本文。本文。本文。</p>";
		final String after = before ? "" : "<p>図の後の本文。" + "本文。".repeat(18) + "</p>";
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml" lang="ja"><head><meta charset="UTF-8"/>
				<style>
				@page{size:148mm 210mm;margin:20mm 16mm}
				html{writing-mode:%s;font:9.2pt/1.8 serif}
				body{margin:0}
				p{margin:0}
				.fig{float:bottom;width:fit-content;margin-top:4mm}
				.fig div{%s}
				.mimi{display:grid;grid-template-columns:12mm 1fr;column-gap:3mm}
				</style></head><body>
				%s<div class="fig"><div></div></div>%s%s<p>その後の本文。%s</p>
				</body></html>
				""".formatted(writingMode, figSize, text, after, box, "本文。".repeat(15));
	}

	private static String grid() {
		return "<div class=\"mimi\"><div></div><div><p>ミミのひとこと。" + SAY + "</p></div></div>";
	}

	private static String table() {
		return "<table style=\"border-collapse:collapse\"><tr><td style=\"width:12mm\"></td><td><p>ミミのひとこと。" + SAY
				+ "</p></td></tr></table>";
	}

	private static final String VERTICAL_FIG = "width:100mm;height:45mm;background:#88c";

	private static final String HORIZONTAL_FIG = "width:70mm;height:100mm;background:#88c";

	/** jigensha の再現: 縦組み、図のあとの本文に続く grid(帯の中で始まる)。 */
	public void testVerticalGridInBand() throws Exception {
		assertNoOverlap(convert("v-grid-in", document("vertical-rl", VERTICAL_FIG, false, grid())), true);
	}

	/** 縦組み、帯の手前(頁の頭)で始まって帯へ入る grid。 */
	public void testVerticalGridBeforeBand() throws Exception {
		assertNoOverlap(convert("v-grid-before", document("vertical-rl", VERTICAL_FIG, true, grid())), true);
	}

	public void testVerticalTableInBand() throws Exception {
		assertNoOverlap(convert("v-table-in", document("vertical-rl", VERTICAL_FIG, false, table())), true);
	}

	public void testHorizontalGridInBand() throws Exception {
		assertNoOverlap(convert("h-grid-in", document("horizontal-tb", HORIZONTAL_FIG, false, grid())), false);
	}

	public void testHorizontalGridBeforeBand() throws Exception {
		assertNoOverlap(convert("h-grid-before", document("horizontal-tb", HORIZONTAL_FIG, true, grid())), false);
	}

	/**
	 * 図版の直後(あいだに段落が無い)の flow-root(jigensha の 2 件目、19105)。箱は帯のわずか手前から
	 * 始まり、中の行だけ短くなって背景と罫が図版の下まで伸びていた。箱ごと図版の手前で短くなる。
	 */
	public void testVerticalFlowRootRightAfterFigure() throws Exception {
		assertBoxesClearOfFigure(convert("v-flow-root-after", rightAfterFigure("display:flow-root")));
	}

	/** 同じく grid。行も短くならず、字が図版に重なっていた。 */
	public void testVerticalGridRightAfterFigure() throws Exception {
		assertBoxesClearOfFigure(convert("v-grid-after", rightAfterFigure("display:grid")));
	}

	private static String rightAfterFigure(final String display) {
		return """
				<!DOCTYPE html>
				<html xmlns="http://www.w3.org/1999/xhtml" lang="ja"><head><meta charset="UTF-8"/>
				<style>
				@page{size:120mm 90mm;margin:6mm}
				html{writing-mode:vertical-rl;font:9.2pt/1.7 serif}
				p{margin:0}
				.col{%s;background:#eef;border-left:3pt solid #25a;padding:3mm;margin:3mm 0}
				.fig{writing-mode:horizontal-tb;float:bottom;width:100mm;height:35mm;background:#fb6;margin-top:3mm}
				</style></head><body>
				<p>本文。本文の一行目。</p><div class="fig"></div>
				<div class="col">コラム。背景のある箱の中の字です。図を避けて箱ごと短くなる。</div>
				<p>コラムのあとの本文。</p>
				</body></html>
				""".formatted(display);
	}

	/** 図版(いちばん大きい背景の枠)と、ほかの背景の枠が交わらず、行も図版に重ならない。 */
	private static void assertBoxesClearOfFigure(final String[] pages) {
		assertNoOverlap(pages, true);
		boolean box = false;
		for (int p = 0; p < pages.length; ++p) {
			final List<double[]> frames = new ArrayList<>();
			final Matcher f = FRAME.matcher(pages[p]);
			while (f.find()) {
				frames.add(new double[] { Double.parseDouble(f.group(1)), Double.parseDouble(f.group(2)),
						Double.parseDouble(f.group(3)), Double.parseDouble(f.group(4)) });
			}
			double[] fig = null;
			for (final double[] r : frames) {
				if (fig == null || r[2] * r[3] > fig[2] * fig[3]) {
					fig = r;
				}
			}
			for (final double[] r : frames) {
				if (r == fig) {
					continue;
				}
				box = true;
				final boolean overlaps = r[0] < fig[0] + fig[2] - 0.5 && fig[0] < r[0] + r[2] - 0.5
						&& r[1] < fig[1] + fig[3] - 0.5 && fig[1] < r[1] + r[3] - 0.5;
				assertFalse((p + 1) + " 頁で図版 " + Arrays.toString(fig) + " と重なる箱 " + Arrays.toString(r), overlaps);
			}
		}
		assertTrue("背景のある箱が無い", box);
	}

	/**
	 * どの頁でも、図版の枠と交わる行が無いこと。行の長さは全角の字数×字の大きさ(行末の句読点はぶら下げで
	 * 行の外へ出てよいので数えない)。図版はいちばん大きい背景つきの枠。
	 */
	private static void assertNoOverlap(final String[] pages, final boolean vertical) {
		boolean found = false;
		for (int p = 0; p < pages.length; ++p) {
			double[] fig = null;
			final Matcher f = FRAME.matcher(pages[p]);
			while (f.find()) {
				final double[] r = { Double.parseDouble(f.group(1)), Double.parseDouble(f.group(2)),
						Double.parseDouble(f.group(3)), Double.parseDouble(f.group(4)) };
				if (fig == null || r[2] * r[3] > fig[2] * fig[3]) {
					fig = r;
				}
			}
			if (fig == null || fig[2] < 150) {
				continue;
			}
			found = true;
			final Matcher t = TEXT.matcher(pages[p]);
			while (t.find()) {
				final String text = t.group(3);
				final int chars = text.endsWith("。") || text.endsWith("、") ? text.length() - 1 : text.length();
				final double x = Double.parseDouble(t.group(1));
				final double y = Double.parseDouble(t.group(2));
				final double thick = Double.parseDouble(t.group(4)) + Double.parseDouble(t.group(5));
				final double[] line = vertical ? new double[] { x, y, thick, chars * FONT }
						: new double[] { x, y, chars * FONT, thick };
				final boolean overlaps = line[0] < fig[0] + fig[2] - 0.5 && fig[0] < line[0] + line[2] - 0.5
						&& line[1] < fig[1] + fig[3] - 0.5 && fig[1] < line[1] + line[3] - 0.5;
				assertFalse((p + 1) + " 頁で図版 " + Arrays.toString(fig) + " と重なる行: " + t.group(), overlaps);
			}
		}
		assertTrue("図版が無い", found);
	}

	/** 変換して、各頁の表示リストを頁順に返します。 */
	private static String[] convert(final String name, final String html) throws Exception {
		final File dir = new File("local/bottom-float-beside-boxes/" + name);
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
					CTISessionHelper.transcodeFile(session, input, "application/xhtml+xml", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "bottom-float-beside-boxes-" + name, 64L * 1024 * 1024);
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
		final String[] dumps = new String[pages.length];
		for (int i = 0; i < pages.length; ++i) {
			dumps[i] = java.nio.file.Files.readString(pages[i].toPath(), StandardCharsets.UTF_8);
		}
		return dumps;
	}
}
