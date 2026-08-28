package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;

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
 * <b>1つのitemが複数ページぶんあるgrid/flexが最後まで改ページできる</b>ことを
 * 固定します(2026-08-17新設)。
 *
 * <p>
 * {@code GridBox.split}/{@code FlexBox.split}の境界行強制分割
 * (crosses+anySplit)は、継続断片の行帳簿の行高を<b>分割直後のremainderの
 * 実測</b>で書いていた。remainderはその時点で未レイアウト(アンカー復元前)で
 * {@code getPageExtent}がほぼ0を返すため、gridでは次のsplitの境界探索
 * ({@code Row.start}直接比較)が「全行が切断線の手前に収まる」と誤読して
 * <b>空の継続断片</b>を返し、残余が頭断片に積み残って紙外へ描かれた。
 * </p>
 *
 * <p>
 * 実物: eLifeの論文(`files/realworld/elife-art`)は本文全体を
 * {@code display:grid}のラッパーで包んでおり、95ページぶんが3ページ目に
 * 積み上がっていた(文字の重なり4,081,661対・紙外51,339pt)。実コーパス
 * 235文書のうち同型の壊れ方が6文書(pandoc-doc・qiita-article・godoc-pkg・
 * elife-art・mathjax-docs・rtd-theme)。
 * </p>
 *
 * <p>
 * 修正は「継続行の高さは<b>元の行の高さ−このページで消費した量</b>を
 * 下回らせない」という幾何学的下限(GridBox/FlexBox両方)。flexは境界探索が
 * 累積和なので実害までは確認されていないが、同じ帳簿誤りがあるため同じ
 * 下限で守る。
 * </p>
 */
public class RowSplitContinuationLedgerTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** 打ち切り時間。実測は1件あたり5秒未満。 */
	private static final long WATCHDOG_MS = 120_000L;

	/** 段落数。紙面(内容180pt)の50ページぶん超。 */
	private static final int PARAGRAPHS = 300;

	public RowSplitContinuationLedgerTest(final String name) {
		super(name);
	}

	/** eLifeと同型: 12列grid、小さいnavと巨大itemの2行。修正前は3ページで内容が尽きた。 */
	public void testGridWithMultiPageItemPaginatesToTheEnd() throws Exception {
		final int pages = convert("grid-multipage-item",
				".wrap{display:grid;grid-template-columns:repeat(12,1fr);grid-column-gap:8px}\n"
						+ ".nav{grid-column:1/13}.main{grid-column:2/12}");
		assertTrue("gridの巨大itemが最後まで組まれていない(ページ数=" + pages + ")", pages >= 40);
	}

	/** flexの鏡像(column方向)。帳簿誤りは同じだが境界探索が累積和のため実害は未確認——防御の固定。 */
	public void testFlexColumnWithMultiPageItemPaginatesToTheEnd() throws Exception {
		final int pages = convert("flex-multipage-item",
				".wrap{display:flex;flex-direction:column}\n.nav{}.main{}");
		assertTrue("flexの巨大itemが最後まで組まれていない(ページ数=" + pages + ")", pages >= 40);
	}

	/**
	 * <b>文書末尾のflexコンテナが、直前の内容に関係なく改ページされる</b>
	 * (2026-08-17、pandocマニュアルの根治)。
	 *
	 * <p>
	 * flex/gridの中身はTwoPass録画で組まれ{@code addBound}のearly-returnを
	 * 通るため、{@code interflowBreak}を立てないまま閉じる。コンテナが
	 * 最後の子だと{@code endFlowBlock}末尾のはみ出し検査が唯一の
	 * 自動改ページ機会だが、直前のnav(inline-flex)がフラグをfalseのまま
	 * 残すと検査がスキップされ、本文全体が1ページに積み上がった
	 * (実測: pandocマニュアル130,000pt)。修正はPageAtomicBoxを閉じたとき
	 * 検査を必ず有効にすること({@code BreakableBuilder.endFlowBlock})。
	 * </p>
	 */
	public void testTrailingFlexAfterInlineFlexNavPaginates() throws Exception {
		final StringBuilder html = new StringBuilder();
		html.append("""
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{font:normal 9pt/1.2 serif;margin:0}
				.container{display:flex}
				nav > ul{display:inline-flex;flex-wrap:wrap}
				</style></head><body>
				<nav><ul><li><a href="#">A</a></li></ul></nav>
				<div class="container">
				<main>
				""");
		for (int i = 0; i < PARAGRAPHS; ++i) {
			html.append("<p>Paragraph ").append(i).append(" text that wraps a bit more here.</p>\n");
		}
		// 後続の内容は置かない——コンテナが最後の子であることが再現条件
		html.append("</main>\n<div>SIDE</div>\n</div>\n</body></html>\n");

		final File dir = new File("local/row-split-ledger/trailing-flex");
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html.toString());
		}
		final int pages = convertFile("trailing-flex", dir, input);
		assertTrue("末尾のflexコンテナが改ページされていない(ページ数=" + pages + ")", pages >= 8);
	}

	/**
	 * <b>body自体がcolumn flexでも最後まで改ページされる</b>
	 * (2026-08-17、godoc-pkgの根治)。
	 *
	 * <p>
	 * column方向flexは行帳簿を持たずatomic——救済分割が1回働いても、
	 * 従来の{@code endFlowBlock}のはみ出し検査は<b>1回だけ</b>だったため、
	 * 残余が2ページ目に置かれたまま再検査されず、はみ出したまま終わった
	 * (実測: pkg.go.devのページが2ページ・重なり1,088万対)。修正は
	 * PageAtomicBoxを閉じたときだけ検査を入るまで繰り返すこと
	 * (無条件のループは白紙ページ抑止とfuzzの既存挙動を壊す)。
	 * </p>
	 */
	public void testBodyAsColumnFlexPaginatesToTheEnd() throws Exception {
		final StringBuilder html = new StringBuilder();
		html.append("""
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{font:normal 9pt/1.2 serif;margin:0;display:flex;flex-direction:column}
				</style></head><body>
				<header>HEAD</header>
				<main>
				""");
		for (int i = 0; i < PARAGRAPHS; ++i) {
			html.append("<p>Paragraph ").append(i).append(" text that wraps a bit more here.</p>\n");
		}
		html.append("</main>\n<footer>FOOT</footer>\n</body></html>\n");

		final File dir = new File("local/row-split-ledger/body-column-flex");
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html.toString());
		}
		final int pages = convertFile("body-column-flex", dir, input);
		// 当時は救済分割で35ページ(帯が行の途中を切る)。2026-08-18の
		// F0非原子化(FlexBox.isPageAtomicNow)以降は通常の行分割で組まれる
		// ためページ数はさらに増える——下限はどちらの経路でも成り立つ値
		assertTrue("bodyのcolumn flexが最後まで組まれていない(ページ数=" + pages + ")", pages >= 20);
	}

	/**
	 * <b>保持側が切断線より早く終わる境界行で、次行が残余に重ならない</b>
	 * (2026-08-19、smolcssの根治)。
	 *
	 * <p>
	 * 行の強制分割は不可分な内容({@code page-break-inside:avoid}のブロック等)を
	 * 丸ごと残余へ送るため、保持側の実内容は切断線より早く終わりうる。
	 * 従来は移送・保持断片寸法が切断線基準だったため、残余の実内容
	 * (=元の行高−実消費>元の行高−切断線ぶん)が「旧幾何−切断線」で固定した
	 * 次行の開始位置に重なった(smolcss: 前の記事のフッタに次の記事の本文が
	 * 重なる)。修正は保持側の実描画終端({@code paintedPageExtent})基準。
	 * </p>
	 */
	public void testKeptSideEndingEarlyDoesNotOverlapNextRow() throws Exception {
		final StringBuilder html = new StringBuilder();
		html.append("""
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{font:normal 9pt/1.2 serif;margin:0}
				.wrap{display:grid}
				.atomic{page-break-inside:avoid}
				</style></head><body><div class="wrap">
				<article>
				""");
		// 保持側になる段落(切断線の手前で終わる)
		for (int i = 0; i < 10; ++i) {
			html.append("<p>Alpha paragraph ").append(i).append(" fills the kept side of row A.</p>\n");
		}
		// 不可分ブロック(切断線を跨ぐため丸ごと残余へ送られる)
		html.append("<div class=\"atomic\">");
		for (int i = 0; i < 8; ++i) {
			html.append("<p>Atomic line ").append(i).append("</p>");
		}
		html.append("</div>\n<p>ATAIL marks the end of row A.</p>\n</article>\n<article>\n");
		html.append("<p>BHEAD starts row B here.</p>\n");
		for (int i = 0; i < 6; ++i) {
			html.append("<p>Bravo paragraph ").append(i).append(".</p>\n");
		}
		html.append("</article>\n</div></body></html>\n");

		final File dir = new File("local/row-split-ledger/kept-early-end");
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html.toString());
		}
		final int pages = convertFile("kept-early-end", dir, input);
		assertTrue("行分割が起きていない(ページ数=" + pages + ")", pages >= 2);
		// 同一ページにATAIL(行Aの末尾)とBHEAD(行Bの先頭)が載るなら、
		// BHEADは必ずATAILより下に置かれる
		boolean checked = false;
		for (int p = 1; p <= pages; ++p) {
			final java.util.List<String> lines = java.nio.file.Files.readAllLines(
					new File(dir, String.format("page-%04d.txt", p)).toPath(), StandardCharsets.UTF_8);
			double atail = Double.NaN, bhead = Double.NaN;
			for (final String line : lines) {
				final java.util.regex.Matcher m = java.util.regex.Pattern
						.compile("y=([0-9.-]+) (?:artifact )?Text\\[\"(ATAIL|BHEAD)\"").matcher(line);
				if (m.find()) {
					if ("ATAIL".equals(m.group(2))) {
						atail = Double.parseDouble(m.group(1));
					} else {
						bhead = Double.parseDouble(m.group(1));
					}
				}
			}
			if (!Double.isNaN(atail) && !Double.isNaN(bhead)) {
				checked = true;
				assertTrue("p" + p + "で行Bの先頭(y=" + bhead + ")が行Aの残余(y=" + atail + ")に重なっています",
						bhead > atail);
			}
		}
		assertTrue("ATAILとBHEADが同一ページに現れず、重なり検査ができていません(フィクスチャ要調整)", checked);
	}

	/**
	 * 描画物のないflex行を極小ページで分割しても、0消費のまま同じ行を
	 * 再分割して座標が指数的に膨張しないこと(極端掃過 STRICT seed 189)。
	 */
	public void testEmptyVerticalFlexRowMakesProgressAcrossTableFragments() throws Exception {
		final String html = """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="60pt"?>
				<?jp.cssj.property name="output.page-height" value="60pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>@page{margin:10pt} body{font:normal 12pt/1.2 serif;writing-mode:vertical-lr}</style>
				</head><body><table>
				<td><div style="display:flex;flex-direction:row-reverse;flex-wrap:wrap-reverse;width:8em">
				<ul></ul><input type="checkbox" /><div style="width:78%"></div>
				<ol style="list-style-position:inside"><li></li></ol>
				</div></td><td></td><tfoot><td>T256</td></tfoot>
				</table></body></html>
				""";
		final File dir = new File(System.getProperty("java.io.tmpdir"),
				"row-split-ledger/empty-vertical-flex-row");
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
		final int pages = convertFile("empty-vertical-flex-row", dir, input);
		assertTrue("空flex行の分割が進んでいない(ページ数=" + pages + ")", pages <= 10);
	}

	/**
	 * 分割中の先頭行だけを再flowし、丸ごと持ち越した後続行のpercent寸法と
	 * 枠込み外寸を世代ごとに再加算しないこと(extreme STRICT seed 189の
	 * 第二最小条件)。旧実装は26ページでx座標が4.97e8ptまで発散した。
	 */
	public void testLaterVerticalFlexRowsKeepExtentsAcrossFragments() throws Exception {
		final String html = """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="60pt"?>
				<?jp.cssj.property name="output.page-height" value="60pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>@page{margin:10pt}body{margin:0;font:normal 12pt/1.2 serif;writing-mode:vertical-lr}</style>
				</head><body><table>
				<thead><th>T181</th></thead>
				<td>T223<div style="display:flex;flex-direction:row-reverse;flex-wrap:wrap-reverse;width:8em">
				<div style="flex:0 0 calc(25% + 0pt);min-width:8em"><input /><input value="x" /></div>
				<div style="width:78%"><table><td>T230</td></table></div>
				<ol></ol>
				</div></td>
				<tfoot><td>T256</td></tfoot>
				</table></body></html>
				""";
		final File dir = new File(System.getProperty("java.io.tmpdir"),
				"row-split-ledger/later-vertical-flex-rows");
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
		final int pages = convertFile("later-vertical-flex-rows", dir, input);
		assertTrue("後続行の寸法が増幅している(ページ数=" + pages + ")", pages <= 30);
		final java.util.regex.Pattern xPattern = java.util.regex.Pattern.compile("\\bx=(-?[0-9.E+-]+)");
		final StringBuilder displayLists = new StringBuilder();
		for (int p = 1; p <= pages; ++p) {
			final String dl = java.nio.file.Files.readString(
					new File(dir, String.format("page-%04d.txt", p)).toPath(), StandardCharsets.UTF_8);
			displayLists.append(dl);
			final java.util.regex.Matcher matcher = xPattern.matcher(dl);
			while (matcher.find()) {
				final double x = Double.parseDouble(matcher.group(1));
				assertTrue("後続行のx座標が増幅しています: " + x + " (page=" + p + ")", Math.abs(x) < 500);
			}
		}
		for (final String token : new String[] { "T181", "T223", "T230", "T256" }) {
			assertTrue("内容が欠落しています: " + token, displayLists.indexOf(token) >= 0);
		}
	}

	private static int convertFile(final String name, final File dir, final File input) throws Exception {
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
		}, name, 64L * 1024 * 1024);
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
		return pages.length;
	}

	private static int convert(final String name, final String wrapCss) throws Exception {
		final StringBuilder html = new StringBuilder();
		html.append("""
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<?jp.cssj.property name="output.page-width" value="200pt"?>
				<?jp.cssj.property name="output.page-height" value="200pt"?>
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				<style>
				@page{margin:10pt}
				body{font:normal 9pt/1.2 serif;margin:0}
				""").append(wrapCss).append("""
				</style></head><body>
				<div class="wrap">
				<div class="nav">NAV</div>
				<div class="main">
				""");
		for (int i = 0; i < PARAGRAPHS; ++i) {
			html.append("<div><p>Paragraph ").append(i)
					.append(" with some text that wraps a little bit more.</p></div>\n");
		}
		html.append("</div>\n</div>\n<p>AFTER</p>\n</body></html>\n");

		final File dir = new File("local/row-split-ledger/" + name);
		dir.mkdirs();
		final File[] old = dir.listFiles();
		if (old != null) {
			for (final File f : old) {
				f.delete();
			}
		}
		final File input = new File(dir, "input.html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html.toString());
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
		}, name, 64L * 1024 * 1024);
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
		return pages.length;
	}
}
