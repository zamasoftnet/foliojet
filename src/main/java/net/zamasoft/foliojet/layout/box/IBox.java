package net.zamasoft.foliojet.layout.box;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;
import java.util.ArrayDeque;
import java.util.Deque;

import net.zamasoft.foliojet.layout.box.impl.PageBox;
import net.zamasoft.foliojet.layout.box.params.Params;
import net.zamasoft.foliojet.layout.box.params.Pos;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.visitor.Visitor;

public interface IBox {

	/**
	 * この内容を生んだ LayoutSource のイベントIDを返します
	 * (SourceAnchor。記録時に一度だけ付与され不変。断片・未記録は -1)。
	 */
	public long getSourceAnchor();

	/**
	 * SourceAnchor を付与します(記録時・再生時のドライバ専用。
	 * 一度だけ)。
	 */
	public void setSourceAnchor(long id);

	/**
	 * ボックスのタイプを返します。
	 *
	 * @return
	 */
	public BoxType getType();

	public BoxSubtype getSubtype();

	/**
	 * 内容のパラメータを返します。
	 * 
	 * @return
	 */
	public Params getParams();

	/**
	 * 位置のパラメータを返します。
	 * 
	 * @return
	 */
	public Pos getPos();

	/**
	 * ボックスの現在の幅を返します。
	 * 
	 * @return
	 */
	public double getWidth();

	/**
	 * ボックスの現在の高さを返します。
	 * 
	 * @return
	 */
	public double getHeight();

	/**
	 * ボックスの現在の内部幅を返します。
	 *
	 * @return
	 */
	public double getInnerWidth();

	/**
	 * ボックスの現在の内部高さを返します。
	 *
	 * @return
	 */
	public double getInnerHeight();

	/**
	 * 与えられた書字方向での行方向の寸法を返します(横書き=幅、縦書き=高さ)。
	 *
	 * @param flow 軸を決める書字方向(通常は包含ブロックのもの)
	 * @return 行方向の寸法
	 */
	public default double getLineExtent(WritingMode flow) {
		return flow.isVertical() ? this.getHeight() : this.getWidth();
	}

	/**
	 * 与えられた書字方向でのページ方向の寸法を返します(横書き=高さ、縦書き=幅)。
	 *
	 * @param flow 軸を決める書字方向(通常は包含ブロックのもの)
	 * @return ページ方向の寸法
	 */
	public default double getPageExtent(WritingMode flow) {
		return flow.isVertical() ? this.getWidth() : this.getHeight();
	}

	/**
	 * 与えられた書字方向での行方向の内部寸法を返します。
	 *
	 * @param flow 軸を決める書字方向
	 * @return 行方向の内部寸法
	 */
	public default double getInnerLineExtent(WritingMode flow) {
		return flow.isVertical() ? this.getInnerHeight() : this.getInnerWidth();
	}

	/**
	 * 与えられた書字方向でのページ方向の内部寸法を返します。
	 *
	 * @param flow 軸を決める書字方向
	 * @return ページ方向の内部寸法
	 */
	public default double getInnerPageExtent(WritingMode flow) {
		return flow.isVertical() ? this.getInnerWidth() : this.getInnerHeight();
	}

	/**
	 * ページ方向の幅を確定します(2026-07-20、反復化——ARCHITECTURE.md
	 * 不変条件6。旧実装はポリモーフィックな相互再帰で、深いネスト文書
	 * (1000段超)でStackOverflowErrorを起こしていた——実文書=法令ページで
	 * 確認済み)。JVMコールスタックの代わりに明示的な{@link Deque}を
	 * ワークリストとして使う反復DFSに置き換えた。個々のボックス型は
	 * {@link #finishLayoutSelf}(局所処理)と
	 * {@link #pushFinishLayoutChildren}(子の登録)だけを実装すればよく、
	 * このdefaultメソッド自体を書き換える必要はない。
	 *
	 * @param containerBox
	 */
	public default void finishLayout(IFramedBox containerBox) {
		final Deque<FinishLayoutStep> worklist = new ArrayDeque<>();
		worklist.push(IBox.step(this, containerBox));
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
	}

	/**
	 * {@code box}の{@link #finishLayoutSelf}実行後に
	 * {@link #pushFinishLayoutChildren}を行う、1つの{@link FinishLayoutStep}
	 * を作ります(IBoxの子をワークリストへ積む共通ヘルパー)。
	 */
	public static FinishLayoutStep step(final IBox box, final IFramedBox containerBox) {
		return worklist -> {
			box.finishLayoutSelf(containerBox);
			box.pushFinishLayoutChildren(containerBox, worklist);
		};
	}

