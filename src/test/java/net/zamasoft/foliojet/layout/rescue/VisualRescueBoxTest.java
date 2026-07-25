package net.zamasoft.foliojet.layout.rescue;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.Deque;

import junit.framework.TestCase;
import net.zamasoft.foliojet.layout.box.AbstractBox;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.FloatPos;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * 救済分割の断片({@link VisualRescueBox})のclip・座標・枠slice描画の
 * 単体テストです(2026-07-25新設、増分3。<b>まだ未配線</b>)。
 *
 * <p>
 * 元ボックスは{@link FakeSource}(描画引数を記録するだけのテストダブル)に
 * 置き換えています。断片が担うのは「クリップの交差」と「元ボックスを
 * どこから描くか」の2点だけなので、これで機能の全部を固定できます。
 * 枠線・マージンがsliceになること(切断面に装飾なし、先頭断片に上、
 * 最終断片に下)は、元ボックスの装飾帯がクリップの内か外かという
 * 幾何の問題に還元して検証します。
 * </p>
 */
public class VisualRescueBoxTest extends TestCase {

	private static final double SOURCE_PAGE_EXTENT = 100;

	private static final double SOURCE_LINE_EXTENT = 60;

	/** 元ボックスの上枠線の帯(ページ方向の区間)。 */
	private static final double TOP_DECORATION_END = 5;

	/** 元ボックスの下枠線の帯(ページ方向の区間)。 */
	private static final double BOTTOM_DECORATION_START = 95;

	// ------------------------------------------------------------------
	// テストダブル
	// ------------------------------------------------------------------

	/** 描画・輪郭・テキスト抽出の呼び出しを記録するだけの元ボックスです。 */
	private static class FakeSource extends AbstractBox implements IFlowBox {
		private final BlockParams params = new BlockParams();
		private final Pos pos = new FlowPos();
		private final double width, height;

		int drawCount = 0;
		Shape drawClip = null;
		double drawX = Double.NaN, drawY = Double.NaN;
		int textShapeCount = 0;
		double textShapeX = Double.NaN, textShapeY = Double.NaN;

		boolean avoidBefore = false, avoidAfter = false;

		FakeSource(final double width, final double height) {
			this.width = width;
			this.height = height;
		}

		public BoxType getType() {
			return BoxType.REPLACED;
		}

		public Params getParams() {
			return this.params;
		}

		public Pos getPos() {
			return this.pos;
		}

		public double getWidth() {
			return this.width;
		}

		public double getHeight() {
			return this.height;
		}

		public double getInnerWidth() {
			return this.width;
		}

		public double getInnerHeight() {
			return this.height;
		}

		public void finishLayoutSelf(final IFramedBox containerBox) {
			TestCase.fail("断片は元ボックスをfinishLayoutし直さない");
		}

		public void pushFinishLayoutChildren(final IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
			TestCase.fail("断片は元ボックスをfinishLayoutし直さない");
		}

		public void pushDrawSteps(final PageBox pageBox, final Drawer drawer, final Visitor visitor, final Shape clip,
				final AffineTransform transform, final double contextX, final double contextY, final double x,
				final double y, final Deque<DrawStep> worklist) {
			++this.drawCount;
			this.drawClip = clip;
			this.drawX = x;
			this.drawY = y;
			this.drawArtifact = drawer != null && drawer.isArtifact();
			this.drawVisitor = visitor;
		}

		public void pushGetTextSteps(final StringBuilder textBuff, final Deque<GetTextStep> worklist) {
			textBuff.append("SOURCE");
		}

		boolean drawArtifact;

		Visitor drawVisitor;

		public void pushTextShapeSteps(final PageBox pageBox, final GeneralPath path, final AffineTransform transform,
				final double x, final double y, final Deque<TextShapeStep> worklist) {
			++this.textShapeCount;
			this.textShapeX = x;
			this.textShapeY = y;
		}

		public boolean avoidBreakBefore() {
			return this.avoidBefore;
		}

		public boolean avoidBreakAfter() {
			return this.avoidAfter;
		}
	}

	/** float用の元ボックス。 */
	private static final class FakeFloatSource extends FakeSource implements IFloatBox {
		private final FloatPos floatPos = new FloatPos();

