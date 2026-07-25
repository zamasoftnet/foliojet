package net.zamasoft.foliojet.layout.rescue;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.awt.geom.Rectangle2D;
import java.util.Deque;

import net.zamasoft.foliojet.layout.box.AbstractBox;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.FinishLayoutStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.IFloatBox;
import net.zamasoft.foliojet.layout.box.IFlowBox;
import net.zamasoft.foliojet.layout.box.IFramedBox;
import net.zamasoft.foliojet.layout.box.TextShapeStep;
import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.foliojet.layout.visitor.ArtifactVisitor;
import net.zamasoft.foliojet.layout.visitor.Visitor;

/**
 * 救済分割(visual rescue split)の断片です
 * (2026-07-25新設、増分3。{@code docs/consultations/consult-rescue-split-codex.md}
 * §2。<b>まだ本番経路へは配線されていません</b>)。
 *
 * <p>
 * 「同じ箱を、消費済み量だけずらして、残りをクリップして描く」という
 * 短命な描画デコレータです。元ボックス({@code source})の寸法・Params・
 * Pos・Containerは<b>一切</b>変更しません。断片が変えるのはページ方向の
 * <b>占有量</b>だけで、行方向の寸法は元ボックスと同じです。つまり
 * 「元ボックスのレイアウト寸法」と「フラグメント上の占有寸法」を
 * 分離します。
 * </p>
 *
 * <h2>座標</h2>
 *
 * <p>
 * 断片の物理原点{@code (x, y)}に対して、元ボックスを描く原点は
 * </p>
 *
 * <pre>
 * 横書き(TB): sourceX = x
 *             sourceY = y - offset
 * 縦書き(RL/LR): sourceX = x - (sourcePageExtent - offset - sliceExtent)
 *                sourceY = y
 * </pre>
 *
 * <p>
 * です。縦書きのページ軸が右→左に進む内部規約は
 * {@link LayoutUtils#drawX} と {@link LayoutUtils#drawY} に集約されており、
 * 上式はその規約から導かれます(縦書きではページ方向始端=元ボックスの
 * 右端。断片の左端{@code x}から元ボックスの左端までの距離が
 * 「まだ消費していない残余」{@code sourcePageExtent - offset - sliceExtent}
 * になる)。
 * </p>
 *
 * <h2>枠線・マージン</h2>
 *
 * <p>
 * 断片の矩形でクリップするだけなので、装飾は自然にsliceになります
 * (CSS {@code box-decoration-break: slice}と同じ):先頭断片だけが上
 * マージン・上枠線を含み、最終断片だけが下枠線・下マージンを含み、
 * 中間断片はどちらも含まず、切断面に新しい線は描かれません。
 * {@code AbsoluteRectFrame.cut()}は<b>使いません</b>——元の幾何を保った
 * クリップの方が、背景画像やtransformを含めて正確だからです(答申§2)。
 * </p>
 *
 * <h2>意味論</h2>
 *
 * <p>
 * 続きの断片({@code offset > 0})は「見た目は内容、意味の上では先頭断片に
 * 属する」ものです。{@link #pushGetTextSteps}は先頭断片だけが元ボックスへ
 * 委譲し、継続断片は何も返しません。PDF上のartifact化(構造タグを開かない)
 * は描画側({@code Drawer}のartifact属性 +
 * {@code GC.beginArtifactScope()})が担当します。
 * </p>
 *
 * <h2>SourceAnchor</h2>
 *
 * <p>
 * 救済断片はソースイベントではなくレイアウト済みボックスから派生する
 * ページング状態なので、{@code sourceAnchor}は{@code -1}のままです
 * ({@link AbstractBox}の既定)。{@code stampRanges()}はアンカーが非負の
 * ボックスだけを再生対象にするため、レシピ再生と救済tailは二重化しません。
 * </p>
 */
public class VisualRescueBox extends AbstractBox {

	private final IBox source;

	private final WritingMode progression;

	private final double sourcePageExtent;

	private final double offset;

	private final double sliceExtent;

	/**
	 * @param source           レイアウト済みの元ボックス
	 * @param progression      ページ軸を決める書字方向(包含ブロックのもの)
	 * @param sourcePageExtent 元ボックスのページ方向の占有量(不変)
	 * @param offset           この断片が始まるページ方向位置
	 * @param sliceExtent      この断片の占有量
	 */
	public VisualRescueBox(final IBox source, final WritingMode progression, final double sourcePageExtent,
			final double offset, final double sliceExtent) {
		if (source == null) {
			throw new IllegalArgumentException("source");
		}
		if (source instanceof VisualRescueBox) {
			// 断片の断片は作らない。区間はoffset/sliceExtentだけで表す
			throw new IllegalArgumentException("救済断片を入れ子にしない: " + source);
		}
		if (progression == null) {
			throw new IllegalArgumentException("progression");
		}
		if (!(sourcePageExtent > 0)) {
			throw new IllegalArgumentException("sourcePageExtent=" + sourcePageExtent);
		}
		if (!(offset >= 0)) {
			throw new IllegalArgumentException("offset=" + offset);
		}
		if (!(sliceExtent > 0)) {
			throw new IllegalArgumentException("sliceExtent=" + sliceExtent);
		}
		if (LayoutUtils.compare(offset + sliceExtent, sourcePageExtent) > 0) {
			throw new IllegalArgumentException(
					"断片が元ボックスをはみ出す: offset=" + offset + " sliceExtent=" + sliceExtent + " source=" + sourcePageExtent);
		}
		this.source = source;
		this.progression = progression;
		this.sourcePageExtent = sourcePageExtent;
		this.offset = offset;
		this.sliceExtent = sliceExtent;
	}

