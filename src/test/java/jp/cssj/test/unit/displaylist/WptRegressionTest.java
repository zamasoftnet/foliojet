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
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * <b>WPTコーパスで見つかった不具合</b>の回帰テストです(2026-07-28新設)。
 *
 * <p>
 * WPT(`css/css-break`・`css-multicol`・`css-page`)の2,409文書を不変条件
 * 1〜3(例外で中断しない・停止する・ページ数が有界)にかけて見つけたもの
 * です({@link WptCorpusTest})。<b>20万文書のランダム掃過では1件も出て
 * いません</b>——生成器が作らない形(インラインの中のぶち抜き、
 * {@code column-width:0}、32bit幅の枠線)を突いたためです。
 * </p>
 *
 * <p>
 * <b>再現条件は問題ごとに違います。</b> ぶち抜きと枠線は既定のA4でも落ちますが、
 * {@code column-width:0}は<b>小さい紙面でないと再現しません</b>。掃過が
 * 120x120ptで回しているのはそのためで、テストも同じ条件を作ります。
 * 「小さい紙面でだけページ数が爆発する」種類(grid等)は、退化した幾何の
 * 問題として別に扱います(`docs/NEXT-SESSION.md`)。
 * </p>
 *
 * <h2>機序: インラインの中の{@code column-span:all}</h2>
 *
 * <p>
 * 2,409文書中10件が同一原因で
 * {@code IndexOutOfBoundsException: Index -1 out of bounds for length 0}
 * になっていました。
 * </p>
 *
 * <p>
 * {@code DocumentBuilder.startBox}のFLOW分岐は、ぶち抜き
 * ({@code column-span:all})のとき<b>{@code startColumnSpan}を先に</b>
 * 呼び、そのあとで{@code closeInlines}していました。
 * {@code startColumnSpan}は段組を抜けるために{@code endFlowBlock}まで
 * 戻すので、その時点で{@code containerBuilder}が差し替わります。
 * すると{@code closeInlines}が出す{@code endInline}は、<b>対応する
 * {@code startInline}を見ていない新しい{@code StyledTextUnitizer}</b>へ
 * 届きます。その{@code InlineParamsStack}は根しか積んでいないため、
 * popが根を外し、{@code current()}が空リストを引いて落ちます。
 * </p>
 *
 * <p>
 * 開いているインラインは<b>ぶち抜き前の文脈で開かれた</b>ものなので、
 * その文脈で閉じなければなりません——{@code closeInlines}を先に、
 * 復元({@code restoreInlines})は{@code endColumnSpan}の後に、と
 * 入れ子を正しました。
 * </p>
 *
 * <p>
 * <b>まだ直っていない場合があります</b>: ぶち抜きがインラインの中の
 * <b>ブロックのさらに中</b>にあると、{@code startColumnSpan}自身が
 * {@code restoreInlines}でインラインを開き直したうえで
 * {@code endFlowBlock}するため、同じ型の不均衡が残ります
 * ({@code multicol-span-all-children-height-010}等2件)。
 * {@code InlineParamsStack.pop}に番人を置くだけでは<b>別のnullへ
 * ずれるだけ</b>で直らないことを確認済みです(`docs/NEXT-SESSION.md`)。
 * </p>
 */
