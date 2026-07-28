package jp.cssj.test.unit.displaylist;

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
}