	/**
	 * 判定結果から断片を作ります。元ボックスの種類に応じて通常フロー用と
	 * float用のアダプタを選びます。
	 *
	 * @param source           レイアウト済みの元ボックス
	 * @param progression      ページ軸を決める書字方向
	 * @param sourcePageExtent 元ボックスのページ方向の占有量
	 * @param slice            {@link VisualRescuePlanner}の判定結果
	 * @return 断片
	 */
	public static VisualRescueBox of(final IBox source, final WritingMode progression, final double sourcePageExtent,
			final RescueDecision.Slice slice) {
		if (source instanceof IFloatBox floatBox) {
			return new VisualRescueFloatBox(floatBox, progression, sourcePageExtent, slice.offset(),
					slice.sliceExtent());
		}
		if (source instanceof IFlowBox flowBox) {
			return new VisualRescueFlowBox(flowBox, progression, sourcePageExtent, slice.offset(),
					slice.sliceExtent());
		}
		return new VisualRescueBox(source, progression, sourcePageExtent, slice.offset(), slice.sliceExtent());
	}

	public final IBox getSource() {
		return this.source;
	}

	public final WritingMode getProgression() {
		return this.progression;
	}

	/** 元ボックスのページ方向の占有量です(断片ごとに変わりません)。 */
	public final double getSourcePageExtent() {
		return this.sourcePageExtent;
	}

	/** この断片が始まるページ方向位置(消費済み量)です。 */
	public final double getOffset() {
		return this.offset;
	}

	/** この断片の占有量です。 */
	public final double getSliceExtent() {
		return this.sliceExtent;
	}

	/** 先頭断片(上マージン・上枠線を持つ)であればtrueを返します。 */
	public final boolean isFirstFragment() {
		return this.offset == 0;
	}

	/** 最終断片(下枠線・下マージンを持つ)であればtrueを返します。 */
	public final boolean isLastFragment() {
		return LayoutUtils.compare(this.offset + this.sliceExtent, this.sourcePageExtent) >= 0;
	}

	/**
	 * 続きの断片(PDFのartifactとして出す側)であればtrueを返します。
	 * 答申§3のとおり{@code offset > 0}が唯一の判定です。
	 */
	public final boolean isContinuation() {
		return !this.isFirstFragment();
	}

	public final BoxType getType() {
		return BoxType.RESCUE;
	}

	/**
	 * 元ボックスのページ方向<b>終端側</b>のマージンです(2026-07-25、増分5)。
	 *
	 * <p>
	 * 断片の直後でマージンの折りたたみを再開するために使います。元
	 * ボックスの下マージンは<b>最終断片の幾何の中</b>にあるため、断片の
	 * 占有量には既に含まれています。ここが返すのは「次の内容の上マージンと
	 * 相殺してよい量」です({@code BlockBuilder.addBound}の
	 * {@code poLastMargin}の設定と同じ軸の選び方)。
	 * </p>
	 *
	 * @param flow 軸を決める書字方向
	 * @return 折りたたみ対象のマージン(枠を持たない元ボックスなら0)
	 */
	public final double sourceCollapsibleEndMargin(final WritingMode flow) {
		if (!(this.source instanceof IFramedBox framed)) {
			return 0;
		}
		final net.zamasoft.foliojet.layout.part.AbsoluteRectFrame frame = framed.getFrame();
		return flow.isVertical() ? frame.margin.left : frame.margin.bottom;
	}

	public final Params getParams() {
		return this.source.getParams();
	}

	public Pos getPos() {
		return this.source.getPos();
	}

	public final double getWidth() {
		return this.progression.isVertical() ? this.sliceExtent : this.source.getWidth();
	}

	public final double getHeight() {
		return this.progression.isVertical() ? this.source.getHeight() : this.sliceExtent;
	}

	/**
	 * 断片には固有の枠がないため、内部寸法は「行方向は元ボックスの内部
	 * 寸法、ページ方向はこの断片の占有量」とします(IBoxの契約を満たす
	 * ためだけの値で、レイアウトには使われません)。
	 */
	public final double getInnerWidth() {
		return this.progression.isVertical() ? this.sliceExtent : this.source.getInnerWidth();
	}

