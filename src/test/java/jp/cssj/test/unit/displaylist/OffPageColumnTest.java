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
 * <h2>機序2(未修正)</h2>
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
 * これ。
 * </p>
 *
 * <p>
 * <b>「段を使い切ったら{@code false}を返す」は入れてはいけません</b>
 * (2026-07-28に実測して撤回)。ビルダーの各所が<b>「要求した改ページは
 * 必ず起きる」</b>を前提に書かれており、{@code false}を返すと次々に壊れます:
 * </p>
 * <ol>
 * <li>{@code endFlowBlock()}の浮動体切断ループ({@code breakFloats}は
 * {@code beginBreak()}でしか空にならない)——<b>無限ループ</b></li>
 * <li>{@code flush()}の行間改ページ(直後に{@code textBuilder}を無検査で
 * 使う)——AssertionError/NPE</li>
 * <li>1と2を個別に直しても、再生中の{@code TextBuilder.finish()}で別の
 * assertionが発火</li>
 * </ol>
 * <p>
 * 50,000シードの掃過で<b>strict 102件 + wild 65件</b>の新規失敗
 * ({@code textBuilderが開いたまま})を出したため元に戻しました。機序2を
 * 直すには「もう断片を作れない」をビルダー全体の契約として扱う作業が必要で、
 * 1つの変更で安全に収まりません。
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
	 * 変換して、(1) 説明のつかない紙面外への配置がないこと、(2) T0..T(n-1) の
	 * トークンが全部どこかのページに現れること、を検査します。
	 *
	 * @param name            作業ディレクトリ名
	 * @param html            文書
	 * @param pageWidth       紙面の幅(pt)
	 * @param pageHeight      紙面の高さ(pt)
	 * @param maxExplicitSize 文書が指定した{@code width}/{@code height}の最大値
	 * @param tokenCount      文書が持つ T トークンの数
	 */
	private static void assertNoUnexplainedOffPage(final String name, final String html, final double pageWidth,
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
	}
}
