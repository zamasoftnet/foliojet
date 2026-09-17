package jp.cssj.test.unit.displaylist;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

/**
 * 掃過オラクルの<b>除外述語</b>を固定します(2026-07-28新設)。
 *
 * <p>
 * 除外述語は<b>仕様の境界そのもの</b>です——ここで{@code true}になった
 * 文書は、以後どんな壊れ方をしても「組版を指定した側の責任」として
 * 数えられます。広すぎれば<b>本物の欠陥が黙って除外に紛れ込み</b>、
 * 3,000万文書の掃過は「失敗ゼロ」と嘘をつきます。
 * {@code ARCHITECTURE.md} §5.13 が「述語は機械的に計算できる形にすること」
 * と定めているのは、人が1件ずつ見て気づけないからです。
 * </p>
 *
 * <p>
 * したがってこのテストは「除外されること」だけでなく、
 * <b>除外されないこと</b>を同じ重さで固定します。
 * </p>
 */
public class FuzzOraclePredicateTest extends TestCase {

	public FuzzOraclePredicateTest(String name) {
		super(name);
	}

	private static String doc(final String bodyStyle, final String body) {
		return "<html><head><style>body{margin:0;font:normal 6pt/1.2 serif" + bodyStyle + "}</style></head><body>"
				+ body + "</body></html>";
	}

	// ------------------------------------------------------------------
	// hasOrthogonalFlow: 軸が2種類あるか
	// ------------------------------------------------------------------

	/** seed 194970 の形。縦書きの中に横書きがある。 */
	public void testVerticalBodyWithHorizontalChildIsOrthogonal() {
		assertTrue(RandomDocumentFuzzTest.hasOrthogonalFlow(
				doc(";writing-mode:vertical-rl", "<div style=\"writing-mode:horizontal-tb\">T0</div>")));
	}

	/** 既定(宣言なし)は横書きなので、縦書きの子があれば直交。 */
	public void testDefaultHorizontalBodyWithVerticalChildIsOrthogonal() {
		assertTrue(RandomDocumentFuzzTest
				.hasOrthogonalFlow(doc("", "<div style=\"writing-mode:vertical-rl\">T0</div>")));
	}

	/** 全部縦書きなら直交しない。 */
	public void testAllVerticalIsNotOrthogonal() {
		assertFalse(RandomDocumentFuzzTest.hasOrthogonalFlow(
				doc(";writing-mode:vertical-rl", "<div style=\"writing-mode:vertical-rl\">T0</div>")));
	}

	/**
	 * <b>同じ軸の方向違いは直交ではない</b>。{@code vertical-rl}の中の
	 * {@code vertical-lr}は{@code SAME_AXIS_DIRECTION_CHANGE}であり、
	 * 行軸の長さは変わらないので、はみ出しの言い訳にはならない。
	 */
	public void testSameAxisDirectionChangeIsNotOrthogonal() {
		assertFalse(RandomDocumentFuzzTest.hasOrthogonalFlow(
				doc(";writing-mode:vertical-rl", "<div style=\"writing-mode:vertical-lr\">T0</div>")));
	}

	/** {@code writing-mode}が1つも無い文書は直交しない。 */
	public void testNoWritingModeIsNotOrthogonal() {
		assertFalse(RandomDocumentFuzzTest.hasOrthogonalFlow(doc("", "<div>T0</div>")));
	}

	// ------------------------------------------------------------------
	// pageAxisIsY: 紙面のページ軸
	// ------------------------------------------------------------------

	public void testHorizontalBodyPaginatesAlongY() {
		assertTrue(RandomDocumentFuzzTest.pageAxisIsY(doc("", "<div>T0</div>")));
		assertTrue(RandomDocumentFuzzTest.pageAxisIsY(doc(";writing-mode:horizontal-tb", "<div>T0</div>")));
	}

	public void testVerticalBodyPaginatesAlongX() {
		assertFalse(RandomDocumentFuzzTest.pageAxisIsY(doc(";writing-mode:vertical-rl", "<div>T0</div>")));
		assertFalse(RandomDocumentFuzzTest.pageAxisIsY(doc(";writing-mode:vertical-lr", "<div>T0</div>")));
	}

	// ------------------------------------------------------------------
	// 除外の条件式(オラクル本体と同じ式を使う)
	// ------------------------------------------------------------------

	/** {@code assertNoUnexplainedOffPage}の判定式をそのまま写したもの。 */
	private static boolean excluded(final String html, final boolean worstIsY) {
		return worstIsY != RandomDocumentFuzzTest.pageAxisIsY(html) && RandomDocumentFuzzTest.hasOrthogonalFlow(html);
	}