	/** @see #getInnerWidth() */
	public final double getInnerHeight() {
		return this.progression.isVertical() ? this.source.getInnerHeight() : this.sliceExtent;
	}

	/**
	 * 断片の物理原点{@code fragmentX}に対する、元ボックスの描画原点Xです。
	 */
	public final double sourceDrawX(final double fragmentX) {
		if (!this.progression.isVertical()) {
			return fragmentX;
		}
		return fragmentX - (this.sourcePageExtent - this.offset - this.sliceExtent);
	}

	/**
	 * 断片の物理原点{@code fragmentY}に対する、元ボックスの描画原点Yです。
	 */
	public final double sourceDrawY(final double fragmentY) {
		return this.progression.isVertical() ? fragmentY : fragmentY - this.offset;
	}

	/**
	 * 断片の物理矩形です(この矩形の外は描かれません)。
	 */
	public final Rectangle2D.Double fragmentRect(final double x, final double y) {
		return new Rectangle2D.Double(x, y, this.getWidth(), this.getHeight());
	}

	/**
	 * 断片の矩形と既存のクリップを交差させます。
	 * {@code AbstractContainerBox.clip()}と同じ流儀です
	 * ({@link Rectangle2D#createIntersection(Rectangle2D)})。
	 *
	 * @param clip 既存のクリップ({@code null}可)
	 * @param x    断片の物理X
	 * @param y    断片の物理Y
	 * @return 交差したクリップ
	 */
	public final Shape clip(final Shape clip, final double x, final double y) {
		final Rectangle2D.Double newClip = this.fragmentRect(x, y);
		if (clip == null) {
			return newClip;
		}
		return newClip.createIntersection((Rectangle2D) clip);
	}

	/**
	 * 元ボックスは既にレイアウト済みなので何もしません(断片は寸法計算を
	 * 一切行わない——これが「レイアウト計算を変えない」という設計の核)。
	 */
	public void finishLayoutSelf(final IFramedBox containerBox) {
	}

	/**
	 * 元ボックスを再度finishLayoutすると二重計算になるため、子は積みません。
	 */
	public void pushFinishLayoutChildren(final IFramedBox containerBox, final Deque<FinishLayoutStep> worklist) {
	}

	/**
	 * 断片の矩形でクリップし、消費済み量だけずらした位置から元ボックスを
	 * 描きます。元ボックスの描画そのものには一切手を加えません
	 * (=内容はベクターのまま。ラスタライズしない)。
	 */
	public void pushDrawSteps(final PageBox pageBox, final Drawer drawer, final Visitor visitor, final Shape clip,
			final AffineTransform transform, final double contextX, final double contextY, final double x,
			final double y, final Deque<DrawStep> worklist) {
		final Shape sliceClip = this.clip(clip, x, y);
		// 継続断片({@code offset > 0})はartifactとして出す(答申§3)。
		// 唯一の判定はoffset>0で、(a)表示リストへのartifact印、
		// (b)副作用のないVisitor、の二つを同時に切り替える。先頭断片は
		// 通常どおり実Visitor・実内容
		final Drawer sliceDrawer = this.isContinuation() ? drawer.artifactView() : drawer;
		final Visitor sliceVisitor = this.isContinuation() ? ArtifactVisitor.INSTANCE : visitor;
		worklist.push(IBox.drawStep(this.source, pageBox, sliceDrawer, sliceVisitor, sliceClip, transform, contextX,
				contextY, this.sourceDrawX(x), this.sourceDrawY(y)));
	}

	/**
	 * 先頭断片だけが元ボックス全体のテキストを一度返します。継続断片は
	 * 空です(テキスト抽出・読み上げの二重化を防ぐ)。
	 */
	public void pushGetTextSteps(final StringBuilder textBuff, final Deque<GetTextStep> worklist) {
		if (this.isFirstFragment()) {
			worklist.push(IBox.getTextStep(this.source, textBuff));
		}
	}

	/**
	 * 輪郭は見た目の話なので、全断片が元ボックスへ委譲します(座標だけ
	 * ずらす)。テキスト抽出({@link #pushGetTextSteps})とは判定が異なる
	 * ことに注意してください。
	 */
	public void pushTextShapeSteps(final PageBox pageBox, final GeneralPath path, final AffineTransform transform,
			final double x, final double y, final Deque<TextShapeStep> worklist) {
		worklist.push(IBox.textShapeStep(this.source, pageBox, path, transform, this.sourceDrawX(x),
				this.sourceDrawY(y)));
	}

	public String toString() {
		return this.getClass().getName() + "[offset=" + this.offset + ",sliceExtent=" + this.sliceExtent
				+ ",sourcePageExtent=" + this.sourcePageExtent + ",progression=" + this.progression + ",source="
				+ this.source + "]";
	}
}
