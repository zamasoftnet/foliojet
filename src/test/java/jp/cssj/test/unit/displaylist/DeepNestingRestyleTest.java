package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * 深いネスト+改ページ(restyle系が実際に発火する構成)に対する回帰テストです
 * (ARCHITECTURE.md不変条件6、2026-07-20)。
 *
 * <p>
 * {@link DeepNestingLayoutTest}はfinishLayout/frames/draw/textShape/getText
 * の反復化を固定するテストだが、意図的にページ分割を誘発しない構成
 * (ネストした<code>&lt;div&gt;</code>群がすべて1ページに収まる)のため、
 * {@code restyle}系(継続機構、まだ`FlowContainer.restyle`↔
 * `AbstractContainerBox.restyle`のポリモーフィックな相互再帰のまま)を
 * 経路に含まない。
 * </p>
 *
 * <p>
 * このテストは逆に、深いネスト構造の最深部に複数ページ分の内容を置き、
 * ネストの祖先チェーンが**開いたまま**ページ分割を跨ぐ構成にする
 * ({@code OpenShape.OpenChain}が深く入れ子になり、
 * {@code FlowContainer.restyle}・{@code RootBuilder.resumeFrame}の
 * 相互再帰が実際に深さ分だけ発火する)。restyle系の反復化(codex/grok
 * への外部相談、docs/consultations/consult-restyle-*.md参照)に着手する
 * 前の回帰基盤として、現状の再帰実装がどこまでの深さに耐えるかを
 * 実測・記録する。
 * </p>
 *
 * <p>
 * <b>実測結果(2026-07-20、初回)</b>: 深さ200・500は成功、深さ1000・5000は
 * {@code StackOverflowError}。ただしスタックトレースを実際に確認したところ、
 * 直接の原因は当初想定した{@code restyle}系ではなく、
 * {@code FlowContainer.avoidBreakBefore/After}(改ページ回避判定、
 * `FlowContainer`↔`FlowBlockBox`の同型のポリモーフィック相互再帰)
 * だった。これは{@code restyle}以前の、改ページ位置探索(break-point
 * search)の中で発火する別系統の再帰であり、restyle系の反復化に着手する
 * 前に本テストで新たに発見された(restyle系はこの手前で既に
 * StackOverflowErrorしていたため、まだ経路にすら到達していなかった)。
 * </p>
 *
 * <p>
 * <b>対応(2026-07-20)</b>: {@code FlowContainer.avoidBreakBefore/After}を
 * 明示的{@link java.util.Deque}ワークリストへ反復化した
 * (finishLayout等と同じ設計パターン。{@code FlowContainer}の
 * {@code walkAvoidBreak}参照)。
 * </p>
 *
 * <p>
 * <b>実測結果(2026-07-20、avoidBreakBefore/After反復化後)</b>: 深さ1000・
 * 5000は依然{@code StackOverflowError}だが、発生箇所が
 * {@code FlowContainer.splitPageAxis}↔{@code AbstractBlockBox.splitForContinuation}
 * (改ページ時のボックス分割=ARCHITECTURE.mdのパイプライン図でいう
 * 「splitPageAxis(変異切断)」)へ移った。この経路は
 * {@code restyle}よりさらに手前(改ページの実行そのもの)で走る、
 * 第三の独立した再帰系統である。{@code restyle}系自体の反復化が実際に
 * 必要になる深さへは、この{@code splitPageAxis}の壁が先に立ちはだかる
 * ため、本テストではまだ到達できていない。
 * </p>
 *
 * <p>
 * <b>{@code splitPageAxis}に今は着手しない理由</b>: `docs/NEXT-SESSION.md`
 * 「Box/Builderコア: FlowContainer.splitPageAxisの核心ループはM6d前提の
 * まま」に既存の記録があるとおり、{@code splitPageAxis}/{@code .split()}
 * 呼び出しは子ボックスを直接変異させる構造であり、
 * ConstraintSpace/write-onceボックス(M6d)の設計が入るまでは安全な
 * 機械的リファクタが難しいと既に判断されている。この既存判断を覆さず、
 * 深さ1000・5000のテストは「splitPageAxisがM6d後に反復化されるまでの
 * 既知の限界」として固定する(restyle系の反復化の完了条件では、もはや
 * ない)。
 * </p>
 *
 * <p>
 * <b>B0(2026-07-20、codex/grokへの外部相談後)</b>:
 * {@code ContinuationStats.RESTYLE_CHAIN_FIRINGS}
 * (`FlowContainer.restyle`の{@code OpenShape.OpenChain}分岐発火数)を
 * 深さ200で計測したところ0のままだった。実際に発火していたのは
 * {@code CHILD_FRAMES}(1206回)——単純な「1段1子」の深いネストは
 * {@code FlowContainer.restyle}の{@code OpenChain}分岐ではなく
 * {@code RootBuilder.resumeFrame()}自身の自己再帰
 * (`ContinuationFrame.Child`を1段ごとに1回)で処理されていた。この
 * 自己再帰はswitch文の唯一かつ末尾の文(真の末尾再帰)だったため、
 * {@code while}ループへの書き換えのみで挙動を変えず反復化した(修正済み、
 * 三層検証済み)。`docs/NEXT-SESSION.md`「B0着手結果」参照。
 * </p>
 */