	/**
	 * <b>これが本題</b>: 縦書き文書で<b>行軸(y)</b>へ溢れたら除外だが、
	 * <b>ページ軸(x)</b>へ溢れたら<b>除外しない</b>。
	 *
	 * <p>
	 * ページ軸のはみ出しは改ページで直せるので、直らなければエンジンの
	 * 欠陥である。この区別を落とすと、直交フローを含む文書のはみ出しを
	 * 何でも見逃すことになる。
	 * </p>
	 */
	public void testOnlyLineAxisOverflowIsExcludedInVerticalDocument() {
		final String html = doc(";writing-mode:vertical-rl", "<div style=\"writing-mode:horizontal-tb\">T0</div>");
		assertTrue("縦書き文書のy方向は行軸——除外されるべき", excluded(html, true));
		assertFalse("縦書き文書のx方向はページ軸——除外してはいけない", excluded(html, false));
	}

	/** 横書き文書では軸が入れ替わる(x=行軸、y=ページ軸)。 */
	public void testOnlyLineAxisOverflowIsExcludedInHorizontalDocument() {
		final String html = doc("", "<div style=\"writing-mode:vertical-rl\">T0</div>");
		assertTrue("横書き文書のx方向は行軸——除外されるべき", excluded(html, false));
		assertFalse("横書き文書のy方向はページ軸——除外してはいけない", excluded(html, true));
	}

	/**
	 * <b>直交フローが無ければ、どちらの軸でも除外しない。</b>
	 * 除外の口実に使えるのは直交フローを含む文書だけである。
	 */
	public void testWithoutOrthogonalFlowNothingIsExcluded() {
		final String html = doc(";writing-mode:vertical-rl", "<div>T0</div>");
		assertFalse(excluded(html, true));
		assertFalse(excluded(html, false));
	}

	// ------------------------------------------------------------------
	// hasUntypesettableOppositeProgression: 同軸逆進行が組版不能幅の場合だけ除外
	// ------------------------------------------------------------------

