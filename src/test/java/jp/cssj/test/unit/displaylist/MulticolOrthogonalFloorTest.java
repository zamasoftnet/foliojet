package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
 * <b>段組の高さ合わせ(column-fill: balance)で、直交する書字方向の子より段を
 * 小さくしない</b>ことを固定します(2026-10-03新設、掃過 seed 11587843)。
 *
 * <p>
 * 直交する子(縦の段組の中の横書き、横の段組の中の縦書き)は改ページ契約で
 * atomic——段境界で切れません。ところが容量探索
 * ({@code Container.getCutPointBelow})はその子の<b>行の境目</b>(軸違い)を
 * 切れ目として返すので、{@code ColumnBalancer}は子より小さい段容量を選び、
 * そのまま箱の寸法になっていました:
 * </p>
 * <ul>
 * <li>縦の段組(vertical-rl)は子より<b>細く</b>組まれ、子は段の右端(ブロック
 * の始まり)に寄せて置かれるので、左へはみ出す。本文が vertical-lr だと段組は
 * 紙の左端にあり、字が紙の外(x&lt;0)に描かれた。2 段で x=−26.90、4 段で
 * x=−62.09(Chrome はどちらも 0)</li>
 * <li>横の段組は子より<b>低く</b>組まれ、後ろの段落が子に重なった(高さ 88pt の
 * 子の後ろの段落が y=73.42。段組なしなら 100.32)</li>
 * </ul>
 *
 * <p>
 * 修正は{@code FlowContainer.balancePageSizeFloor}: 同軸逆進行(RL⇄LR)の子だけ
 * だった床(2026-08-22)を、書字方向が段組と違う子すべてへ広げ、同じ書字方向の
 * 子の中にあるものも辿る。
 * </p>
 */
public class MulticolOrthogonalFloorTest extends TestCase {
	private static final long WATCHDOG_MS = 60_000L;

	private static final Pattern TEXT_IN_DUMP = Pattern
			.compile("x=(-?[\\d.]+) y=(-?[\\d.]+) Text\\[\"(T\\d+)\"");

	public MulticolOrthogonalFloorTest(String name) {
		super(name);
	}

	private static String document(final String bodyWritingMode, final String body) {
		return """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="595pt"?>
				<?jp.cssj.property name="output.page-height" value="842pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:0pt}
				body{margin:0;font:normal 11pt/1.2 serif;writing-mode:%s}
				div,p{margin:0;padding:0}
				</style></head><body>
				%s
				</body></html>
				""".formatted(bodyWritingMode, body);
	}

	private static final String HORIZONTAL_CHILD = "<div style=\"writing-mode:horizontal-tb;width:88pt\">T0<br>T1</div>";

	/** 縦の段組の中の横書きの子。段数を変えても字は紙の左端(x=0)から。 */
	public void testVerticalColumnsAreNotNarrowerThanHorizontalChild() throws Exception {
		for (final int count : new int[] { 2, 3, 4 }) {
			final String html = document("vertical-lr", "<div style=\"writing-mode:vertical-rl\"><div style=\"column-count:"
					+ count + "\">" + HORIZONTAL_CHILD + "</div></div>");
			final String dump = convert("vertical-c" + count, html);
			assertEquals(count + " 段: T0 の x(Chrome は 0)", 0.0, x(dump, "T0"), 0.01);
			assertEquals(count + " 段: T1 の x", 0.0, x(dump, "T1"), 0.01);
		}
	}

	/** 同じ書字方向の div で一段包んでも同じ。 */
	public void testNestedHorizontalChildIsFound() throws Exception {
		final String html = document("vertical-lr", "<div style=\"writing-mode:vertical-rl\"><div style=\"column-count:2\"><div>"
				+ HORIZONTAL_CHILD + "</div></div></div>");
		assertEquals("T0 の x(Chrome は 0)", 0.0, x(convert("vertical-nested", html), "T0"), 0.01);
	}

	/** 掃過で止まった元の文書(縮小前の 930 バイトそのまま)。Chrome は x=30.0。 */
	public void testSweepSeed11587843() throws Exception {
		final String html = document("vertical-lr", """
				<div style="display:list-item;position:static;float:left;writing-mode:vertical-rl;">
				<div style="column-count:4;column-gap:4pt">
				<div style="writing-mode:horizontal-tb;width:min-content;min-width:8em;max-width:90%;">
				<ul style="list-style-position:outside;list-style-type:none">
				<li>T0</li>
				<li>T1</li>
				</ul>
				</div>
				</div>
				</div>""");
		final String dump = convert("seed-11587843", html);
		assertEquals("T0 の x(Chrome は 30.0)", 30.0, x(dump, "T0"), 0.01);
	}

	/** 横の段組の中の縦書きの子(高さ 88pt)。後ろの段落は子に重ならない。 */
	public void testHorizontalColumnsAreNotShorterThanVerticalChild() throws Exception {
		final String html = document("horizontal-tb", "<div style=\"column-count:2\">"
				+ "<div style=\"writing-mode:vertical-rl;height:88pt\">T2<br>T3</div></div><p>T5</p>");
		final double y = y(convert("horizontal-c2", html), "T5");
		assertTrue("T5 が縦書きの子(高さ 88pt)に重なっている: y=" + y, y >= 88);
	}

	private static double x(final String dump, final String token) {
		return position(dump, token)[0];
	}

	private static double y(final String dump, final String token) {
		return position(dump, token)[1];
	}

	private static double[] position(final String dump, final String token) {
		final Matcher m = TEXT_IN_DUMP.matcher(dump);
		while (m.find()) {
			if (m.group(3).equals(token)) {
				return new double[] { Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2)) };
			}
		}
		throw new AssertionError(token + " が描かれていない:\n" + dump);
	}

	/** 1 頁の文書を変換して、その頁の表示リストを返します。 */
	private static String convert(final String name, final String html) throws Exception {
		final File dir = new File("local/multicol-orthogonal-floor/" + name);
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
					CTISessionHelper.transcodeFile(session, input, "text/html", null);
				} finally {
					session.close();
				}
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "multicol-orthogonal-floor-" + name, 64L * 1024 * 1024);
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
