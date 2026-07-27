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
 * <b>段数倍に膨らんだ最小内容寸法で内容を紙面外へ置かない</b>ことを固定します
 * (2026-07-28新設)。
 *
 * <p>
 * {@code RandomDocumentFuzzTest}の<b>不変条件6</b>(説明のつかない紙面外への
 * 配置)で最後まで残っていた欠陥種別です。50,000シードで8件、いずれも
 * <b>縦書き</b>と<b>段組</b>を含んでいました
 * (`copperpdf4/docs/REVIEW-STATISTICS.md` §12)。実測すると<b>独立した2つの
 * 機序</b>で、6件が機序1、2件が機序2でした。ここで固定するのは<b>機序1だけ</b>
 * です。
 * </p>
 *
 * <h2>機序1(修正済み・このテストが固定する)</h2>
 *
 * <p>
 * {@code fit-content}は{@code max(min-content, min(available, max-content))}
 * なので、<b>最小内容寸法が使える空間より大きいとそれがそのまま採用される</b>。
 * 段組の最小内容寸法は「段数 × 中身の最小内容寸法 + 段間」で<b>段数倍に
 * 膨らむ</b>ため、入れ子の段組では紙の何倍にもなる。しかも<b>行軸は分割
 * できない</b>(ページ分割はページ軸にしか効かない)ので、あふれた内容は
 * 次のページへ送られず紙の外の座標に描かれる。
 * </p>
 *
 * <p>
 * この文書(seed 35842 由来)は<b>明示サイズを一切含まない</b>のに、130ptの
 * 行軸に対して264ptの箱ができ、内容が y=−260(紙面は150pt)に描かれていた。
 * 修正は{@code AbstractStaticBlockBox.shrinkToFit}で、<b>段数倍が効いたとき
 * だけ</b>({@code IntrinsicSizes.columnInflated})行軸を使える空間で
 * 頭打ちにする。
 * </p>
 *
 * <h2>機序2(修正済み・このテストが固定する)</h2>
 *
 * <p>
 * <b>段に収まらない不可分な箱</b>(画像・インラインブロック)があると、
 * {@code BreakableBuilder.addBound()}の「はみ出している間{@code autoBreak()}を
 * 呼ぶ」ループが回り続ける。{@code findColumnBreak()}は
 * {@code AbstractContainerBox.canColumnBreak()}で段数の上限を守るのに、そこで
 * 断られた{@code autoBreak()}は最後の手段として{@code pageBreak()}を呼び、
 * 段組の中ではそれが<b>無条件に改段する</b>{@code ColumnBuilder.pageBreak()}
 * である。実測(seed 46577)では{@code column-count:4}が<b>14段</b>まで増え、
 * 内容が y=2,835(紙面は842pt)へ描かれた。残り2件(seeds 45399, 46577)が
 * これだった。
 * </p>
 *
 * <p>
 * <b>「段を使い切ったら{@code false}を返す」だけでは入りません</b>
 * (2026-07-28に一度実測して撤回した)。ビルダーの各所が<b>「要求した改ページは
 * 必ず起きる」</b>を前提に書かれており、素朴に{@code false}を返すと次々に
 * 壊れます:
 * </p>
 * <ol>
 * <li>{@code endFlowBlock()}の浮動体切断ループ({@code breakFloats}は
 * {@code beginBreak()}でしか空にならない)——<b>無限ループ</b></li>
 * <li>{@code flush()}の行間改ページ(直後に{@code textBuilder}を無検査で
 * 使う)——AssertionError/NPE</li>
 * <li>1と2を個別に直しても、再生中の{@code TextBuilder.finish()}で別の
 * assertionが発火(空のテキストブロックを閉じてしまうため)</li>
 * </ol>
 *
 * <p>
 * <b>入った形</b>(2026-07-28): 戻り値を後から見て取り繕うのをやめ、
 * <b>飛ぶ前に訊く</b>{@code BreakableBuilder.canFragmentFurther()}を
 * 契約として足した。既定は{@code true}、{@code ColumnBuilder}だけが
 * 「段を使い切った」ときに{@code false}を返す。危険な2か所——
 * {@code flush()}の行間改ページと{@code endFlowBlock()}の浮動体切断ループ
 * ——は、改ページを<b>試みる前に</b>これを見て、テキストブロックを閉じずに
 * 素通りする/予約を捨てて抜ける。あわせて
 * {@code ColumnBuilder.pageBreak()}は、<b>自動</b>改ページで段数を
 * 使い切っていたら{@code beginBreak()}を呼んでから{@code false}を返す
 * ({@code RootBuilder.pageBreak()}が「改ページ点なし」で{@code false}を
 * 返すときと同じ契約——{@code breakFloats}が空になることが上記ループの
 * 停止条件になっている)。<b>強制</b>改ページは作者が段を要求したものなので
 * 従来どおり段を作る({@code ContinuationStats.guardBreakProgress}が自動
 * 改ページだけを見張るのと同じ理由)。
 * </p>
 *
 * <p>
 * <b>この判定は「失敗しても壊れない」側に倒すこと</b>——最初の実装は
 * {@code canFragmentFurther()}を{@code findColumnBreak() != null ||
 * canColumnBreak()}としており、50,000シードの掃過で<b>strict 3件 +
 * wild 5件</b>の変換失敗を出しました(seeds 2928/40824/41678,
 * 10322/10538/15952/19100/37455)。入れ子の段組が{@code flowStack}に
 * <b>あっても</b>{@code columnBreak()}は{@code Keep}/{@code Move}で
 * 失敗しうるため、「訊いたら大丈夫と言われたのに飛べなかった」が起きます。
 * <b>この失敗の見え方に注意</b>: 種別名は
 * {@code 不変条件: textBuilderが開いたまま}ですが、実際に発火するのは
 * {@code BreakableBuilder.flush()}の{@code assert this.textBuilder != null}
 * ——つまり<b>開いたままではなく null</b> です
 * ({@code RandomDocumentFuzzTest.classify}が{@code "Unexpected error."}を
 * 一括でこの名前に寄せているだけ)。
 * </p>
 *
 * <h2>判定について</h2>
 *
 * <p>
 * <b>ファジングの不変条件6と同じ基準</b>にします——紙面をまるごと1枚分
 * はみ出し、かつ文書中の最大の明示サイズの2倍を超えたときだけ数える。
 * {@code overflow}の既定値は{@code visible}なので、箱からはみ出した内容を
 * 紙の外に描くこと自体は正しい挙動であり、素朴に「紙面内」を要求すると
 * 正当な文書が軒並み落ちます。
 * </p>
 *
 * <p>
 * <b>文書はここで組み立てます</b>——外部ファイルにすると相対パスの画像参照で
 * 判定が変わる事故を起こします(docs/LESSONS.md §6.9h)。画像を使わない
 * 縮小形を選んであるのはそのためです。
 * </p>
 *
 * <p>
 * <b>紙面外は「内容を捨てる」ことでも消せる</b>ので、トークンの残存も
 * 併せて検査します。
 * </p>
 */