	/** seed 36607の最小形。Chromeも紙面右端から外向きに配置する。 */
	public void testZeroWidthOppositeVerticalProgressionIsExcluded() {
		final String html = doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:vertical-lr;width:0pt\">"
						+ "<div style=\"writing-mode:vertical-rl;width:24pt\">T7</div></div>");
		assertTrue(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(html));
	}

	/** seed 36607の元形。反転した48ptの箱へ幅86ptの子を置いている。 */
	public void testPositiveWidthOppositeProgressionWithWiderChildIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:vertical-lr;width:48pt\"><div style=\"width:86pt\">T0</div></div>")));
	}

	/** seed 82162の最小形。10pt文字に幅1ptでは1文字も組めない。 */
	public void testTooNarrowOppositeVerticalProgressionIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(
				doc(";writing-mode:vertical-rl", "<div style=\"writing-mode:vertical-lr;width:1pt\">T0</div>")));
	}

	/** 同軸反転だけでは除外しない。子孫が幅内なら通常の版面として検査する。 */
	public void testPositiveWidthOppositeVerticalProgressionIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:vertical-lr;width:80pt\">T0</div>")));
	}

	/** 幅0でも進行方向が同じなら除外しない。 */
	public void testZeroWidthSameVerticalProgressionIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:vertical-rl;width:0pt\">T0</div>")));
	}

	/** 縦書きのページ軸は幅なので、別軸のheight:0では除外しない。 */
	public void testZeroHeightOppositeVerticalProgressionIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:vertical-lr;height:0pt\">T0</div>")));
	}

	/** 直交フローは既存の別述語で扱い、幅0の同軸反転へ混ぜない。 */
	public void testZeroWidthOrthogonalFlowIsNotOppositeProgression() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:horizontal-tb;width:0pt\">T0</div>")));
	}

	/** 別々の枝にある宣言を誤って一つの除外条件に結び付けない。 */
	public void testZeroWidthAndOppositeProgressionInSeparateBranchesAreNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(doc(";writing-mode:vertical-rl",
				"<div style=\"width:0pt\">T0</div><div style=\"writing-mode:vertical-lr;width:80pt\">T1</div>")));
	}

	// ------------------------------------------------------------------
	// hasUntypesettableOrthogonalFlow: 軸変更と狭幅が同じ要素にある場合だけ除外
	// ------------------------------------------------------------------

	/** seed 372387の最小形。縦書き内の幅0横書きは1文字も組めない。 */
	public void testZeroWidthOrthogonalFlowIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasUntypesettableOrthogonalFlow(doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:horizontal-tb;width:0pt\">T0</div>")));
	}

	/** seed 266476等の最小形。10pt文字に幅4ptの直交フローも組版不能。 */
	public void testTooNarrowOrthogonalFlowIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasUntypesettableOrthogonalFlow(
				doc("", "<div style=\"writing-mode:vertical-rl;width:4pt\">T0</div>")));
	}

	/** 組版下限以上の直交フローは通常どおり検査する。 */
	public void testUsableWidthOrthogonalFlowIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOrthogonalFlow(
				doc("", "<div style=\"writing-mode:vertical-rl;width:80pt\">T0</div>")));
	}

	/** 同軸方向変更は専用の別述語へ任せる。 */
	public void testNarrowSameAxisFlowIsNotOrthogonalExclusion() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOrthogonalFlow(doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:vertical-lr;width:0pt\">T0</div>")));
	}

	/** 狭幅と直交指定が別枝なら結び付けない。 */
	public void testNarrowWidthAndOrthogonalFlowInSeparateBranchesAreNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOrthogonalFlow(doc("",
				"<div style=\"width:0pt\">T0</div><div style=\"writing-mode:vertical-rl;width:80pt\">T1</div>")));
	}

	/** 別物理軸のheightだけでは幅の組版不能とみなさない。 */
	public void testZeroHeightOrthogonalFlowIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasUntypesettableOrthogonalFlow(
				doc("", "<div style=\"writing-mode:vertical-rl;height:0pt\">T0</div>")));
	}

	// ------------------------------------------------------------------
	// hasOverwideFloat: 実際の包含幅より広い左右フロートだけを除外
	// ------------------------------------------------------------------

	/** seed 132786の最小形。99ptの親に126ptの右フロートを置いている。 */
	public void testFloatWiderThanExplicitParentIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"writing-mode:horizontal-tb;width:99pt\"><div style=\"float:right;width:126pt\">T0</div></div>")));
	}

	/** seed 143513の最小形。110pt内容幅の3段は段間を引くと約25.3pt。 */
	public void testFloatWiderThanComputedColumnIsExcluded() {
		final String html = "<?jp.cssj.property name=\"output.page-width\" value=\"120pt\"?>"
				+ "<html><head><style>@page{margin:5pt}body{font:normal 10pt/1.2 serif}</style></head><body>"
				+ "<div style=\"column-count:3;column-gap:17pt\"><div style=\"float:right;width:89pt\">T0</div></div>"
				+ "</body></html>";
		assertTrue(RandomDocumentFuzzTest.hasOverwideFloat(html));
	}

	/** seed 865035。自動幅floatの子孫が包含幅より広い。 */
	public void testAutoWidthFloatWithOverwideDescendantIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"width:48pt\"><div style=\"float:right\"><div style=\"width:55pt\">T0</div></div></div>")));
	}

	/** 同じ幅関係でもfloat祖先が無ければ専用除外にしない。 */
	public void testOverwideDescendantWithoutFloatIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(
				shrinkerDoc("<div style=\"width:48pt\"><div><div style=\"width:55pt\">T0</div></div></div>")));
	}

	/** 自動幅floatの子孫が包含幅内なら除外しない。 */
	public void testAutoWidthFloatWithFittingDescendantIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"width:55pt\"><div style=\"float:right\"><div style=\"width:55pt\">T0</div></div></div>")));
	}

	/** 親幅と同じフロートは通常の版面なので除外しない。 */
	public void testFloatFittingExplicitParentIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(
				shrinkerDoc("<div style=\"width:30pt\"><div style=\"float:right;width:30pt\">T0</div></div>")));
	}

	/** 別々の枝の幅を誤って親子として結び付けない。 */
	public void testWideFloatAndNarrowBoxInSeparateBranchesAreNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"width:20pt\">T0</div><div style=\"float:right;width:30pt\">T1</div>")));
	}

	/** {@code float:none}は幅が親より広くても専用除外にしない。 */
	public void testNonFloatingWideBoxIsNotExcludedAsOverwideFloat() {
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(shrinkerDoc(
				"<div style=\"width:20pt\"><div style=\"float:none;width:30pt\">T0</div></div>")));
	}

	/** 計算した段幅に収まるフロートは除外しない。 */
	public void testFloatFittingComputedColumnIsNotExcluded() {
		final String html = "<?jp.cssj.property name=\"output.page-width\" value=\"120pt\"?>"
				+ "<html><head><style>@page{margin:5pt}body{font:normal 10pt/1.2 serif}</style></head><body>"
				+ "<div style=\"column-count:3;column-gap:17pt\"><div style=\"float:right;width:25pt\">T0</div></div>"
				+ "</body></html>";
		assertFalse(RandomDocumentFuzzTest.hasOverwideFloat(html));
	}

	/** seed 78906の最小形。幅0の祖先内に無幅指定のfloatが入る。 */
	public void testFloatInsideNarrowContainerIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasFloatInsideNarrowContainer(
				shrinkerDoc("<div style=\"width:0pt\"><div><div style=\"float:left\">T0</div></div></div>"),
				48));
	}

	/** 狭い箱とfloatが別の枝なら除外しない。 */
	public void testNarrowContainerAndFloatInSeparateBranchesAreNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasFloatInsideNarrowContainer(
				shrinkerDoc("<div style=\"width:0pt\">T0</div><div style=\"float:left\">T1</div>"), 48));
	}

	/** 組版下限以上の祖先に入ったfloatは除外しない。 */
	public void testFloatInsideUsableContainerIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasFloatInsideNarrowContainer(
				shrinkerDoc("<div style=\"width:48pt\"><div style=\"float:left\">T0</div></div>"), 48));
	}

	/** 狭い祖先内でもfloat:noneは除外しない。 */
	public void testFloatNoneInsideNarrowContainerIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasFloatInsideNarrowContainer(
				shrinkerDoc("<div style=\"width:0pt\"><div style=\"float:none\">T0</div></div>"), 48));
	}

	/** 詳細ダンプを通った場合も、失敗ではなく専用の除外種別として数える。 */
	public void testDetailedDumpClassifiesZeroWidthOppositeProgression() throws Exception {
		final String html = doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:vertical-lr;width:0pt\"><div>T0</div></div>");
		final RandomDocumentFuzzTest.Generated generated = new RandomDocumentFuzzTest.Generated(html, List.of("T0"),
				Set.of(), 595, 842, 0, false, false);
		final File dump = writeDump("  x=595.80 y=0.50 Text[\"T0\" asc=3.00 desc=2.00] w=4.00 h=5.00\n");
		try {
			try {
				RandomDocumentFuzzTest.assertSomeDrawingOnPage(generated, new File[] { dump }, dump);
				fail("幅0の同軸逆進行フローは専用除外になるべき");
			} catch (final AssertionError e) {
				assertEquals("(除外)同軸逆進行フローの組版不能幅", RandomDocumentFuzzTest.classify(e));
			}
		} finally {
			dump.delete();
		}
	}

	/** 全描画紙面外の経路でも、直交フローの組版不能幅を専用除外にする。 */
	public void testDetailedDumpClassifiesUntypesettableOrthogonalFlow() throws Exception {
		final String html = doc(";writing-mode:vertical-rl",
				"<div style=\"writing-mode:horizontal-tb;width:27pt\"><ol><li>T0</li></ol></div>");
		final RandomDocumentFuzzTest.Generated generated = new RandomDocumentFuzzTest.Generated(html, List.of("T0"),
				Set.of(), 300, 150, 27, false, false);
		final File dump = writeDump("  x=303.00 y=6.72 Text[\"T0\" asc=5.15 desc=2.05] w=6.67 h=7.20\n");
		try {
			try {
				RandomDocumentFuzzTest.assertSomeDrawingOnPage(generated, new File[] { dump }, dump);
				fail("組版不能幅の直交フローは専用除外になるべき");
			} catch (final AssertionError e) {
				assertEquals("(除外)直交フローの組版不能幅", RandomDocumentFuzzTest.classify(e));
			}
		} finally {
			dump.delete();
		}
	}

	// ------------------------------------------------------------------
	// rectangleIntersectsPage: 全描画が紙面外かの矩形判定
	// ------------------------------------------------------------------

	public void testRectangleWhollyInsidePageIntersects() {
		assertTrue(RandomDocumentFuzzTest.rectangleIntersectsPage(10, 10, 5, 5, 60, 60));
	}

	/** 原点が外でも字形が紙面へかかれば、見えている描画として扱う。 */
	public void testOutsideOriginWithInkInsideIntersects() {
		assertTrue(RandomDocumentFuzzTest.rectangleIntersectsPage(-1, 10, 2, 5, 60, 60));
	}

	/** 紙面の端に接するだけで面積が無ければ、見えているとは数えない。 */
	public void testRectangleTouchingEdgeDoesNotIntersect() {
		assertFalse(RandomDocumentFuzzTest.rectangleIntersectsPage(60, 10, 2, 5, 60, 60));
		assertFalse(RandomDocumentFuzzTest.rectangleIntersectsPage(-2, 10, 2, 5, 60, 60));
	}

	/** 原点が遠くても外接矩形が紙面へ掛かるなら、紙面外配置とは数えない。 */
	public void testWideDrawingReachingPageIsNotBeyondWholePage() {
		assertTrue(RandomDocumentFuzzTest.distanceBeyondWholePage(-70, 124, 60) <= 0);
	}

	/** 矩形の近い辺まで紙面1枚以上離れていれば、紙面外配置として数える。 */
	public void testDetachedDrawingIsBeyondWholePage() {
		assertEquals(10.0, RandomDocumentFuzzTest.distanceBeyondWholePage(-80, 10, 60));
		assertEquals(10.0, RandomDocumentFuzzTest.distanceBeyondWholePage(130, 10, 60));
	}

	/** UA既定20exになる幅指定なしの入力欄だけを許容量へ含める。 */
	public void testDefaultTextControlsContributeIntrinsicWidth() {
		assertEquals(124.0, RandomDocumentFuzzTest.defaultTextControlWidth("<input />"));
		assertEquals(124.0, RandomDocumentFuzzTest.defaultTextControlWidth("<textarea></textarea>"));
	}

	/** 小型controlは数えない。size指定済みinputへは既定20exではなく、指定したsizeの実寸を使う。 */
	public void testNonDefaultTextControlsDoNotContributeIntrinsicWidth() {
		assertEquals(0.0, RandomDocumentFuzzTest.defaultTextControlWidth("<input type=\"radio\" />"));
		assertEquals(40.0, RandomDocumentFuzzTest.defaultTextControlWidth("<input size=\"6\" />"));
	}

	/**
	 * seed 4608881: size=16の入力欄は100pt(16ex+外枠4pt)。縦書きでも回転しないので、60pt幅の紙では
	 * 100pt厚の行になる。読み飛ばすと「作者が指定した大きさ」から漏れる(2026-09-17)。最も広いものを採る。
	 */
	public void testSizedTextInputContributesItsOwnWidth() {
		assertEquals(100.0, RandomDocumentFuzzTest
				.defaultTextControlWidth("<input type=\"text\" value=\"x\" size=\"16\" /><input type=\"radio\" />"));
		assertEquals(124.0, RandomDocumentFuzzTest
				.defaultTextControlWidth("<input type=\"text\" size=\"4\" /><textarea></textarea>"));
	}

	/** seed 473924の最小形。flex祖先→3段組→表の実際の入れ子だけを拾う。 */
	public void testFlexMulticolTableIsExcluded() {
		assertTrue(RandomDocumentFuzzTest.hasFlexMulticolTable(doc("",
				"<div style=\"display:flex\"><div style=\"column-count:3\"><table><tr><td>T0</td></tr></table></div></div>")));
	}

	/** flex外の段組表は専用除外にしない。 */
	public void testMulticolTableOutsideFlexIsNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasFlexMulticolTable(
				doc("", "<div style=\"column-count:3\"><table><tr><td>T0</td></tr></table></div>")));
	}

	/** flexと段組表が別の枝なら結び付けない。 */
	public void testFlexAndMulticolTableInSeparateBranchesAreNotExcluded() {
		assertFalse(RandomDocumentFuzzTest.hasFlexMulticolTable(doc("",
				"<div style=\"display:flex\">T0</div><div style=\"column-count:3\"><table><tr><td>T1</td></tr></table></div>")));
	}

	/** 1段指定、表なし、gridはそれぞれ専用除外にしない。 */
	public void testOtherIntrinsicContainersAreNotFlexMulticolTable() {
		assertFalse(RandomDocumentFuzzTest.hasFlexMulticolTable(doc("",
				"<div style=\"display:flex\"><div style=\"column-count:1\"><table><tr><td>T0</td></tr></table></div></div>")));
		assertFalse(RandomDocumentFuzzTest.hasFlexMulticolTable(
				doc("", "<div style=\"display:flex\"><div style=\"column-count:3\">T0</div></div>")));
		assertFalse(RandomDocumentFuzzTest.hasFlexMulticolTable(doc("",
				"<div style=\"display:grid\"><div style=\"column-count:3\"><table><tr><td>T0</td></tr></table></div></div>")));
	}

	/** 詳細ダンプの解析まで含め、紙面上のトークンを見つけられること。 */
	public void testDetailedDumpFindsVisibleToken() throws Exception {
		final File dump = writeDump("  x=-1.00 y=10.00 Text[\"T0\" asc=3.00 desc=2.00] w=2.00 h=5.00\n");
		try {
			RandomDocumentFuzzTest.assertSomeDrawingOnPage(generated(), new File[] { dump }, dump);
		} finally {
			dump.delete();
		}
	}

	/** テキストを持たない画像・フォームだけの文書も可視描画として数える。 */
	public void testDetailedDumpFindsVisibleNonTextDrawing() throws Exception {
		final File dump = writeDump("  x=10.00 y=10.00 AbsoluteRectFrame[w=20.00 h=20.00]\n");
		try {
			RandomDocumentFuzzTest.assertSomeDrawingOnPage(generated(), new File[] { dump }, dump);
		} finally {
			dump.delete();
		}
	}

	/** 全トークンの矩形が紙面外なら、新しい不変条件が実際に落ちること。 */
	public void testDetailedDumpRejectsAllTokensOffPage() throws Exception {
		final File dump = writeDump("  x=60.00 y=10.00 Text[\"T0\" asc=3.00 desc=2.00] w=2.00 h=5.00\n");
		try {
			try {
				RandomDocumentFuzzTest.assertSomeDrawingOnPage(generated(), new File[] { dump }, dump);
				fail("全描画が紙面外なら失敗しなければならない");
			} catch (final AssertionError e) {
				assertTrue(String.valueOf(e.getMessage()).contains("全描画が紙面外"));
			}
		} finally {
			dump.delete();
		}
	}

	// ------------------------------------------------------------------
	// FuzzShrinker.analyze: 読み順の並べ替えを許す部分木
	// ------------------------------------------------------------------

	public void testShrinkerTreatsAbsolutePositionedDescendantsAsReorderable() {
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker.analyze(shrinkerDoc(
				"<div style=\"position:absolute;top:0;left:0\"><p>T0</p></div><p>T1</p>"));
		assertNotNull(generated);
		assertEquals(Set.of("T0"), generated.reorderable());
	}

	public void testShrinkerDoesNotTreatFloatNoneDescendantsAsReorderable() {
		final RandomDocumentFuzzTest.Generated generated = FuzzShrinker
				.analyze(shrinkerDoc("<div style=\"float:none\"><p>T0</p></div>"));
		assertNotNull(generated);
		assertTrue(generated.reorderable().isEmpty());
	}

	// findUnfittableContent: 版面に物理的に収まらない内容(2026-09-17のユーザー裁定)
	// shrinkerDocは内容領域50x50pt・6pt(組版下限48pt)

	private static final String LONG_RUBY = "<p><ruby class=\"fuzz-long-ruby\">"
			+ "T10 T11 T12 T13 T14 T15 T16 T17 T18 T19 T20 T21<rt>T22</rt></ruby></p>";

	/** 12語(下限150pt)の割れないルビは50ptの行に入らない。はみ出しが行軸(x)のときだけ除外する。 */
	public void testLongRubyBeyondLineIsUnfittableOnlyAlongItsLineAxis() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_RUBY,
				RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(LONG_RUBY), false));
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(LONG_RUBY), true));
	}

	/** 縦書きの中のルビは行軸が縦(y)。seed 2031709の形。 */
	public void testLongRubyInVerticalFlowUsesPageHeight() {
		final String html = shrinkerDoc("<div style=\"writing-mode:vertical-rl\">" + LONG_RUBY + "</div>");
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_RUBY,
				RandomDocumentFuzzTest.findUnfittableContent(html, true));
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(html, false));
	}

	/** 行に入る長さのルビは除外しない(A4)。 */
	public void testLongRubyThatFitsIsNotUnfittable() {
		final String html = shrinkerDoc(LONG_RUBY).replace("value=\"60pt\"", "value=\"595pt\"");
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(html, false));
	}

	/** 短いルビ(クラス無し)は数えない。 */
	public void testShortRubyIsNotUnfittable() {
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<p><ruby>T0<rt>T1</rt></ruby></p>"), false));
	}

	/** min-width:8em(48pt)は50ptに入るが、表(−4pt)のセル(−2pt)の中では入らない。seed 5210679の形。 */
	public void testMinWidthBeyondCellIsUnfittable() {
		final String box = "<div style=\"width:calc(35% + 8em);min-width:8em;max-width:90%;\">T0</div>";
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(box), false));
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_MIN_WIDTH, RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<table><tr><td>" + box + "</td></tr></table>"), false));
	}

	/** 明示幅の箱より広いmin-width。別の枝の狭い箱とは結び付けない。 */
	public void testMinWidthIsComparedWithItsOwnAncestors() {
		final String box = "<div style=\"min-width:8em\">T0</div>";
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_MIN_WIDTH, RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<div style=\"width:30pt\">" + box + "</div>"), false));
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<div style=\"width:30pt\">T1</div>" + box), false));
	}

	/** min-widthを持つ箱の中は、明示幅が狭くてもそのmin-widthまで使える(同じmin-widthの子は収まる)。 */
	public void testMinWidthWidensItsOwnContent() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(
				"<div style=\"width:30pt;min-width:8em\"><div style=\"min-width:8em\">T0</div></div>"), false));
	}

	// --- 収まる文書を収まらないと言わないこと(2026-09-17のcodexレビューの反例) ---

	private static String wide(final String body) {
		return shrinkerDoc(body).replace("name=\"output.page-width\" value=\"60pt\"",
				"name=\"output.page-width\" value=\"210pt\"");
	}

	/** 内容幅200ptなら12語のルビ(下限121.2pt)は収まる。以下の反例の前提。 */
	public void testLongRubyFitsTwoHundredPoints() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(wide(LONG_RUBY), false));
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_RUBY, RandomDocumentFuzzTest
				.findUnfittableContent(wide("<div style=\"width:30pt\">" + LONG_RUBY + "</div>"), false));
	}

	/** 幅の宣言は後勝ち。最後が百分率なら静的には不明なので、前のpt値で狭めない。 */
	public void testLaterPercentageWidthOverridesEarlierLength() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				wide("<div style=\"width:30pt;width:80%;min-width:8em;max-width:90%\">" + LONG_RUBY + "</div>"),
				false));
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_RUBY, RandomDocumentFuzzTest.findUnfittableContent(
				wide("<div style=\"width:80%;width:30pt\">" + LONG_RUBY + "</div>"), false));
	}

	/** 非置換のinlineのwidthは効かないので、中身の上限にしない。 */
	public void testInlineWidthDoesNotBoundItsContent() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				wide("<div style=\"display:inline;width:30pt\">" + LONG_RUBY + "</div>"), false));
	}

	/** flex項目のwidthは伸びる前の基準でしかない。項目の中の段組を狭い段と決めつけない。 */
	public void testFlexItemWidthDoesNotBoundItsContent() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(wide("<div style=\"display:flex\">"
				+ "<div style=\"flex:1 1 auto;width:8em\"><div style=\"column-count:2\">T0</div></div></div>"),
				false));
		// flexの入れ物自身の幅は確定する
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_COLUMN, RandomDocumentFuzzTest.findUnfittableContent(
				wide("<div style=\"display:flex;width:60pt\"><div><div style=\"column-count:2\">T0</div></div></div>"),
				false));
	}

	/** 表とセルは内容に合わせて広がるので、そのwidthでは狭めない。 */
	public void testTableWidthDoesNotBoundItsContent() {
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				wide("<table style=\"width:30pt\"><tr><td style=\"width:30pt\">" + LONG_RUBY + "</td></tr></table>"),
				false));
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(
				wide("<div style=\"display:table;width:30pt\">" + LONG_RUBY + "</div>"), false));
	}

	/** 罫線を重ねる表には間隔が無く、引けるのは罫線の半分ずつ(計1pt)だけ。48ptの箱は50ptの表のセル(49pt)に収まる。 */
	public void testCollapsedTableDoesNotLoseSpacing() {
		final String cell = "<table><tr><td><div style=\"width:8em;min-width:8em\">T0</div></td></tr></table>";
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_MIN_WIDTH,
				RandomDocumentFuzzTest.findUnfittableContent(shrinkerDoc(cell), false));
		final String collapsed = shrinkerDoc(cell).replace("<style>", "<style>table{border-collapse:collapse}");
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(collapsed, false));
		// 同じ48ptの箱でも、48ptの入れ物の中の表のセル(47pt)には入らない(seed 2129171の形)
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_MIN_WIDTH, RandomDocumentFuzzTest.findUnfittableContent(
				collapsed.replace("<table>", "<div style=\"width:8em\"><table>").replace("</table>", "</table></div>"),
				false));
	}

	/** 浮動体の幅も後勝ちで読み、min-widthが勝つならその幅で見る。 */
	public void testNarrowFloatUsesTheEffectiveWidth() {
		assertFalse(RandomDocumentFuzzTest.hasNarrowFloat(
				shrinkerDoc("<div style=\"float:left;width:22pt;width:80%\">T0</div>"), 48));
		assertFalse(RandomDocumentFuzzTest.hasNarrowFloat(
				shrinkerDoc("<div style=\"float:left;width:22pt;min-width:8em\">T0</div>"), 48));
		assertTrue(RandomDocumentFuzzTest.hasNarrowFloat(
				shrinkerDoc("<div style=\"float:left;width:80%;width:22pt\">T0</div>"), 48));
	}

	/** 50ptを2段(間5pt)に割ると22.5pt=組版下限48pt未満。1段や、下限以上の段は除外しない。 */
	public void testNarrowColumnIsUnfittable() {
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_COLUMN, RandomDocumentFuzzTest.findUnfittableContent(
				shrinkerDoc("<div style=\"column-count:2;column-gap:5pt\">T0</div>"), false));
		assertNull(RandomDocumentFuzzTest
				.findUnfittableContent(shrinkerDoc("<div style=\"column-count:1\">T0</div>"), false));
		final String wide = shrinkerDoc("<div style=\"column-count:2;column-gap:5pt\">T0</div>")
				.replace("value=\"60pt\"", "value=\"595pt\"");
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(wide, false));
	}

	/** 縦書きの段組は高さを割る。 */
	public void testVerticalColumnsDivideHeight() {
		final String html = shrinkerDoc(
				"<div style=\"writing-mode:vertical-rl;column-count:2;column-gap:5pt\">T0</div>")
				.replace("name=\"output.page-width\" value=\"60pt\"", "name=\"output.page-width\" value=\"595pt\"");
		assertEquals(RandomDocumentFuzzTest.UNFITTABLE_COLUMN,
				RandomDocumentFuzzTest.findUnfittableContent(html, false));
		final String tall = shrinkerDoc(
				"<div style=\"writing-mode:vertical-rl;column-count:2;column-gap:5pt\">T0</div>")
				.replace("name=\"output.page-height\" value=\"60pt\"", "name=\"output.page-height\" value=\"842pt\"");
		assertNull(RandomDocumentFuzzTest.findUnfittableContent(tall, false));
	}

	/** 生成器v2はfloatとwidthの間にwriting-modeを挟む。宣言順で漏らさない(seed 3767082・4709606)。 */
	public void testNarrowFloatIsFoundRegardlessOfDeclarationOrder() {
		final String html = shrinkerDoc(
				"<div style=\"float:right;writing-mode:horizontal-tb;width:22pt;\"><ol><li>T0</li></ol></div>");
		assertTrue(RandomDocumentFuzzTest.hasNarrowFloat(html, 48));
		assertTrue(RandomDocumentFuzzTest.hasUntypesettableFloat(html));
		assertFalse(RandomDocumentFuzzTest.hasNarrowFloat(shrinkerDoc(
				"<div style=\"float:right;writing-mode:horizontal-tb;width:48pt;\">T0</div>"), 48));
		assertFalse(RandomDocumentFuzzTest.hasNarrowFloat(shrinkerDoc(
				"<div style=\"float:none;width:22pt;\">T0</div><div style=\"float:left\">T1</div>"), 48));
	}

	/**
	 * 除外述語が紙面外の検査をどれだけ覆うかの実測(-Dfoliojet.unfittableRate=件数 のときだけ)。
	 * 「従来の述語だけ」と「収まらない内容を足したあと」で、どちらの軸のはみ出しでも除外になる文書の数を比べる。
	 */
	public void testUnfittableDetectorRate() {
		final int n = Integer.getInteger("foliojet.unfittableRate", 0).intValue();
		if (n == 0) {
			return;
		}
		final java.util.Map<String, Integer> counts = new java.util.TreeMap<>();
		for (int seed = 0; seed < n; ++seed) {
			final RandomDocumentFuzzTest.Generated g = RandomDocumentFuzzTest.generate(5_250_000 + seed, true);
			final String html = g.html();
			final boolean anyAxis = g.beyondEngineControl()
					|| RandomDocumentFuzzTest.hasUntypesettableOppositeProgression(html)
					|| RandomDocumentFuzzTest.hasUntypesettableOrthogonalFlow(html)
					|| legacyUntypesettableFloat(html)
					|| RandomDocumentFuzzTest.hasFlexMulticolTable(html)
					|| RandomDocumentFuzzTest.orthogonalAxisChanges(html) >= 2;
			int before = 0, after = 0;
			for (final boolean failureIsY : new boolean[] { false, true }) {
				final boolean base = anyAxis || (failureIsY != RandomDocumentFuzzTest.pageAxisIsY(html)
						&& RandomDocumentFuzzTest.hasOrthogonalFlow(html));
				final String unfittable = RandomDocumentFuzzTest.findUnfittableContent(html, failureIsY);
				before += base ? 1 : 0;
				after += base || unfittable != null || RandomDocumentFuzzTest.hasUntypesettableFloat(html) ? 1 : 0;
				counts.merge("reason:" + unfittable, 1, Integer::sum);
			}
			counts.merge("before:excludedAxes=" + before, 1, Integer::sum);
			counts.merge("after:excludedAxes=" + after, 1, Integer::sum);
		}
		System.out.println("[unfittableRate] n=" + n + " " + counts);
	}

	/** 2026-09-17より前の「組版できない幅の浮動体」(floatとwidthの隣接が前提)。発火率の比較用。 */
	private static boolean legacyUntypesettableFloat(final String html) {
		final java.util.regex.Matcher fm = java.util.regex.Pattern.compile("font:normal (\\d+)pt").matcher(html);
		if (!fm.find()) {
			return false;
		}
		final double least = Double.parseDouble(fm.group(1)) * 8;
		final java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("float:[a-z]+;(?:width|height):(\\d+)pt").matcher(html);
		while (m.find()) {
			if (Double.parseDouble(m.group(1)) < least) {
				return true;
			}
		}
		return RandomDocumentFuzzTest.hasFloatInsideNarrowContainer(html, least)
				|| RandomDocumentFuzzTest.hasOverwideFloat(html);
	}

	private static String shrinkerDoc(final String body) {
		return "<?jp.cssj.property name=\"output.page-width\" value=\"60pt\"?>"
				+ "<?jp.cssj.property name=\"output.page-height\" value=\"60pt\"?>"
				+ "<html><head><style>@page{margin:5pt}body{margin:0;font:normal 6pt/1.2 serif}</style></head>"
				+ "<body>" + body + "</body></html>";
	}

	private static RandomDocumentFuzzTest.Generated generated() {
		return new RandomDocumentFuzzTest.Generated("<html><body>T0</body></html>", List.of("T0"), Set.of(), 60,
				60, 0, false, false);
	}

	private static File writeDump(final String line) throws Exception {
		final File dump = File.createTempFile("fuzz-visible-", ".txt");
		Files.writeString(dump.toPath(), line, StandardCharsets.UTF_8);
		return dump;
	}
}
