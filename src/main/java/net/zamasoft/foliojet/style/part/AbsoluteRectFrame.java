package net.zamasoft.foliojet.style.part;

import java.awt.Shape;

import net.zamasoft.foliojet.style.box.params.RectFrame;
import net.zamasoft.foliojet.style.box.params.WritingMode;
import net.zamasoft.foliojet.style.util.StyleUtils;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: AbsoluteRectFrame.java 1631 2022-05-15 05:43:49Z miyabe $
 */
public class AbsoluteRectFrame {
	public RectFrame frame;

	public AbsoluteInsets margin;

	public AbsoluteInsets padding;

	public AbsoluteRectFrame(RectFrame frame) {
		this(frame, new AbsoluteInsets(), new AbsoluteInsets());
	}

	protected AbsoluteRectFrame(RectFrame frame, AbsoluteInsets margin, AbsoluteInsets padding) {
		this.frame = frame;
		this.margin = margin;
		this.padding = padding;
	}

	public final double getFrameTop() {
		return this.margin.top + this.frame.border.getTop().width + this.padding.top;
	}

	public final double getFrameLeft() {
		return this.margin.left + this.frame.border.getLeft().width + this.padding.left;
	}

	public final double getFrameBottom() {
		return this.padding.bottom + this.frame.border.getBottom().width + this.margin.bottom;
	}

	public final double getFrameRight() {
		return this.padding.right + this.frame.border.getRight().width + this.margin.right;
	}

	public final double getFrameHeight() {
		return this.getFrameTop() + this.getFrameBottom();
	}

	public final double getFrameWidth() {
		return this.getFrameLeft() + this.getFrameRight();
	}

	/**
	 * 行方向の始端側(横書き=左、縦書き=上)のフレーム幅を返します。
	 *
	 * @param flow 軸を決める書字方向
	 * @return 行方向始端側のフレーム幅
	 */
	public final double getFrameLineStart(WritingMode flow) {
		return flow.isVertical() ? this.getFrameTop() : this.getFrameLeft();
	}

	/**
	 * 行方向の終端側(横書き=右、縦書き=下)のフレーム幅を返します。
	 *
	 * @param flow 軸を決める書字方向
	 * @return 行方向終端側のフレーム幅
	 */
	public final double getFrameLineEnd(WritingMode flow) {
		return flow.isVertical() ? this.getFrameBottom() : this.getFrameRight();
	}

	/**
	 * ページ方向の始端側(横書き=上、縦書き=右)のフレーム幅を返します。
	 * 縦書きの内部座標は常に右から左(LRは描画段で反転)です。
	 *
	 * @param flow 軸を決める書字方向
	 * @return ページ方向始端側のフレーム幅
	 */
	public final double getFramePageStart(WritingMode flow) {
		return flow.isVertical() ? this.getFrameRight() : this.getFrameTop();
	}

	/**
	 * ページ方向の終端側(横書き=下、縦書き=左)のフレーム幅を返します。
	 *
	 * @param flow 軸を決める書字方向
	 * @return ページ方向終端側のフレーム幅
	 */
	public final double getFramePageEnd(WritingMode flow) {
		return flow.isVertical() ? this.getFrameLeft() : this.getFrameBottom();
	}

	/**
	 * 行方向のフレーム幅の合計を返します。
	 *
	 * @param flow 軸を決める書字方向
	 * @return 行方向のフレーム幅
	 */
	public final double getFrameLineExtent(WritingMode flow) {
		return flow.isVertical() ? this.getFrameHeight() : this.getFrameWidth();
	}

	/**
	 * ページ方向のフレーム幅の合計を返します。
	 *
	 * @param flow 軸を決める書字方向
	 * @return ページ方向のフレーム幅
	 */
	public final double getFramePageExtent(WritingMode flow) {
		return flow.isVertical() ? this.getFrameWidth() : this.getFrameHeight();
	}

	/**
	 * 行方向のボーダー+パディング幅の合計を返します。
	 *
	 * @param flow 軸を決める書字方向
	 * @return 行方向のボーダー+パディング幅
	 */
	public final double getBorderLineExtent(WritingMode flow) {
		return flow.isVertical() ? this.getBorderHeight() : this.getBorderWidth();
	}

	/**
	 * ページ方向のボーダー+パディング幅の合計を返します。
	 *
	 * @param flow 軸を決める書字方向
	 * @return ページ方向のボーダー+パディング幅
	 */
	public final double getBorderPageExtent(WritingMode flow) {
		return flow.isVertical() ? this.getBorderWidth() : this.getBorderHeight();
	}

	public final double getBorderHeight() {
		return this.frame.border.getTop().width + this.padding.top + this.frame.border.getBottom().width
				+ this.padding.bottom;
	}

	public final double getBorderWidth() {
		return this.frame.border.getLeft().width + this.padding.left + this.frame.border.getRight().width
				+ this.padding.right;
	}

	public boolean isVisible() {
		return this.frame.isVisible();
	}

	public void draw(GC gc, double x, double y, double width, double height, Shape textClip) throws GraphicsException {
		assert !StyleUtils.isNone(x) : "Undefined x";
		assert !StyleUtils.isNone(y) : "Undefined y";
		assert !StyleUtils.isNone(width) : "Undefined width";
		assert !StyleUtils.isNone(height) : "Undefined height";
		x += this.margin.left;
		y += this.margin.top;
		width -= this.margin.getFrameWidth();
		height -= this.margin.getFrameHeight();
		this.frame.background.draw(gc, x, y, width, height, this.frame.border, this.frame.padding, textClip);
		this.frame.border.draw(gc, x, y, width, height);
	}

	public AbsoluteRectFrame cut(boolean top, boolean right, boolean bottom, boolean left) {
		return new AbsoluteRectFrame(this.frame.cut(top, right, bottom, left),
				this.margin.cut(top, right, bottom, left), this.padding.cut(top, right, bottom, left));
	}

	public String toString() {
		return super.toString() + "[frame=" + this.frame + ",margin=" + this.margin + ",padding=" + this.padding + "]";
	}
}