	/**
	 * このボックス自身の局所処理(位置・寸法の確定等、子を持たない部分)
	 * だけを行います。子の処理は{@link #pushFinishLayoutChildren}が
	 * 別途担当します。
	 */
	public void finishLayoutSelf(IFramedBox containerBox);

	/**
	 * 子ボックス(または{@code Container})の処理ステップを{@code worklist}
	 * へ積みます。子を複数持つ場合は、元の再帰と同じ走査順になるよう
	 * **逆順**でpushしてください(スタックとして使うため)。
	 */
	public void pushFinishLayoutChildren(IFramedBox containerBox, Deque<FinishLayoutStep> worklist);

	/**
	 * 描画可能なコンテンツを追加します(2026-07-20、反復化——finishLayoutと
	 * 同じ理由。深いネストでのStackOverflowErrorを避けるため、明示的な
	 * {@link Deque}をワークリストとして使う反復DFSに置き換えた)。
	 *
	 * <p>
	 * 与えられる座標系はページの左上を基点とします。
	 * </p>
	 *
	 * @param pageBox
	 *            TODO
	 * @param drawer
	 * @param clip
	 * @param transform
	 *            TODO
	 * @param contextX
	 *            TODO
	 * @param contextY
	 *            TODO
	 */
	public default void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y) {
		final Deque<DrawStep> worklist = new ArrayDeque<>();
		worklist.push(IBox.drawStep(this, pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y));
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
	}

	/**
	 * {@code box}の{@link #pushDrawSteps}を実行する1つの{@link DrawStep}を
	 * 作ります(子ボックスの描画をworklistへ積む共通ヘルパー)。
	 */
	public static DrawStep drawStep(final IBox box, final PageBox pageBox, final Drawer drawer, final Visitor visitor,
			final Shape clip, final AffineTransform transform, final double contextX, final double contextY,
			final double x, final double y) {
		return worklist -> box.pushDrawSteps(pageBox, drawer, visitor, clip, transform, contextX, contextY, x, y,
				worklist);
	}

	/**
	 * このボックス(とその子孫)の描画手順を{@code worklist}へ積みます。
	 * 元の{@code draw}の実行順を保つため、局所的な描画(このボックス自身の
	 * 背景・枠・テキストラン等)と子ボックスの描画手順を、実行順のまま
	 * 組み立てたうえで**逆順**でpushしてください(スタックとして使うため。
	 * 局所描画が子の描画と交互に現れる場合は、局所描画もその場で即座に
	 * 実行するのではなく、正しい位置に挿し込まれるステップとして
	 * pushする必要があります——{@link AbstractTextBox}参照)。
	 */
	public void pushDrawSteps(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y, Deque<DrawStep> worklist);

	/**
	 * 内部のテキストを返します(2026-07-20、反復化——drawと同じ理由)。
	 */
	public default void getText(StringBuilder textBuff) {
		final Deque<GetTextStep> worklist = new ArrayDeque<>();
		worklist.push(IBox.getTextStep(this, textBuff));
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
	}

	/**
	 * {@code box}の{@link #pushGetTextSteps}を実行する1つの
	 * {@link GetTextStep}を作ります。
	 */
	public static GetTextStep getTextStep(final IBox box, final StringBuilder textBuff) {
		return worklist -> box.pushGetTextSteps(textBuff, worklist);
	}

	/**
	 * このボックス(とその子孫)のテキスト抽出手順を{@code worklist}へ
	 * 積みます。{@link #pushDrawSteps}と同じ規約(元の走査順を保つため
	 * **逆順**でpush)に従ってください。
	 */
	public void pushGetTextSteps(StringBuilder textBuff, Deque<GetTextStep> worklist);

	/**
	 * クリップ用の輪郭を{@code path}へ積み上げます(2026-07-20、反復化——drawと
	 * 同じ理由)。
	 */
	public default void textShape(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double d) {
		final Deque<TextShapeStep> worklist = new ArrayDeque<>();
		worklist.push(IBox.textShapeStep(this, pageBox, path, transform, x, d));
		while (!worklist.isEmpty()) {
			worklist.pop().run(worklist);
		}
	}

	/**
	 * {@code box}の{@link #pushTextShapeSteps}を実行する1つの
	 * {@link TextShapeStep}を作ります。
	 */
	public static TextShapeStep textShapeStep(final IBox box, final PageBox pageBox, final GeneralPath path,
			final AffineTransform transform, final double x, final double y) {
		return worklist -> box.pushTextShapeSteps(pageBox, path, transform, x, y, worklist);
	}

	/**
	 * このボックス(とその子孫)の輪郭手順を{@code worklist}へ積みます。
	 * {@link #pushDrawSteps}と同じ規約(元の走査順を保つため**逆順**で
	 * push)に従ってください。
	 */
	public void pushTextShapeSteps(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double y,
			Deque<TextShapeStep> worklist);
}