public class WptRegressionTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/** 1文書あたりの上限時間。通常は1秒未満で終わる。 */
	private static final long WATCHDOG_MS = 60_000L;

	public WptRegressionTest(String name) {
		super(name);
	}

	/**
	 * 最小形。{@code <span>}の直下にぶち抜きブロックがある。
	 * WPTの{@code css-multicol/spanner-in-child-after-parallel-flow-003}
	 * 等がこの形。
	 */
	private static final String SPANNER_IN_INLINE = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			</head><body>
			<div style="columns:2; width:100px">
			<span>
			<div style="column-span:all; height:10px; background:green"></div>
			</span>
			</div>
			</body></html>
			""";

	/**
	 * ぶち抜きの前にインラインの内容がある形
	 * ({@code css-multicol/multicol-span-all-019}の骨格)。
	 * インラインが実際に文字を持っていると、{@code endInline}が
	 * グリフパイプラインを通るため経路が変わる。
	 */
	private static final String SPANNER_IN_INLINE_WITH_TEXT = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>body{font:normal 10pt/1.2 serif}</style>
			</head><body>
			<div style="columns:2; width:100px; orphans:1; widows:1">
			<div style="height:15px">
			<span>ABC DEF
			<div style="column-span:all; height:20px; background:green"></div>
			</span>
			</div>
			<div style="height:40px"></div>
			</div>
			</body></html>
			""";

	/** ぶち抜きが置換要素の場合({@code addReplacedBox}側の同じ順序)。 */
	private static final String REPLACED_SPANNER_IN_INLINE = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			</head><body>
			<div style="columns:2; width:100px">
			<span>XY
			<img src="@IMG@" style="column-span:all; display:block; width:20pt" />
			</span>
			</div>
			</body></html>
			""";

	public void testSpannerDirectlyInsideInline() throws Exception {
		convertWithin("spanner-in-inline", SPANNER_IN_INLINE);
	}

	public void testSpannerInsideInlineWithText() throws Exception {
		convertWithin("spanner-in-inline-text", SPANNER_IN_INLINE_WITH_TEXT);
	}

	/**
	 * {@code column-width:0}が現実的な時間で終わること(2026-07-28、WPT
	 * {@code css-multicol/zero-column-width-layout.html})。
	 *
	 * <p>
	 * css-multicol-1 §3.1は「{@code column-width:0}は指定値・計算値としては
	 * 正当だが、<b>使用値が1pxを下回ることはない</b>」と定めています。
	 * 丸めないと{@code LayoutUtils.getColumnCount}の除算が0除算になり、
	 * {@code (int)Infinity} = 2,147,483,647段を作ろうとします。
	 * </p>
	 *
	 * <p>
	 * <b>厳密には無限ループではなく「極端に遅い」</b>——実測すると修正前でも
	 * <b>約50秒</b>で終わります。掃過の打ち切りが30秒なので「停止しない」と
	 * 分類されていました。したがってこのテストは<b>短い予算</b>で測ります
	 * ——既定の60秒だと修正を戻しても緑のままで、回帰を検出できません
	 * (2026-07-28に実際に踏んだ)。修正後は1秒未満です。
	 * </p>
	 */
	public void testZeroColumnWidthIsFast() throws Exception {
		// **文書は組み立てず、WPTの原本をそのまま使う**
		// (files/unittest/0490-robustness/wpt-zero-column-width.html)。
		// 骨格だけを写した最小形をいくつも試したが、どれも再現しなかった。
		// **小さい紙面**も必須で、しかもPIではなくセッションプロパティで
		// 与えないと再現しない(WPTの掃過と同じ経路にすること)
		convertWithinFile("wpt-zero-column-width.html", "120x120", 15_000L);
	}

	/**
	 * 巨大な{@code border-width}でも変換が失敗しないこと(2026-07-28、WPT
	 * {@code css-break/grid/grid-large-end-border-crash.html})。
	 *
	 * <p>
	 * {@code 4294967295px}は3.22e9ptになり、
	 * {@code BackgroundBorderDrawable}の「描画高が異常」assertで<b>変換が
	 * 失敗</b>していました。{@code Border.MAX_WIDTH}へ丸めます——
	 * {@code colspan}/{@code rowspan}をHTML Standardの上限へ丸めたのと
	 * 同じ立場です。
	 * </p>
	 */
	public void testHugeBorderWidthDoesNotFail() throws Exception {
		convertWithin("huge-border", """
				<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
				<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
				</head><body>
				<div style="column-count:2; column-fill:auto; border-bottom:4294967295px solid">
				<div style="display:grid; padding-top:1px; border-bottom:4294967295px solid">
				<div></div>
				</div>
				</div>
				</body></html>
				""");
	}

	public void testReplacedSpannerInsideInline() throws Exception {
		final File png = new File("files/unittest/red.png");
		assertTrue("テスト画像が見つからない: " + png.getAbsolutePath(), png.isFile());
		convertWithin("replaced-spanner-in-inline",
				REPLACED_SPANNER_IN_INLINE.replace("@IMG@", png.toURI().toString()));
	}

	/**
	 * 文書を別スレッドで変換し、{@link #WATCHDOG_MS}以内に例外なく
	 * 終わることを確認します({@code SpanRobustnessTest}と同じ形)。
	 */
	private static void convertWithin(final String name, final String html) throws Exception {
		convertWithin(name, html, null);
	}

	/**
	 * {@code files/unittest/0490-robustness/}に置いた文書をそのまま変換します。
	 *
	 * <p>
	 * 他のケースは文書をここで組み立てますが({@code docs/LESSONS.md} §6.9h)、
	 * <b>骨格を写すと再現しない</b>ものはWPTの原本を取り込んで使います。
	 * 再現しない最小形で固定しても、修正を戻したときに落ちないので
     * 回帰テストになりません。
	 * </p>
	 */
	private static void convertWithinFile(final String fileName, final String pageSize, final long budgetMs)
			throws Exception {
		final File input = new File("files/unittest/0490-robustness/" + fileName);
		assertTrue("テスト文書が見つからない: " + input.getAbsolutePath(), input.isFile());
		final File dir = new File("local/unittest/wpt-regression");
		dir.mkdirs();
		runWithin(fileName, input, new File(dir, fileName + ".pdf"), pageSize, budgetMs);
	}

	/**
	 * @param pageSize {@code "120x120"}(pt)のような紙面指定。{@code null}なら既定。
	 *                 <b>PIではなくセッションプロパティで与えます</b>——WPTの掃過
	 *                 ({@link WptCorpusTest})がそうしているためで、PIで書くと
	 *                 同じ条件にならず、修正を戻してもテストが緑のままになります
	 *                 (2026-07-28に実際に踏んだ)
	 */
	private static void convertWithin(final String name, final String html, final String pageSize) throws Exception {
		final File dir = new File("local/unittest/wpt-regression");
		dir.mkdirs();
		final File input = new File(dir, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(input), StandardCharsets.UTF_8)) {
			w.write(html);
		}

		runWithin(name, input, new File(dir, name + ".pdf"), pageSize, WATCHDOG_MS);
	}

	/** 別スレッドで変換し、{@code budgetMs}以内に例外なく終わることを確認します。 */
	private static void runWithin(final String name, final File input, final File pdf, final String pageSize,
			final long budgetMs) throws Exception {
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(() -> {
			try {
				convert(input, pdf, pageSize);
			} catch (final Throwable t) {
				failure[0] = t;
			}
		}, "wpt-regression-" + name);
		worker.setDaemon(true);
		worker.start();
		worker.join(budgetMs);
		if (worker.isAlive()) {
			fail(name + ": " + budgetMs + "ms以内に変換が終わりませんでした");
		}
		if (failure[0] != null) {
			throw new AssertionError(name + ": 変換が例外で終わりました", failure[0]);
		}
	}

	private static void convert(final File input, final File pdf, final String pageSize) throws Exception {
		try (OutputStream out = new FileOutputStream(pdf)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				if (pageSize != null) {
					final int x = pageSize.indexOf('x');
					session.property("output.page-width", pageSize.substring(0, x) + "pt");
					session.property("output.page-height", pageSize.substring(x + 1) + "pt");
				}
				CTISessionHelper.transcodeFile(session, input, "text/html", null);
			} finally {
				session.close();
			}
		}
	}
}
