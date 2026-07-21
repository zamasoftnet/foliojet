package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.layout.fragment.ContinuationStats;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * {@code RootBuilder.pageBreak()}の収集可能プレフィックス切り詰め
 * (M6b Phase B、2026-07-20)に対する回帰テストです。
 *
 * <p>
 * <b>実証された発火条件</b>: {@code FlowContainer.restyle}の
 * {@code OpenShape.OpenChain}分岐(まだ反復化されていない再帰)は、
 * {@code RootBuilder.pageBreak()}の事前検分(祖先チェーン
 * {@code flowStack[1..]}が全て段組なし・単一書字方向のplain
 * {@code FlowBlockBox}であること)が成立しない場合に発火する。
 * 表・書字方向混在をリーフに置いただけでは発火しない
 * ({@code testTableLeafNeverTriggersOpenChain}が固定する既存の安全な
 * 経路——{@code FlowContainer.splitPageAxis}のTABLE/TEXT_BLOCK分岐は
 * 元々chain-fragment機構を経由しない)。実際に発火するのは
 * <b>段組(column-count&gt;1)</b>を祖先チェーンの途中に挟んだ場合だけ
 * だった。
 * </p>
 *
 * <p>
 * <b>旧実装(2026-07-20以前)の問題</b>: 事前検分は祖先チェーン
 * <i>全体</i>に対する all-or-nothing のブール判定だった
 * (1レベルでも段組等で失敗すれば{@code plan=null}になり、
 * {@code flowStack}全体のサイズがそのまま{@code OpenChain}の深さに
 * なる)。このため段組を囲む外側のplainラッパーdivがどれだけ深く
 * ネストしていても(実文書では普通にありうる)、その深さがそのまま
 * 未反復の再帰へ流れ込んでいた(実測: 外側60段+内側10段+段組で
 * {@code MAX_OPEN_TAIL_DEPTH}=74に到達)。
 * </p>
 *
 * <p>
 * <b>修正</b>: 事前検分を「先頭から最初の違反レベルまでの収集可能な
 * プレフィックス」へ変更した({@code RootBuilder.pageBreak()}の
 * {@code BreakPlan}構築部)。{@code BreakPlan.depth}は
 * {@code flowStack.size()}のまま変更しない(ここを縮めると
 * {@code OpenShape}の入れ子数と実ボックス木の開き構造が食い違い、
 * まだ開いているボックスを誤って閉じる恐れがある——独立レビューで
 * 確認済み、{@code docs/consultations/consult-open-chain-prefix-*.md}
 * 参照)。{@code BreakPlan.openTailDepth() = depth - index - 1}は
 * {@code depth}を歩かずに得られる値のまま保たれるため、プレフィックスを
 * 切り詰めるだけで残存{@code OpenChain}深さが「違反箇所からその内側」
 * だけに自然に縮む。
 * </p>
 *
 * <p>
 * <b>残る制約</b>: この修正は「段組等の<i>外側</i>のplainラッパー深さ」
 * だけを再帰リスクから除外する。段組ボックス<i>自身の内側</i>が深く
 * ネストしていれば、同じ安全閾値
 * ({@code ContinuationStats.OPEN_CHAIN_DEPTH_ALARM_THRESHOLD}=64)に
 * 依然到達しうる。この場合、2026-07-20時点では本番・テストの区別なく
 * {@link net.zamasoft.foliojet.layout.fragment.ContinuationDepthLimitExceededException}
 * を投げて安全に停止する(ガード地点では新ページに何も書き込まれて
 * おらず、状態変異なしに中断できることを独立レビューで確認済み)。
 * {@code DirectSession.transcode}の既存{@code catch(Throwable)}が
 * {@code TranscoderException(STATE_BROKEN, FATAL_UNEXPECTED)}へ変換する。
 * </p>
 */
public class OpenChainCollectablePrefixTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final URI COPPER_URI = URI.create("copper:direct:");

	/**
	 * 対照実験: 表をリーフに置いた場合、外側のラッパーdivがどれだけ
	 * 深くても(ここでは40段)chain-fragment機構がそのまま処理し、
	 * {@code OpenChain}には一切落ちない。段組が本質的な発火条件である
	 * ことを裏付ける。
	 */
	public void testTableLeafNeverTriggersOpenChain() throws Exception {
		this.run("table-leaf-control", 40, this::writeTableLeaf, 300);
		assertEquals("表リーフはOpenChainを発火させないはずです", 0, ContinuationStats.RESTYLE_CHAIN_FIRINGS.get());
		assertEquals("表リーフの開きテイルは深さ1(開きテキストのみ)のはずです", 1,
				ContinuationStats.MAX_OPEN_TAIL_DEPTH.get());
		assertEquals(0, ContinuationStats.OPEN_CHAIN_DEPTH_ALARMS.get());
	}

	/**
	 * 段組を祖先チェーンの途中に挟んだ場合、外側のラッパーdivをかなり
	 * 深く(100段)しても、収集可能プレフィックス切り詰めにより
	 * {@code OpenChain}の実深さは段組周辺(内側10段)だけに留まり、
	 * 安全閾値(64)を大きく下回ったまま安定する——外側の深さに
	 * <i>比例して</i>大きくならないことがこのテストの主張。
	 */
	public void testDeepOuterWrapperAroundMulticolStaysShallow() throws Exception {
		this.run("outer-wrapper-around-multicol", 100, (w, leafLines) -> this.writeMulticolLeaf(w, 10, leafLines),
				300);
		final long depth = ContinuationStats.MAX_OPEN_TAIL_DEPTH.get();
		assertTrue("外側100段でも開きテイル深さは段組周辺だけに留まるはずです(実測=" + depth + ")", depth < 20);
		assertEquals("プレフィックス切り詰め後は安全閾値に達しないはずです", 0, ContinuationStats.OPEN_CHAIN_DEPTH_ALARMS.get());
	}

	/**
	 * 段組<i>自身の内側</i>を深くネストさせた場合(外側は5段のみと浅い)、
	 * プレフィックス切り詰めの対象外(違反箇所より内側)であるため、
	 * 依然として安全閾値(64)へ到達しうる。この場合、素の
	 * {@code StackOverflowError}ではなく、型付きの
	 * {@code ContinuationDepthLimitExceededException}経由の
	 * {@code TranscoderException}として安全に停止することを確認する。
	 */
	public void testDeepNestingInsideMulticolTripsDepthGuard() throws Exception {
		try {
			this.run("nesting-inside-multicol", 5, (w, leafLines) -> this.writeMulticolLeaf(w, 80, leafLines), 300);
			fail("段組内側の深いネストは安全閾値に到達し、TranscoderExceptionになるはずです");
		} catch (TranscoderException e) {
			assertTrue("安全閾値アラームが記録されているはずです", ContinuationStats.OPEN_CHAIN_DEPTH_ALARMS.get() > 0);
		}
	}

	private interface LeafWriter {
		void write(Writer w, int leafLines) throws IOException;
	}

	private void writeTableLeaf(Writer w, int leafLines) throws IOException {
		w.write("<table style=\"table-layout:fixed;width:100%\">\n");
		for (int i = 0; i < leafLines; ++i) {
			w.write("<tr><td>ROW-" + String.format("%06d", i) + "</td></tr>\n");
		}
		w.write("</table>\n");
	}

	private void writeMulticolLeaf(Writer w, int innerDepth, int leafLines) throws IOException {
		w.write("<div style=\"column-count:2;column-gap:1em\">\n");
		for (int i = 0; i < innerDepth; ++i) {
			w.write("<div>");
		}
		this.writeTextLeaf(w, leafLines);
		for (int i = 0; i < innerDepth; ++i) {
			w.write("</div>");
		}
		w.write("\n</div>\n");
	}

	private void writeTextLeaf(Writer w, int leafLines) throws IOException {
		for (int i = 0; i < leafLines; ++i) {
			w.write("LEAF-");
			w.write(String.format("%06d", i));
			w.write("<br/>\n");
		}
	}

	private void run(String name, int depth, LeafWriter leaf, int leafLines) throws Exception {
		ContinuationStats.reset();
		final File doc = this.generate(name, depth, leaf, leafLines);
		final File pdf = new File("local/unittest/display-list/open-chain-" + name + ".pdf");
		pdf.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(pdf)) {
			DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				CTISessionHelper.transcodeFile(session, doc, "text/html", null);
			} finally {
				session.close();
			}
		}
		System.err.println(name + ": RESTYLE_CHAIN_FIRINGS=" + ContinuationStats.RESTYLE_CHAIN_FIRINGS.get()
				+ " CHILD_FRAMES=" + ContinuationStats.CHILD_FRAMES.get() + " OPEN_TAILS="
				+ ContinuationStats.OPEN_TAILS.get() + " UNCHAINED_RESTYLES=" + ContinuationStats.UNCHAINED_RESTYLES.get()
				+ " MAX_OPEN_TAIL_DEPTH=" + ContinuationStats.MAX_OPEN_TAIL_DEPTH.get() + " OPEN_CHAIN_DEPTH_ALARMS="
				+ ContinuationStats.OPEN_CHAIN_DEPTH_ALARMS.get());
	}

	private File generate(String name, int depth, LeafWriter leaf, int leafLines) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, "open-chain-" + name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"250pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif}td{border:0.5pt solid black}</style>\n");
			w.write("</head><body>\n");
			for (int i = 0; i < depth; ++i) {
				w.write("<div>");
			}
			leaf.write(w, leafLines);
			for (int i = 0; i < depth; ++i) {
				w.write("</div>");
			}
			w.write("\n</body></html>\n");
		}
		return file;
	}
}