		FakeFloatSource(final double width, final double height) {
			super(width, height);
		}

		public FloatPos getFloatPos() {
			return this.floatPos;
		}

		public Pos getPos() {
			return this.floatPos;
		}
	}

	private static FakeSource source(final WritingMode progression) {
		return progression.isVertical() ? new FakeSource(SOURCE_PAGE_EXTENT, SOURCE_LINE_EXTENT)
				: new FakeSource(SOURCE_LINE_EXTENT, SOURCE_PAGE_EXTENT);
	}

	private static VisualRescueFlowBox fragment(final FakeSource src, final WritingMode progression,
			final double offset, final double sliceExtent) {
		return new VisualRescueFlowBox(src, progression, SOURCE_PAGE_EXTENT, offset, sliceExtent);
	}

	/**
	 * 元ボックスのページ方向区間{@code [from, to)}が、実際に描かれた
	 * 位置で占める物理区間(横書きならY、縦書きならX)を返します。
	 */
	private static double[] physicalBand(final VisualRescueBox box, final double sourceX, final double sourceY,
			final double from, final double to) {
		if (!box.getProgression().isVertical()) {
			return new double[] { sourceY + from, sourceY + to };
		}
		// 縦書きはページ軸が右→左に進む
		final double right = sourceX + box.getSourcePageExtent();
		return new double[] { right - to, right - from };
	}

	/** クリップ矩形のページ方向区間を返します。 */
	private static double[] clipBand(final VisualRescueBox box, final Rectangle2D clip) {
		return box.getProgression().isVertical() ? new double[] { clip.getMinX(), clip.getMaxX() }
				: new double[] { clip.getMinY(), clip.getMaxY() };
	}

	private static boolean contains(final double[] outer, final double[] inner) {
		return outer[0] <= inner[0] && inner[1] <= outer[1];
	}

	private static boolean disjoint(final double[] a, final double[] b) {
		return a[1] <= b[0] || b[1] <= a[0];
	}

	// ------------------------------------------------------------------
	// 寸法
	// ------------------------------------------------------------------

	/** 断片が変えるのはページ方向の占有量だけ。行方向は元ボックスのまま。 */
	public void testOnlyPageExtentDiffersFromSource() {
		for (final WritingMode progression : WritingMode.values()) {
			final FakeSource src = source(progression);
			final VisualRescueBox box = fragment(src, progression, 20, 30);
			assertEquals(progression.name(), SOURCE_LINE_EXTENT, box.getLineExtent(progression), 0);
			assertEquals(progression.name(), 30.0, box.getPageExtent(progression), 0);
			assertEquals(progression.name(), SOURCE_LINE_EXTENT, src.getLineExtent(progression), 0);
			assertEquals(progression.name(), SOURCE_PAGE_EXTENT, src.getPageExtent(progression), 0);
		}
	}

	/** 断片は独立の型を名乗る(既存型を偽装しない)。 */
	public void testTypeIsRescue() {
		final FakeSource src = source(WritingMode.TB);
		assertEquals(BoxType.RESCUE, fragment(src, WritingMode.TB, 0, 40).getType());
		assertEquals(BoxType.REPLACED, src.getType());
	}