public class OffPageColumnTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	/** 打ち切り時間。実測は1件あたり1秒未満。 */
	private static final long WATCHDOG_MS = 60_000L;

	/** 表示リストの描画位置。{@code RandomDocumentFuzzTest}と同じ書式。 */
	private static final Pattern POS_IN_DUMP = Pattern.compile("x=(-?[\\d.]+) y=(-?[\\d.]+)");

	public OffPageColumnTest(String name) {
		super(name);
	}

	/**
	 * 機序1: 直交書字の中の入れ子段組。<b>明示サイズを一切含まない</b>ので、
	 * わずかなはみ出しでも不変条件6にかかる。
	 */
	private static final String ORTHOGONAL = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="300pt"?>
			<?jp.cssj.property name="output.page-height" value="150pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:10pt}
			body{margin:0;font:normal 13pt/1.2 serif;writing-mode:horizontal-tb}
			p,div{margin:0;padding:0}
			</style></head><body>
			<div style="writing-mode:vertical-lr">
			<div style="column-count:3;column-gap:5pt">
			<div style="column-count:4;column-gap:9pt">
			<p>T0</p>
			<p>T1</p>
			<p>T2</p>
			</div>
			</div>
			</div>
			</body></html>
			""";

	public void testOrthogonalNestedColumnsStayOnPage() throws Exception {
		assertNoUnexplainedOffPage("orthogonal", ORTHOGONAL, 300, 150, 0, 3);
	}

	/**
	 * 機序2: <b>段に収まらない不可分な箱</b>が段を無限に増やす
	 * (seed 46577の縮小形。画像を{@code display:inline-block}へ置き換えた
	 * だけで、表示リストの数値は元の文書と<b>1ptも変わらない</b>ことを
	 * 確認済み——修正前の最悪はみ出しはどちらも{@code y=2835.08}の1,151pt)。
	 *
	 * <p>
	 * {@code column-count:4}に対して<b>14段</b>できていた。段は行方向
	 * (この文書は縦書きなので<b>y</b>)に{@code i×(段幅+段間)}で並ぶので、
	 * 段が増えるほど内容はまっすぐ紙の外へ出ていく。
	 * </p>
	 */
	private static final String COLUMN_BUDGET = """
			<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01//EN">
			<?jp.cssj.property name="output.page-width" value="595pt"?>
			<?jp.cssj.property name="output.page-height" value="842pt"?>
			<html><head><meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<style>
			@page{margin:5pt}
			body{margin:0;font:normal 13pt/1.2 serif;writing-mode:vertical-rl}
			p,div,td{margin:0;padding:0}
			table{border-collapse:separate;table-layout:fixed}
			td{border:1pt solid black}
			</style></head><body>
			<div style="column-count:4;column-gap:19pt">
			<div style="float:right;width:44pt">
			<p>T0 T1 T2 T3 T4 T5 </p>
			<p><span style="display:inline-block;width:249pt;height:236pt">T6</span></p>
			</div>
			<table><tbody>
			<tr><td>T7</td><td rowspan="3">T8</td><td colspan="2">T9</td></tr>
			<tr><td>T10</td><td>T11</td><td colspan="3">T12</td></tr>
			<tr><td>T13</td><td colspan="1" rowspan="3">T14</td><td rowspan="3">T15</td></tr>
			</tbody></table>
			</div>
			</body></html>
			""";

	/**
	 * <b>段数の上限を超えて段を作らない</b>ことを固定します(2026-07-28新設)。
	 *
	 * <p>
	 * 判定は2本立てです。1本目は不変条件6と同じ基準(明示サイズ249ptなので
	 * 猶予は498pt)——修正前は<b>1,151pt</b>で落ちます。ただしこれは
	 * 「ひどさ」の判定であって<b>段数</b>の判定ではないので、2本目に
	 * <b>行方向(この文書ではy)に描かれるものが紙面の高さに収まる</b>ことを
	 * 要求します。段は{@code i×(段幅+段間)=i×212.75pt}に並ぶので、
	 * 4段なら最後の段は{@code y=596..789.75}——実測の最大は{@code y=707.58}
	 * です。5段目ができた時点で{@code y≧808.75}になり、この検査は落ちます
	 * (修正前の実測は{@code y=2835.08}=14段)。
	 * </p>
	 *
	 * <p>
	 * <b>紙面の高さ(842pt)を閾値にしているのは、それが「段が紙に収まって
	 * いる」と言えるいちばん素直な線だから</b>です。{@code overflow}の既定は
	 * {@code visible}なので<b>ページ方向</b>(x)のはみ出しは正当であり、
	 * ここでは問いません——実際、修正後も{@code T6}の断片は{@code x=620.8}
	 * (紙面幅595pt)に描かれます。これが本修正の設計そのもので、
	 * <b>分割できない行方向へ伸ばす代わりに、最後の段の中であふれさせる</b>。
	 * </p>
	 */
	public void testColumnCountIsNotExceeded() throws Exception {
		final File dir = assertNoUnexplainedOffPage("column-budget", COLUMN_BUDGET, 595, 842, 249, 16);

		double maxY = 0;
		String at = null;
		final File[] pages = dir.listFiles((d, n) -> n.endsWith(".txt"));
		java.util.Arrays.sort(pages);
		for (final File page : pages) {
			final String dump = java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8);
			final Matcher m = POS_IN_DUMP.matcher(dump);
			while (m.find()) {
				final double y = Double.parseDouble(m.group(2));
				if (y > maxY) {
					maxY = y;
					at = m.group(0) + " " + page.getName();
				}
			}
		}
		assertTrue("段を作りすぎている: 行方向の最大 y=" + Math.round(maxY) + "pt (紙面の高さ842pt, " + at
				+ ")。段は i×212.75pt に並ぶので、これは段" + (int) Math.floor(maxY / 212.75 + 1) + "に相当する"
				+ "(column-count は 4)", maxY <= 842);
	}

	/**
	 * 変換して、(1) 説明のつかない紙面外への配置がないこと、(2) T0..T(n-1) の
	 * トークンが全部どこかのページに現れること、を検査します。
	 *
	 * @param name            作業ディレクトリ名
	 * @param html            文書
	 * @param pageWidth       紙面の幅(pt)
	 * @param pageHeight      紙面の高さ(pt)
	 * @param maxExplicitSize 文書が指定した{@code width}/{@code height}の最大値
	 * @param tokenCount      文書が持つ T トークンの数
	 * @return 表示リストのダンプを置いた作業ディレクトリ(追加の判定用)
	 */
	private static File assertNoUnexplainedOffPage(final String name, final String html, final double pageWidth,
			final double pageHeight, final double maxExplicitSize, final int tokenCount) throws Exception {
		final File dir = new File("local/off-page-column/" + name);
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
		}, "off-page-column-" + name, 64L * 1024 * 1024);
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
		java.util.Arrays.sort(pages);

		// 紙面をまるごと1枚分はみ出して初めて数え、明示サイズの2倍までは
		// 「作者の指定の帰結」として見逃す(不変条件6と同じ基準)
		final double slack = 2 * maxExplicitSize;
		double worst = 0;
		String worstAt = null;
		final StringBuilder all = new StringBuilder();
		for (final File page : pages) {
			final String dump = java.nio.file.Files.readString(page.toPath(), StandardCharsets.UTF_8);
			all.append(dump);
			final Matcher m = POS_IN_DUMP.matcher(dump);
			while (m.find()) {
				final double x = Double.parseDouble(m.group(1)), y = Double.parseDouble(m.group(2));
				final double over = Math.max(Math.max(-x - pageWidth, x - 2 * pageWidth),
						Math.max(-y - pageHeight, y - 2 * pageHeight));
				if (over > worst) {
					worst = over;
					worstAt = "x=" + x + " y=" + y + " " + page.getName();
				}
			}
		}
		assertTrue(name + ": 紙面外への配置 " + Math.round(worst) + "pt (紙面" + Math.round(pageWidth) + "x"
				+ Math.round(pageHeight) + "pt, 最大明示サイズ" + Math.round(maxExplicitSize) + "pt, " + worstAt + ", 全"
				+ pages.length + "ページ)", worst <= slack);

		// 紙面外は「内容を捨てる」ことでも消せる。それが退行として見えるように
		final List<String> lost = new ArrayList<>();
		for (int i = 0; i < tokenCount; ++i) {
			if (all.indexOf("T" + i) < 0) {
				lost.add("T" + i);
			}
		}
		assertTrue(name + ": 内容が失われた " + lost, lost.isEmpty());
		return dir;
	}
}
