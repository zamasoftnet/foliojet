package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.foliojet.layout.util.BorderRenderer;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: RectBorder.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class RectBorder {
	public static final RectBorder NONE_RECT_BORDER = new RectBorder(Border.NONE_BORDER, Border.NONE_BORDER,
			Border.NONE_BORDER, Border.NONE_BORDER, Radius.ZERO_RADIUS, Radius.ZERO_RADIUS, Radius.ZERO_RADIUS,
			Radius.ZERO_RADIUS, null);

	private final Border top, right, bottom, left;

	/** 計算済みborder-image。sourceがnoneまたは画像取得失敗ならnull。 */
	private final BorderImage borderImage;

	public static class Radius {
		public static final Radius ZERO_RADIUS = new Radius(0, 0);

		public final double hr, vr;

		/**
		 * パーセント半径の比率成分(border-radius:50%等)。水平はボックス幅、
		 * 垂直はボックス高さに掛ける(CSS Backgrounds §5.1)。寸法はレイアウト
		 * 確定まで分からないため、描画側が{@link #resolve}で絶対値へ解決する。
		 */
		public final double hrRatio, vrRatio;

		public static Radius create(double hr, double vr) {
			if (hr == 0 && vr == 0) {
				return ZERO_RADIUS;
			}
			return new Radius(hr, vr);
		}

		public static Radius create(double hr, double vr, double hrRatio, double vrRatio) {
			if (hrRatio == 0 && vrRatio == 0) {
				return create(hr, vr);
			}
			return new Radius(hr, vr, hrRatio, vrRatio);
		}

		public Radius(double hr, double vr) {
			this(hr, vr, 0, 0);
		}

		private Radius(double hr, double vr, double hrRatio, double vrRatio) {
			this.hr = hr;
			this.vr = vr;
			this.hrRatio = hrRatio;
			this.vrRatio = vrRatio;
		}

		/** ボックス寸法でパーセント成分を絶対値へ解決した半径を返します。 */
		public Radius resolve(double width, double height) {
			if (this.hrRatio == 0 && this.vrRatio == 0) {
				return this;
			}
			return create(this.hr + width * this.hrRatio, this.vr + height * this.vrRatio);
		}

		public boolean equals(Object o) {
			final Radius r = (Radius) o;
			return r.hr == this.hr && r.vr == this.vr && r.hrRatio == this.hrRatio && r.vrRatio == this.vrRatio;
		}
	}

	private final Radius topLeft, topRight, bottomLeft, bottomRight;

	public static RectBorder create(Border top, Border right, Border bottom, Border left, Radius topLeft,
			Radius topRight, Radius bottomLeft, Radius bottomRight) {
		return create(top, right, bottom, left, topLeft, topRight, bottomLeft, bottomRight, null);
	}

	public static RectBorder create(Border top, Border right, Border bottom, Border left, Radius topLeft,
			Radius topRight, Radius bottomLeft, Radius bottomRight, BorderImage borderImage) {
		if (top.style == Border.NONE && right.style == Border.NONE && bottom.style == Border.NONE
				&& left.style == Border.NONE && topLeft == Radius.ZERO_RADIUS && topRight == Radius.ZERO_RADIUS
				&& bottomLeft == Radius.ZERO_RADIUS && bottomRight == Radius.ZERO_RADIUS && borderImage == null) {
			return RectBorder.NONE_RECT_BORDER;
		}
		return new RectBorder(top, right, bottom, left, topLeft, topRight, bottomLeft, bottomRight, borderImage);
	}

	private RectBorder(Border top, Border right, Border bottom, Border left, Radius topLeft, Radius topRight,
			Radius bottomLeft, Radius bottomRight, BorderImage borderImage) {
		this.top = top;
		this.right = right;
		this.bottom = bottom;
		this.left = left;
		this.topLeft = topLeft;
		this.topRight = topRight;
		this.bottomLeft = bottomLeft;
		this.bottomRight = bottomRight;
		this.borderImage = borderImage;
	}

	public Border getTop() {
		return this.top;
	}

	public Border getRight() {
		return this.right;
	}

	public Border getBottom() {
		return this.bottom;
	}

	public Border getLeft() {
		return this.left;
	}

	public Radius getTopLeft() {
		return this.topLeft;
	}

	public Radius getTopRight() {
		return this.topRight;
	}

	public Radius getBottomLeft() {
		return this.bottomLeft;
	}

	public Radius getBottomRight() {
		return this.bottomRight;
	}

	public BorderImage getBorderImage() {
		return this.borderImage;
	}

	public void draw(GC gc, double x, double y, double width, double height) throws GraphicsException {
		BorderRenderer.INSTANCE.drawRectBorder(gc, this, x, y, width, height);
	}

	public double getFrameWidth() {
		return this.getLeft().width + this.getRight().width;
	}

	public double getFrameHeight() {
		return this.getTop().width + this.getBottom().width;
	}

	public boolean isVisible() {
		// 境界画像だけが指定された箱(border-style:noneのまま border-image を
		// 出す書き方)は、4辺のスタイルを見るだけでは不可視と判定されて
		// 描画そのものが省かれてしまう
		return this.borderImage != null || this.getTop().isVisible() || this.getRight().isVisible()
				|| this.getBottom().isVisible() || this.getLeft().isVisible();
	}

	public boolean isNull() {
		return this.getTop().isNull() && this.getRight().isNull() && this.getBottom().isNull()
				&& this.getLeft().isNull();
	}

	public boolean isRounded() {
		return this.topLeft != Radius.ZERO_RADIUS || this.topRight != Radius.ZERO_RADIUS
				|| this.bottomLeft != Radius.ZERO_RADIUS || this.bottomRight != Radius.ZERO_RADIUS;
	}

	public RectBorder cut(boolean top, boolean right, boolean bottom, boolean left) {
		final RectBorder newBorder = RectBorder.create(top ? this.getTop() : Border.NONE_BORDER,
				right ? this.getRight() : Border.NONE_BORDER, bottom ? this.getBottom() : Border.NONE_BORDER,
				left ? this.getLeft() : Border.NONE_BORDER, (top && left) ? this.getTopLeft() : Radius.ZERO_RADIUS,
				(top && right) ? this.getTopRight() : Radius.ZERO_RADIUS,
				(bottom && left) ? this.getBottomLeft() : Radius.ZERO_RADIUS,
				(bottom && right) ? this.getBottomRight() : Radius.ZERO_RADIUS,
				this.borderImage == null ? null : this.borderImage.cut(top, right, bottom, left));
		return newBorder;
	}

	public String toString() {
		return "[top=" + this.getTop() + ",left=" + this.getLeft() + ",bottom=" + this.getBottom() + ",right="
				+ this.getRight() + "]";
	}
}
