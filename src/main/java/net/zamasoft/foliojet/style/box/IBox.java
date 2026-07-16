package net.zamasoft.foliojet.style.box;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

import net.zamasoft.foliojet.style.box.impl.PageBox;
import net.zamasoft.foliojet.style.box.params.Params;
import net.zamasoft.foliojet.style.box.params.Pos;
import net.zamasoft.foliojet.style.box.params.WritingMode;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.visitor.Visitor;

public interface IBox {

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
	 * ページ方向の幅を確定します。
	 * 
	 * @param containerBox
	 */
	public void finishLayout(IFramedBox containerBox);

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