public class DeepNestingRestyleTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * 深さ200の開いた祖先チェーンが複数ページの改ページを跨ぐ構成。
	 * 実測(2026-07-20)では深さ500まで成功し、1000でStackOverflowError
	 * に到達する(下記{@link #testDepth1000OpenChainAcrossPageBreaksCurrentlyOverflows}
	 * 参照)。この深さ200は「現状でも安全な下限」を回帰として固定する。
	 *
	 * <p>
	 * 併せて{@code ContinuationStats.RESTYLE_CHAIN_FIRINGS}
	 * (M6b Phase B「切断ブロックチェーン」ソース再生化のB0=発火可視化、
	 * 2026-07-20。codex/grokへの外部相談、
	 * docs/consultations/consult-open-chain-replay-*.md参照)を計測し、
	 * この構成で開いたチェーン経由のbox-restyleが実際に多数発火している
	 * ことを固定する。ソース再生化が進むほどこの値は下がるべきで、将来の
	 * 段階ごとの縮小を実測するための基準値としてここに記録する。
	 * </p>
	 */
	public void testDepth200OpenChainAcrossPageBreaks() throws Exception {
		net.zamasoft.foliojet.layout.fragment.ContinuationStats.reset();
		this.runDeepOpenChain(200, 300);
		System.err.println("深さ200: RESTYLE_CHAIN_FIRINGS="
				+ net.zamasoft.foliojet.layout.fragment.ContinuationStats.RESTYLE_CHAIN_FIRINGS.get()
				+ " CHILD_FRAMES=" + net.zamasoft.foliojet.layout.fragment.ContinuationStats.CHILD_FRAMES.get()
				+ " OPEN_TAILS=" + net.zamasoft.foliojet.layout.fragment.ContinuationStats.OPEN_TAILS.get()
				+ " UNCHAINED_RESTYLES="
				+ net.zamasoft.foliojet.layout.fragment.ContinuationStats.UNCHAINED_RESTYLES.get()
				+ " MAX_PAGE_OPEN_TAIL_DEPTH="
				+ net.zamasoft.foliojet.layout.fragment.ContinuationStats.MAX_PAGE_OPEN_TAIL_DEPTH.get());
	}

	/**
	 * 深さ500。実測(2026-07-20)ではまだ成功する上限側の境界。
	 */
	public void testDepth500OpenChainAcrossPageBreaks() throws Exception {
		this.runDeepOpenChain(500, 300);
	}

	/**
	 * 深さ1000(finishLayout修正前の実測限界=1000段と同水準)。
	 *
	 * <p>
	 * <b>既知の現状の限界(2026-07-20実測、avoidBreakBefore/After反復化後)
	 * </b>: {@code FlowContainer.splitPageAxis}↔
	 * {@code AbstractBlockBox.splitForContinuation}(改ページ時のボックス
	 * 分割)がこの深さで実際に{@code StackOverflowError}に到達する
	 * (クラスjavadoc参照)。この経路は`docs/NEXT-SESSION.md`
	 * 「splitPageAxisの核心ループはM6d前提のまま」に既存の記録がある
	 * とおり、子ボックスを直接変異させる構造であり、
	 * ConstraintSpace/write-onceボックス(M6d)の設計が入るまでは安全な
	 * 反復化が難しいと既に判断されている。restyle系自体の反復化は、
	 * この手前の壁のためまだ経路にすら到達できていない。
	 * </p>
	 *
	 * <p>
	 * M6d設計後にsplitPageAxisが反復化されたら、このテストは
	 * {@link #testDepth5000OpenChainAcrossPageBreaksCurrentlyOverflows}
	 * とあわせて「成功する」側のアサーションへ書き換えること
	 * (このメソッド名の"CurrentlyOverflows"を外し、
	 * {@code runDeepOpenChain}で成功を確認する形に戻す)。それでもなお
	 * 別の深さで{@code StackOverflowError}に到達する場合、その時点で
	 * ようやくrestyle系自体の反復化の要否を実測で判断できる。
	 * </p>
	 */
	public void testDepth1000OpenChainAcrossPageBreaks() throws Exception {
		// 2026-07-26: レイアウトを常に64MBスタックの専用スレッドで実行する
		// ようにした(DirectSession.LAYOUT_STACK_SIZE)ため、深さ1000は
		// 成功するようになった。反復化したのではなく、スタックを増やして
		// 実務上の問題を解消した形(相互再帰自体は残っている)。
		this.runDeepOpenChain(1000, 300);
	}

	/**
	 * 深さ5000(finishLayout/frames/draw/textShape/getTextの反復化後に
	 * 確認済みの深さと同水準)。同じく現状は{@code splitPageAxis}経由で
	 * {@code StackOverflowError}に到達する(クラスjavadoc参照)。
	 */
	public void testDepth5000OpenChainAcrossPageBreaks() throws Exception {
		// 同上。実測では深さ5000に必要なstackは8MBで、64MBは8倍の余裕がある
		this.runDeepOpenChain(5000, 300);
	}

	/**
	 * {@code splitPageAxis}の反復化(M6d後の大規模リファクタ)と比較検討
	 * する代替案の実証実験です(2026-07-23、
	 * `docs/history/2026-07-22-m6d-splitpageaxis-iteration
	 * -investigation.md`「代替案」節参照)。
	 *
	 * <p>
	 * 深さ1000・5000での{@code StackOverflowError}は典型的な「JVM
	 * デフォルトのスレッドstackサイズ不足」パターン(深さ500は成功、
	 * 1000で失敗——1段あたり数百バイト消費と見積もれば筋が通る)と
	 * 仮説を立てた。{@code splitPageAxis}のロジックには一切触れず、
	 * 大きいstackサイズ(64MB)を持つ専用スレッドで同じ計算を実行する
	 * だけで解消するかを直接検証する。
	 * </p>
	 *
	 * <p>
	 * この実証実験自体はテストコード側だけで大きいstackスレッドを
	 * 手動生成する(本番コードには一切手を入れない、生の仮説検証)。
	 * 検証後に実際に本番へ配線した統合({@code processing
	 * .large-stack-thread}プロパティ経由)は
	 * {@link #testDepth5000SucceedsWithLargeStackThreadProperty}参照。
	 * </p>
	 */
	public void testDepth5000SucceedsOnLargeStackThread() throws Throwable {
		final int depth = 5000;
		final int leafLines = 300;
		final long largeStackBytes = 64L * 1024 * 1024;
		final Throwable[] failure = new Throwable[1];
		final Thread worker = new Thread(null, () -> {
			try {
				this.runDeepOpenChain(depth, leafLines);
			} catch (Throwable t) {
				failure[0] = t;
			}
		}, "deep-nesting-large-stack", largeStackBytes);
		worker.start();
		worker.join();
		if (failure[0] != null) {
			throw failure[0];
		}
	}

	/**
	 * 上記の仮説検証を受けて実際に本番へ配線した統合(2026-07-23、
	 * `processing.large-stack-thread`セッションプロパティ、
	 * {@code DirectSession.runOnLargeStackIfEnabled}経由)を、
	 * テストコード側で手動スレッドを作らず、{@code DirectSession
	 * .transcode()}の通常の呼び出し経路だけで検証する。深さ5000は
	 * このプロパティを立てない既定設定では{@code StackOverflowError}
	 * になる({@link #testDepth5000OpenChainAcrossPageBreaksCurrentlyOverflows}
	 * 参照)——このプロパティを立てるだけで例外なく成功することを
	 * 固定する。
	 */
	public void testDepth5000SucceedsWithLargeStackThreadProperty() throws Exception {
		this.runDeepOpenChain(5000, 300, true);
	}

	private void runDeepOpenChain(int depth, int leafLines) throws Exception {
		this.runDeepOpenChain(depth, leafLines, false);
	}

	private void runDeepOpenChain(int depth, int leafLines, boolean largeStackThread) throws Exception {
		final String name = "deep-nesting-restyle-" + depth;
		final File doc = generateDeepOpenChainAcrossPageBreaks(name, depth, leafLines);
		final File pdf = new File("local/unittest/display-list/" + name + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				if (largeStackThread) {
					session.property("processing.large-stack-thread", "true");
				}
				CTISessionHelper.transcodeFile(session, doc, "text/html", null);
			} finally {
				session.close();
			}
		}
		assertTrue("PDFが出力されていません(深さ" + depth + ")", pdf.length() > 0);
	}

	/**
	 * depth段だけ{@code <div>}を入れ子にし、最深部にleafLines行分の
	 * 番号付きテキスト(1行8pt、ページ高さ400ptなので約50行/ページ)を
	 * 置いた文書を生成する。leafLinesを1ページ分(約50行)より十分大きく
	 * することで、ネストの祖先チェーン全体が開いたまま複数回改ページする。
	 */
	private static File generateDeepOpenChainAcrossPageBreaks(String name, int depth, int leafLines)
			throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"250pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			// DeepNestingLayoutTestと同様、borderやpaddingは付けない
			// (各段のサイズ計算を単純に保つ)。ただし今回は最深部の内容量を
			// 意図的にページ高さより大きくし、祖先チェーンを開いたまま
			// 改ページさせる。
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif}</style>\n");
			w.write("</head><body>\n");
			for (int i = 0; i < depth; ++i) {
				w.write("<div>");
			}
			for (int i = 0; i < leafLines; ++i) {
				w.write("LEAF-");
				w.write(String.format("%06d", i));
				w.write("<br/>\n");
			}
			for (int i = 0; i < depth; ++i) {
				w.write("</div>");
			}
			w.write("\n</body></html>\n");
		}
		return file;
	}
}
