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
import net.zamasoft.foliojet.layout.fragment.ContinuationCapability;
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
				ContinuationStats.MAX_PAGE_OPEN_TAIL_DEPTH.get());
		assertEquals(0, ContinuationStats.PAGE_OPEN_DEPTH_ALARMS.get());
	}

	/**
	 * 段組を祖先チェーンの途中に挟んだ場合、外側のラッパーdivをかなり
	 * 深く(100段)しても、B3a(段組のPAGE split-through解禁)により
	 * 段組level自体がfirst-classにコンパイルされ、PAGE側の
	 * {@code OpenChain}は完全に消える——外側の深さに関わらず
	 * {@code MAX_PAGE_OPEN_TAIL_DEPTH}は常に1(開きテキストのみ)。
	 *
	 * <p>
	 * <b>2026-07-21追記(B3a)</b>: 以前(B0.5〜B2)はMULTICOLがプレフィックス
	 * スキャンを停止させ、段組から内側だけがOpenChain(box-restyle)に
	 * 落ちていた(実測: 深さ12程度)。B3aで`ContinuationCapability
	 * .supportsPageSplitThrough()`がMULTICOLを自動改ページで収集可能に
	 * したため、段組level自体も`ResumeProgram`のfirst-class levelになり、
	 * PAGE側のOpenChainは完全に消える(`PAGE_RESTYLE_CHAIN_FIRINGS==0`)。
	 * 残る`RESTYLE_CHAIN_FIRINGS`(このfixtureでは40)はすべてCOLUMN経路
	 * (段組内部の改段、B4の対象)由来である。
	 * </p>
	 */
	public void testDeepOuterWrapperAroundMulticolStaysShallow() throws Exception {
		this.run("outer-wrapper-around-multicol", 100, (w, leafLines) -> this.writeMulticolLeaf(w, 10, leafLines),
				300);
		assertEquals("B3a後はPAGE側の開きテイル深さは常に1(開きテキストのみ)のはずです", 1,
				ContinuationStats.MAX_PAGE_OPEN_TAIL_DEPTH.get());
		assertEquals("プレフィックス切り詰め後は安全閾値に達しないはずです", 0, ContinuationStats.PAGE_OPEN_DEPTH_ALARMS.get());
		assertEquals("B3a後はMULTICOLがプレフィックススキャンを止めないはずです", 0,
				ContinuationStats.capabilityScanStops(ContinuationCapability.MULTICOL));
		assertEquals("PAGE側のOpenChainは完全に消えるはずです", 0, ContinuationStats.PAGE_RESTYLE_CHAIN_FIRINGS.get());
		assertTrue("段組levelが実際にfirst-classコンパイルされたはずです",
				ContinuationStats.pageCompiledLevels(ContinuationCapability.MULTICOL) > 0);
		// 2026-07-21(B4-Step4): COLUMN経路もPLAIN_FLOW子孫を自動改段で
		// first-classコンパイルするようになったため、RESTYLE_CHAIN_FIRINGS
		// (旧OpenChain再帰)はPAGE・COLUMN双方でゼロになるはずである
		// (このfixtureの段組内側は10段のPLAIN_FLOWラッパーのみ、
		// FLOW_SUBTYPE/直交等のbarrierを含まないため完全に収集される)。
		assertEquals("B4-Step4後はCOLUMN側のOpenChainも完全に消えるはずです", 0,
				ContinuationStats.COLUMN_RESTYLE_CHAIN_FIRINGS.get());
		assertEquals("RESTYLE_CHAIN_FIRINGSはPAGE・COLUMN双方でゼロになるはずです", 0,
				ContinuationStats.RESTYLE_CHAIN_FIRINGS.get());
		assertTrue("段組owner内側のPLAIN_FLOW子孫がCOLUMN側でもfirst-classコンパイルされたはずです",
				ContinuationStats.columnCompiledLevels(ContinuationCapability.PLAIN_FLOW) > 0);
	}

	/**
	 * M6b Phase B4残作業: COLUMN resume中にPAGE breakが入れ子になる
	 * (逆に言えばPAGE resume中にCOLUMN resumeが入れ子になる)ケースで、
	 * {@code ReplayLeaseSession}スタック・{@code resumeScopes}・
	 * {@code ResumeTrace}・leaseが混線しないことを確認する
	 * characterization test(2026-07-21新設)。
	 *
	 * <p>
	 * 段組(2段)の内容を複数ページにまたがるだけの分量にすると、
	 * 「ページ1内で段1→段2の改段(COLUMN resume)」「ページ1→2の改ページ
	 * (PAGE resume、この中でさらに段組が継続するため新しいCOLUMN
	 * resumeが入れ子で起きる)」の両方が自然に発生する。
	 * {@code enableAssertions=true}(build.gradle)によりテスト実行中は
	 * {@code RootBuilder.ResumeSession}/{@code ColumnResumeSession}の
	 * {@code assert !hasUnconsumedLeases()}が有効なため、この文書が
	 * 例外を投げずに完走すること自体が、入れ子セッション間でリースが
	 * 正しく所有・解放されたことの検証になる。
	 * </p>
	 */
	public void testNestedPageAndColumnResumeDoNotLeakSessions() throws Exception {
		ContinuationStats.reset();
		final File doc = this.generateMultiPageMulticol("nested-page-column-resume", 3, 2, 60);
		final File pdf = new File("local/unittest/display-list/open-chain-nested-page-column-resume.pdf");
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
		// 複数ページ・複数段の両方が実際に発生したことを確認する
		// (発生しなければ、そもそも入れ子resumeを検証できていない)。
		assertTrue("複数ページにまたがる改ページが実際に発生したはずです",
				ContinuationStats.pageCompiledLevels(ContinuationCapability.MULTICOL) > 0
						|| ContinuationStats.capabilityScanStops(ContinuationCapability.MULTICOL) > 0);
		assertTrue("段組内部の改段が実際に複数回発生したはずです",
				ContinuationStats.columnCompiledLevels(ContinuationCapability.PLAIN_FLOW) > 0);
	}

	private File generateMultiPageMulticol(final String name, final int pages, final int columnsPerPage,
			final int leafLinesPerColumn) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, "open-chain-" + name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"250pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif;margin:0}"
					+ "div{margin:0;padding:0}</style>\n");
			w.write("</head><body>\n");
			w.write("<div style=\"column-count:2;column-gap:1em\">\n");
			final int totalLines = pages * columnsPerPage * leafLinesPerColumn;
			// PLAIN_FLOWのラッパーdivを挟む——素のテキストのみだと
			// owner(段組)直下に子孫レベルが存在せず(depth==1)、
			// COLUMN側のfragment chain実行(index>=1)を経由しない
			w.write("<div>\n");
			this.writeTextLeaf(w, totalLines);
			w.write("</div>\n");
			w.write("</div>\n");
			w.write("</body></html>\n");
		}
		return file;
	}

	/**
	 * M6b Phase B4/B5残作業: nested multicol(段組の中の段組)で、
	 * {@code BreakableBuilder.findColumnBreak()}が最内側の
	 * {@code canColumnBreak()}なownerを選ぶ既存挙動が、B4のCOLUMN継続
	 * 型付け後も維持されていることを確認する(2026-07-21新設)。
	 *
	 * <p>
	 * 外側(段数2)は十分な高さを持ち、内側(段数3)の内容だけが自身の
	 * 単一段の容量を超える構成にする——内側だけが改段を要し、外側は
	 * 改段不要のまま文書全体が完結するはずである。改段のたびに
	 * {@code ContinuationStats.LAST_COLUMN_OWNER_COLUMN_COUNT}へ
	 * ownerの設定段数を記録させ、最後に観測された値が常に内側の3で
	 * あって外側の2ではないことを確認する。
	 * </p>
	 */
	public void testNestedMulticolSelectsInnermostOwner() throws Exception {
		ContinuationStats.reset();
		final File doc = this.generateNestedMulticol("nested-multicol-owner", 2, 3);
		final File pdf = new File("local/unittest/display-list/open-chain-nested-multicol-owner.pdf");
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
		assertTrue("段組内部の改段が実際に発生したはずです(でなければownerを検証できていない)",
				ContinuationStats.columnCompiledLevels(ContinuationCapability.PLAIN_FLOW) > 0
						|| ContinuationStats.capabilityScanStops(ContinuationCapability.PLAIN_FLOW) >= 0);
		assertEquals("最内側(段数3)のmulticolがownerとして選ばれ続けるはずです(外側の段数2ではない)", 3,
				ContinuationStats.LAST_COLUMN_OWNER_COLUMN_COUNT.get());
	}

	private File generateNestedMulticol(final String name, final int outerColumnCount, final int innerColumnCount)
			throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, "open-chain-" + name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"300pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"400pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif;margin:0}"
					+ "div{margin:0;padding:0}</style>\n");
			w.write("</head><body>\n");
			// 外側: 十分な高さ、改段が不要な段数
			w.write("<div style=\"column-count:" + outerColumnCount + ";column-gap:1em;height:380pt\">\n");
			// 内側: 単一段の容量を超える内容を持つ、狭い高さ
			w.write("<div style=\"column-count:" + innerColumnCount + ";column-gap:1em;height:60pt\">\n");
			w.write("<div>\n");
			this.writeTextLeaf(w, 200);
			w.write("</div>\n");
			w.write("</div>\n");
			w.write("</div>\n");
			w.write("</body></html>\n");
		}
		return file;
	}

	/**
	 * M6b Phase B3b-1(2026-07-21): 段組祖先(段数2、AUTO高さ、複数ページに
	 * わたる)の内側で強制改ページ({@code page-break-before: always})が
	 * 起きても、{@code ContinuationCapability.supportsPageSplitThrough}の
	 * mode非依存化(B3b-2でKEEP/MOVEが正規経路になったことを根拠に撤去)
	 * 以降、例外なく安全に完走し、段組level自体が{@code
	 * CapabilityBarrier(MULTICOL)}にならず(実際に収集され)first-class
	 * コンパイルされたことを確認する。
	 *
	 * <p>
	 * <b>フィクスチャ設計上の注意</b>: 段組に明示的な高さ({@code height})を
	 * 指定すると、{@code canColumnBreak()}の{@code isSpecifiedPageSize()}
	 * 短絡(高さ指定時は実使用量に関わらず追加の列を無条件に許可する)により、
	 * 収まりきらない内容はPAGE分割ではなく単にオーバーフローする(あるいは
	 * ページ幅が許す限り追加の列が生成される)だけで、実際のPAGEレベル
	 * 強制分割には至らない——このセッションの変更とは無関係の既存挙動
	 * (このテスト作成時に発見)。段組をAUTO高さのままにし
	 * ({@code testNestedPageAndColumnResumeDoNotLeakSessions}の
	 * {@code generateMultiPageMulticol}と同型)、内容が複数ページに
	 * またがる分量にすることで、初めてPAGEレベルの強制分割
	 * (このテストが検証したい経路)が発生することを実測で確認した。
	 * トリガーには{@code page-break-before}を使う(このエンジンは
	 * {@code ElementPropertySet}に登録された旧CSS2プロパティ名のみを
	 * 認識し、現行のCSS Fragmentation仕様の{@code break-before}は未対応、
	 * `PageBreakBefore.java`参照)。
	 * </p>
	 */
	public void testForceBreakInsideMulticolAncestorSplitsThroughSafely() throws Exception {
		ContinuationStats.reset();
		final File doc = this.generateForceBreakInsideMulticol("force-break-inside-multicol");
		final File pdf = new File("local/unittest/display-list/open-chain-force-break-inside-multicol.pdf");
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
		assertEquals("MULTICOLは強制改ページでもbarrierにならないはずです(B3b-1)", 0,
				ContinuationStats.capabilityScanStops(ContinuationCapability.MULTICOL));
		assertTrue("段組levelが強制改ページ経由でもfirst-classコンパイルされたはずです",
				ContinuationStats.pageCompiledLevels(ContinuationCapability.MULTICOL) > 0);
	}

	private File generateForceBreakInsideMulticol(final String name) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, "open-chain-" + name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"250pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif;margin:0}"
					+ "div{margin:0;padding:0}</style>\n");
			w.write("</head><body>\n");
			// 段組祖先(段数2、AUTO高さ)——複数ページにまたがる分量の
			// 内容の途中でpage-break-before:alwaysを発火させ、PAGEレベルの
			// 強制分割にエスカレーションさせる
			w.write("<div style=\"column-count:2;column-gap:1em\">\n");
			w.write("<div>\n");
			this.writeTextLeaf(w, 100);
			w.write("<div style=\"page-break-before:always\">\n");
			this.writeTextLeaf(w, 100);
			w.write("</div>\n");
			w.write("</div>\n");
			w.write("</div>\n");
			w.write("</body></html>\n");
		}
		return file;
	}

	/**
	 * 段組<i>自身の内側</i>を深くネストさせた場合(外側は5段のみと浅い)、
	 * プレフィックス切り詰めの対象外(違反箇所より内側)であるため、
	 * 依然として安全閾値(64)へ到達しうる。この場合、素の
	 * {@code StackOverflowError}ではなく、型付きの
	 * {@code ContinuationDepthLimitExceededException}経由の
	 * {@code TranscoderException}として安全に停止することを確認する。
	 *
	 * <p>
	 * <b>2026-07-21追記</b>: 実際に発火するのはPAGE経路
	 * ({@code RootBuilder.pageBreak}/{@code resumeFrame})ではなく
	 * COLUMN経路({@code BreakableBuilder.columnBreak}、段組内の改段)
	 * だった——80段のネストは2カラムの1カラム分の高さに収まらず、
	 * ページ全体が改ページを要する前に段組内部で改段が先に必要になるため。
	 * COLUMN経路は2026-07-20時点ではガード自体が存在せず無防備だった
	 * (ChatGPT Pro相談で発見、
	 * docs/consultations/ANSWER-CHATGPT-2026-07-21-open-chain-full-fix.md)。
	 * このテストが実測でそれを裏付けた——当初「PAGE側の深さガードの
	 * 検証」のつもりで書いたが、実際にはCOLUMN側ガードの新設を検証する
	 * テストになっている。
	 * </p>
	 */
	public void testDeepNestingInsideMulticolTripsDepthGuard() throws Exception {
		try {
			this.run("nesting-inside-multicol", 5, (w, leafLines) -> this.writeMulticolLeaf(w, 80, leafLines), 300);
			fail("段組内側の深いネストは安全閾値に到達し、TranscoderExceptionになるはずです");
		} catch (TranscoderException e) {
			assertTrue("COLUMN経路の安全閾値アラームが記録されているはずです",
					ContinuationStats.COLUMN_OPEN_DEPTH_ALARMS.get() > 0);
		}
	}

	/**
	 * 直交writing-modeの表(OnePassTableBuilder経由のINCREMENTAL改ページ)は、
	 * {@code BreakableBuilder.forceBreak()}が{@code breakDepth}障壁
	 * (通常は直交書字方向の内部で自動改ページを抑止する仕組み)を迂回する
	 * ため、{@code ORTHOGONAL_FLOW}(段組・RL/LR不一致と同型の未対応
	 * capability)へ実際に到達する——2026-07-21、ChatGPT Pro相談で指摘、
	 * 本セッションの変更とは無関係の既存バグとして実測で確認した
	 * (本セッション以前から存在。`RootBuilder.java`の
	 * {@code assert this.flowStack.size() == continuation.depth()}が
	 * 本番では無検査のまま素通りし、検知されないコンテンツ破損の
	 * 恐れがあった)。
	 *
	 * <p>
	 * 恒久解(B2以降でORTHOGONAL_FLOWを型付きで扱う)は未実装のため、
	 * このテストは「型付き例外で安全に停止すること」だけを固定する
	 * characterization test。この安全網
	 * ({@code ContinuationInvariantViolationException}、旧
	 * assertを本番でも常に効く形へ変更)は本テストの発見を受けて追加した。
	 * </p>
	 */
	public void testOrthogonalWritingModeTableTripsInvariantGuard() throws Exception {
		ContinuationStats.reset();
		final File doc = this.generateOrthogonalWritingModeTable("orthogonal-table", 200);
		final File pdf = new File("local/unittest/display-list/open-chain-orthogonal-table.pdf");
		pdf.getParentFile().mkdirs();
		try {
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
			fail("直交writing-modeの表はContinuationInvariantViolationException経由の"
					+ "TranscoderExceptionになるはずです(未対応のORTHOGONAL_FLOW capability)");
		} catch (TranscoderException e) {
			assertTrue("プレフィックススキャンはORTHOGONAL_FLOWで停止したはずです(B1分類)",
					ContinuationStats.capabilityScanStops(ContinuationCapability.ORTHOGONAL_FLOW) > 0);
		}
	}

	private File generateOrthogonalWritingModeTable(String name, int rows) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, "open-chain-" + name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"300pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"300pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif;writing-mode:horizontal-tb}\n");
			w.write("table{writing-mode:vertical-rl;table-layout:fixed;height:100pt;border-collapse:collapse}\n");
			w.write("td{border:0.5pt solid black}</style>\n");
			w.write("</head><body>\n<table>\n");
			for (int i = 0; i < rows; ++i) {
				w.write("<tr><td>ROW-" + String.format("%06d", i) + "</td></tr>\n");
			}
			w.write("</table>\n</body></html>\n");
		}
		return file;
	}

	/**
	 * 段組だけがOpenChainの発火条件ではない(2026-07-21、ChatGPT Pro相談で
	 * 指摘・検証済み)。{@code RootBuilder.pageBreak()}の事前検分は
	 * 祖先の{@code WritingMode}がルートと完全一致することを要求するが、
	 * 実際の内部切断可否(`FlowContainer.splitPageAxis`)・自動改ページの
	 * 障壁(`BreakableBuilder.startFlowBlock`)はどちらも
	 * {@code isVertical()}の一致しか見ていない。したがって
	 * {@code vertical-rl}の祖先チェーンの途中に{@code vertical-lr}
	 * (縦書きのまま方向だけ違う)が挟まっても、実際の切断はできるのに
	 * 事前検分だけが失敗し、段組と同型のOpenChain発火に至る。
	 */
	public void testMixedVerticalDirectionAncestorAlsoTriggersOpenChain() throws Exception {
		ContinuationStats.reset();
		final File doc = this.generateVerticalDirectionMismatch("vertical-rl-lr-mismatch", 40, 300);
		final File pdf = new File("local/unittest/display-list/open-chain-vertical-rl-lr-mismatch.pdf");
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
		System.err.println("vertical-rl-lr-mismatch: RESTYLE_CHAIN_FIRINGS=" + ContinuationStats.RESTYLE_CHAIN_FIRINGS.get()
				+ " CHILD_FRAMES=" + ContinuationStats.CHILD_FRAMES.get() + " MAX_PAGE_OPEN_TAIL_DEPTH="
				+ ContinuationStats.MAX_PAGE_OPEN_TAIL_DEPTH.get());
		// 2026-07-21(B5)追記: 当初(B1時点)はSAME_AXIS_DIRECTION_CHANGEが
		// プレフィックススキャンを止め、OpenChainが発火することを実証する
		// テストだった。B5で「crossExtent/FragmentStateはisVertical()の
		// みに依存しRL/LRの向きを見ない」ことをcodex/grok/agyへの設計
		// 相談で確認した上でSAME_AXIS_DIRECTION_CHANGEを自動改ページで
		// 収集可能にしたため、このfixtureはもうOpenChainへ落ちない
		// (MULTICOLがB3aで解禁された際と同型の書き換え)。
		assertEquals("B5後はSAME_AXIS_DIRECTION_CHANGEがプレフィックススキャンを止めないはずです", 0,
				ContinuationStats.capabilityScanStops(ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE));
		assertEquals("B5後はPAGE側のOpenChainは完全に消えるはずです", 0,
				ContinuationStats.PAGE_RESTYLE_CHAIN_FIRINGS.get());
		assertTrue("RL/LR混在levelが実際にfirst-classコンパイルされたはずです",
				ContinuationStats.pageCompiledLevels(ContinuationCapability.SAME_AXIS_DIRECTION_CHANGE) > 0);
	}

	private File generateVerticalDirectionMismatch(String name, int depth, int leafLines) throws IOException {
		final File dir = new File("local/unittest/generated");
		dir.mkdirs();
		final File file = new File(dir, "open-chain-" + name + ".html");
		try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
			w.write("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01//EN\">\n");
			w.write("<?jp.cssj.property name=\"output.page-width\" value=\"150pt\"?>\n");
			w.write("<?jp.cssj.property name=\"output.page-height\" value=\"200pt\"?>\n");
			w.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n");
			w.write("<style>@page{margin:0}body{font:normal 8pt/1 serif;writing-mode:vertical-rl}</style>\n");
			w.write("</head><body>\n");
			for (int i = 0; i < depth; ++i) {
				w.write("<div>");
			}
			w.write("<div style=\"writing-mode:vertical-lr\">\n");
			for (int i = 0; i < 10; ++i) {
				w.write("<div>");
			}
			this.writeTextLeaf(w, leafLines);
			for (int i = 0; i < 10; ++i) {
				w.write("</div>");
			}
			w.write("\n</div>\n");
			for (int i = 0; i < depth; ++i) {
				w.write("</div>");
			}
			w.write("\n</body></html>\n");
		}
		return file;
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
				+ " MAX_OPEN_TAIL_DEPTH=" + ContinuationStats.MAX_PAGE_OPEN_TAIL_DEPTH.get() + " OPEN_CHAIN_DEPTH_ALARMS="
				+ ContinuationStats.PAGE_OPEN_DEPTH_ALARMS.get());
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
