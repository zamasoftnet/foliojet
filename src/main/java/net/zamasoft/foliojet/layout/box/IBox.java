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
	 * 描画可能なコンテンツを追加します。
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
	public void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y);

	/**
	 * 内部のテキストを返します。
	 */
	public void getText(StringBuilder textBuff);

	public void textShape(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double d);
}