	/** Params・Posは元ボックスのものをそのまま返す(コピーも改変もしない)。 */
	public void testParamsAndPosAreShared() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox box = fragment(src, WritingMode.TB, 0, 40);
		assertSame(src.getParams(), box.getParams());
		assertSame(src.getPos(), box.getPos());
	}

	/** 救済断片はレシピ再生の対象外(SourceAnchorを持たない)。 */
	public void testSourceAnchorStaysUnset() {
		assertEquals(-1L, fragment(source(WritingMode.TB), WritingMode.TB, 0, 40).getSourceAnchor());
	}

	/** 先頭・最終の判定。 */
	public void testFirstAndLastFragmentFlags() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox head = fragment(src, WritingMode.TB, 0, 40);
		final VisualRescueBox middle = fragment(src, WritingMode.TB, 40, 40);
		final VisualRescueBox tail = fragment(src, WritingMode.TB, 80, 20);
		assertTrue(head.isFirstFragment());
		assertFalse(head.isLastFragment());
		assertFalse(head.isContinuation());
		assertFalse(middle.isFirstFragment());
		assertFalse(middle.isLastFragment());
		assertTrue(middle.isContinuation());
		assertFalse(tail.isFirstFragment());
		assertTrue(tail.isLastFragment());
		assertTrue(tail.isContinuation());
	}

	// ------------------------------------------------------------------
	// 座標(TB / RL / LR)
	// ------------------------------------------------------------------

	/** 横書き: sourceY = fragmentY - offset、Xはそのまま。 */
	public void testHorizontalCoordinates() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox box = fragment(src, WritingMode.TB, 40, 30);
		box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, 17, 200);
		assertEquals(1, src.drawCount);
		assertEquals(17.0, src.drawX, 0);
		assertEquals(200.0 - 40.0, src.drawY, 0);
	}

	/**
	 * 縦書き: sourceX = fragmentX - (sourcePageExtent - offset - sliceExtent)、
	 * Yはそのまま。RLとLRの内部規約は同じ(ページ軸は右→左)。
	 */
	public void testVerticalCoordinates() {
		for (final WritingMode progression : new WritingMode[] { WritingMode.RL, WritingMode.LR }) {
			final FakeSource src = source(progression);
			final VisualRescueBox box = fragment(src, progression, 40, 30);
			box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, 300, 17);
			assertEquals(progression.name(), 1, src.drawCount);
			// 未消費の残余 = 100 - 40 - 30 = 30
			assertEquals(progression.name(), 300.0 - 30.0, src.drawX, 0);
			assertEquals(progression.name(), 17.0, src.drawY, 0);
		}
	}

	/** 先頭断片は元ボックスと同じ位置から描かれる(ずれない)。 */
	public void testFirstFragmentDrawsSourceAtTheFragmentOrigin() {
		for (final WritingMode progression : WritingMode.values()) {
			final FakeSource src = source(progression);
			// 先頭断片(offset=0、まだ残余がある)
			final VisualRescueBox box = fragment(src, progression, 0, 40);
			box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, 50, 60);
			if (progression.isVertical()) {
				// 未消費の残余 = 100 - 0 - 40 = 60 だけ左へ寄る
				assertEquals(progression.name(), 50.0 - 60.0, src.drawX, 0);
				assertEquals(progression.name(), 60.0, src.drawY, 0);
			} else {
				assertEquals(progression.name(), 50.0, src.drawX, 0);
				assertEquals(progression.name(), 60.0, src.drawY, 0);
			}
		}
	}

	/** 最終断片は「元ボックスの終端が断片の終端に一致する」位置から描かれる。 */
	public void testLastFragmentAlignsTheSourceEnd() {
		for (final WritingMode progression : WritingMode.values()) {
			final FakeSource src = source(progression);
			final VisualRescueBox box = fragment(src, progression, 80, 20);
			box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, 50, 60);
			if (progression.isVertical()) {
				// 残余0なので元ボックスの左端は断片の左端と一致
				assertEquals(progression.name(), 50.0, src.drawX, 0);
			} else {
				assertEquals(progression.name(), 60.0 - 80.0, src.drawY, 0);
			}
		}
	}

	// ------------------------------------------------------------------
	// artifact化(2026-07-25、増分5)
	// ------------------------------------------------------------------

	/**
	 * 先頭断片は実内容として描く(実Visitor・非artifact)。
	 */
	public void testFirstFragmentDrawsAsRealContent() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox box = fragment(src, WritingMode.TB, 0, 30);
		final Visitor visitor = new net.zamasoft.foliojet.layout.visitor.VisitorWrapper(null);
		box.draw(null, new Drawer(0), visitor, null, new AffineTransform(), 0, 0, 0, 0);
		assertFalse("先頭断片はartifactではない", src.drawArtifact);
		assertSame("先頭断片は実Visitorで描く", visitor, src.drawVisitor);
	}

	/**
	 * 継続断片({@code offset > 0})はartifactとして描き、副作用のない
	 * Visitorを渡す(答申§3。リンク・フォーム・ページ参照・string-set・
	 * しおりを二度発行しない)。
	 */
	public void testContinuationFragmentDrawsAsArtifact() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox box = fragment(src, WritingMode.TB, 40, 30);
		final Visitor visitor = new net.zamasoft.foliojet.layout.visitor.VisitorWrapper(null);
		box.draw(null, new Drawer(0), visitor, null, new AffineTransform(), 0, 0, 0, 0);
		assertTrue("継続断片はartifact", src.drawArtifact);
		assertSame("継続断片は副作用のないVisitorで描く",
				net.zamasoft.foliojet.layout.visitor.ArtifactVisitor.INSTANCE, src.drawVisitor);
	}

	// ------------------------------------------------------------------
	// クリップ
	// ------------------------------------------------------------------

	/** クリップが無いときは断片の矩形そのものになる。 */
	public void testClipWithoutExistingClipIsTheFragmentRect() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox box = fragment(src, WritingMode.TB, 40, 30);
		box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, 10, 200);
		final Rectangle2D clip = (Rectangle2D) src.drawClip;
		assertEquals(10.0, clip.getX(), 0);
		assertEquals(200.0, clip.getY(), 0);
		assertEquals(SOURCE_LINE_EXTENT, clip.getWidth(), 0);
		assertEquals(30.0, clip.getHeight(), 0);
	}

	/** 既存クリップとは交差する(AbstractContainerBox.clip()と同じ流儀)。 */
	public void testClipIntersectsTheExistingClip() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox box = fragment(src, WritingMode.TB, 40, 30);
		// 断片は (10,200)-(70,230)。既存クリップで左右と下を削る
		final Rectangle2D.Double outer = new Rectangle2D.Double(30, 100, 100, 120);
		box.draw(null, new Drawer(0), null, outer, new AffineTransform(), 0, 0, 10, 200);
		final Rectangle2D clip = (Rectangle2D) src.drawClip;
		final Rectangle2D expected = new Rectangle2D.Double(10, 200, SOURCE_LINE_EXTENT, 30)
				.createIntersection(outer);
		assertEquals(expected.getX(), clip.getX(), 0);
		assertEquals(expected.getY(), clip.getY(), 0);
		assertEquals(expected.getWidth(), clip.getWidth(), 0);
		assertEquals(expected.getHeight(), clip.getHeight(), 0);
		assertEquals(30.0, clip.getX(), 0);
		assertEquals(200.0, clip.getY(), 0);
		assertEquals(40.0, clip.getWidth(), 0);
		assertEquals(20.0, clip.getHeight(), 0);
	}

	/** 縦書きでもクリップは断片の物理矩形(幅がsliceExtent)。 */
	public void testVerticalClipIsTheFragmentRect() {
		final FakeSource src = source(WritingMode.RL);
		final VisualRescueBox box = fragment(src, WritingMode.RL, 40, 30);
		box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, 300, 17);
		final Rectangle2D clip = (Rectangle2D) src.drawClip;
		assertEquals(300.0, clip.getX(), 0);
		assertEquals(17.0, clip.getY(), 0);
		assertEquals(30.0, clip.getWidth(), 0);
		assertEquals(SOURCE_LINE_EXTENT, clip.getHeight(), 0);
	}

	/** 交差できないクリップ形状は既存実装と同じくClassCastExceptionになる。 */
	public void testNonRectangularClipIsRejectedLikeTheExistingClipConvention() {
		final FakeSource src = source(WritingMode.TB);
		final VisualRescueBox box = fragment(src, WritingMode.TB, 0, 30);
		try {
			box.clip(new Ellipse2D.Double(0, 0, 10, 10), 0, 0);
			fail("矩形以外のクリップはAbstractContainerBox.clip()と同様に扱えない");
		} catch (final ClassCastException expected) {
			// 期待どおり(既存の流儀に合わせている)
		}
	}

	// ------------------------------------------------------------------
	// 枠線・マージンのslice
	// ------------------------------------------------------------------

	/**
	 * 先頭断片だけが上の装飾を含み、最終断片だけが下の装飾を含み、
	 * 中間断片はどちらも含まない(CSS box-decoration-break: slice)。
	 * 切断面に新しい線は現れない——断片は元の幾何をそのまま描いて
	 * クリップするだけだから。
	 */
	public void testDecorationIsSlicedAcrossFragments() {
		for (final WritingMode progression : WritingMode.values()) {
			final double[][] intervals = { { 0, 40 }, { 40, 40 }, { 80, 20 } };
			for (int i = 0; i < intervals.length; ++i) {
				final FakeSource src = source(progression);
				final VisualRescueBox box = fragment(src, progression, intervals[i][0], intervals[i][1]);
				box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, 500, 400);
				final double[] clip = clipBand(box, (Rectangle2D) src.drawClip);
				final double[] top = physicalBand(box, src.drawX, src.drawY, 0, TOP_DECORATION_END);
				final double[] bottom = physicalBand(box, src.drawX, src.drawY, BOTTOM_DECORATION_START,
						SOURCE_PAGE_EXTENT);
				final String at = progression + " fragment#" + i;
				if (i == 0) {
					assertTrue(at + ": 先頭断片は上枠線を含む", contains(clip, top));
					assertTrue(at + ": 先頭断片は下枠線を含まない", disjoint(clip, bottom));
				} else if (i == intervals.length - 1) {
					assertTrue(at + ": 最終断片は上枠線を含まない", disjoint(clip, top));
					assertTrue(at + ": 最終断片は下枠線を含む", contains(clip, bottom));
				} else {
					assertTrue(at + ": 中間断片は上枠線を含まない", disjoint(clip, top));
					assertTrue(at + ": 中間断片は下枠線を含まない", disjoint(clip, bottom));
				}
			}
		}
	}

	/** 断片を並べると元ボックスのページ方向をちょうど覆う(重なりも隙間もない)。 */
	public void testFragmentsTileTheSourceExactly() {
		for (final WritingMode progression : WritingMode.values()) {
			final double[][] intervals = { { 0, 40 }, { 40, 40 }, { 80, 20 } };
			double covered = 0;
			for (final double[] interval : intervals) {
				final FakeSource src = source(progression);
				final VisualRescueBox box = fragment(src, progression, interval[0], interval[1]);
				// 断片はページ軸上で連続して置かれる
				final double fragmentX = progression.isVertical() ? 500 - covered - interval[1] : 500;
				final double fragmentY = progression.isVertical() ? 400 : 400 + covered;
				box.draw(null, new Drawer(0), null, null, new AffineTransform(), 0, 0, fragmentX, fragmentY);
				// どの断片も同じ位置に元ボックスを置く(=見た目が連続する)
				if (progression.isVertical()) {
					assertEquals(progression.name(), 500 - SOURCE_PAGE_EXTENT, src.drawX, 0);
				} else {
					assertEquals(progression.name(), 400.0, src.drawY, 0);
				}
				covered += interval[1];
			}
			assertEquals(SOURCE_PAGE_EXTENT, covered, 0);
		}
	}

	// ------------------------------------------------------------------
	// 意味論
	// ------------------------------------------------------------------

	/** テキストは先頭断片だけが一度返す(抽出・読み上げの二重化を防ぐ)。 */
	public void testOnlyTheFirstFragmentYieldsText() {
		final FakeSource src = source(WritingMode.TB);
		final StringBuilder head = new StringBuilder();
		fragment(src, WritingMode.TB, 0, 40).getText(head);
		assertEquals("SOURCE", head.toString());

		final StringBuilder middle = new StringBuilder();
		fragment(src, WritingMode.TB, 40, 40).getText(middle);
		assertEquals("", middle.toString());

		final StringBuilder tail = new StringBuilder();
		fragment(src, WritingMode.TB, 80, 20).getText(tail);
		assertEquals("", tail.toString());
	}

	/** 輪郭は見た目の話なので全断片が委譲する(座標だけずらす)。 */
	public void testTextShapeIsDelegatedWithShiftedOrigin() {
		final FakeSource src = source(WritingMode.TB);
		fragment(src, WritingMode.TB, 40, 30).textShape(null, new GeneralPath(), new AffineTransform(), 10, 200);
		assertEquals(1, src.textShapeCount);
		assertEquals(10.0, src.textShapeX, 0);
		assertEquals(160.0, src.textShapeY, 0);
	}

	/** 断片は元ボックスのレイアウトをやり直さない。 */
	public void testFinishLayoutDoesNotTouchTheSource() {
		final FakeSource src = source(WritingMode.TB);
		// FakeSourceのfinishLayoutはfail()するので、呼ばれれば失敗する
		fragment(src, WritingMode.TB, 40, 30).finishLayout(null);
	}

	// ------------------------------------------------------------------
	// アダプタ
	// ------------------------------------------------------------------

	/** 改ページ禁止は端の断片だけが元ボックスの指定を引き継ぐ。 */
	public void testAvoidBreakOnlyAppliesToTheOuterEdges() {
		final FakeSource src = source(WritingMode.TB);
		src.avoidBefore = true;
		src.avoidAfter = true;
		final VisualRescueFlowBox head = fragment(src, WritingMode.TB, 0, 40);
		final VisualRescueFlowBox middle = fragment(src, WritingMode.TB, 40, 40);
		final VisualRescueFlowBox tail = fragment(src, WritingMode.TB, 80, 20);
		assertTrue(head.avoidBreakBefore());
		assertFalse(head.avoidBreakAfter());
		assertFalse(middle.avoidBreakBefore());
		assertFalse(middle.avoidBreakAfter());
		assertFalse(tail.avoidBreakBefore());
		assertTrue(tail.avoidBreakAfter());
	}

	/** float用アダプタは配置パラメータをそのまま返す。 */
	public void testFloatAdapterSharesTheFloatPos() {
		final FakeFloatSource src = new FakeFloatSource(SOURCE_LINE_EXTENT, SOURCE_PAGE_EXTENT);
		final VisualRescueFloatBox box = new VisualRescueFloatBox(src, WritingMode.TB, SOURCE_PAGE_EXTENT, 40, 30);
		assertSame(src.getFloatPos(), box.getFloatPos());
		assertEquals(BoxType.RESCUE, box.getType());
	}

	/** ファクトリは元ボックスの種類に応じたアダプタを選ぶ。 */
	public void testFactorySelectsTheAdapter() {
		final RescueDecision.Slice slice = new RescueDecision.Slice(40, 30, 70, false, false);
		final VisualRescueBox flow = VisualRescueBox.of(source(WritingMode.TB), WritingMode.TB, SOURCE_PAGE_EXTENT,
				slice);
		assertTrue(flow instanceof VisualRescueFlowBox);
		final VisualRescueBox floating = VisualRescueBox.of(
				new FakeFloatSource(SOURCE_LINE_EXTENT, SOURCE_PAGE_EXTENT), WritingMode.TB, SOURCE_PAGE_EXTENT,
				slice);
		assertTrue(floating instanceof VisualRescueFloatBox);
	}

	// ------------------------------------------------------------------
	// 不変条件
	// ------------------------------------------------------------------

	/** 断片の入れ子は作らない(区間はoffset/sliceExtentだけで表す)。 */
	public void testNestedFragmentsAreRejected() {
		final VisualRescueBox box = fragment(source(WritingMode.TB), WritingMode.TB, 0, 40);
		try {
			new VisualRescueBox(box, WritingMode.TB, 40, 0, 20);
			fail("断片の断片は作れないはず");
		} catch (final IllegalArgumentException expected) {
			// 期待どおり
		}
	}

	/** 元ボックスをはみ出す断片は作れない。 */
	public void testFragmentOutsideTheSourceIsRejected() {
		final FakeSource src = source(WritingMode.TB);
		try {
			new VisualRescueBox(src, WritingMode.TB, SOURCE_PAGE_EXTENT, 80, 40);
			fail("元ボックスをはみ出す断片は作れないはず");
		} catch (final IllegalArgumentException expected) {
			// 期待どおり
		}
	}

	/** 非正の断片・負のoffsetは作れない。 */
	public void testDegenerateIntervalsAreRejected() {
		final FakeSource src = source(WritingMode.TB);
		try {
			new VisualRescueBox(src, WritingMode.TB, SOURCE_PAGE_EXTENT, 0, 0);
			fail("寸法0の断片は作れないはず");
		} catch (final IllegalArgumentException expected) {
			// 期待どおり
		}
		try {
			new VisualRescueBox(src, WritingMode.TB, SOURCE_PAGE_EXTENT, -1, 10);
			fail("負のoffsetは作れないはず");
		} catch (final IllegalArgumentException expected) {
			// 期待どおり
		}
	}
}
