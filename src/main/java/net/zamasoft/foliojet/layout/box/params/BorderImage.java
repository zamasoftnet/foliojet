package net.zamasoft.foliojet.layout.box.params;

import net.zamasoft.foliojet.css.value.PaintValue;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * 計算済みの {@code border-image-*} を描画層へ渡すパラメータです。
 *
 * <p>現時点では値の受け渡しまでで、9スライス描画は未実装です。</p>
 */
public final class BorderImage {
	public sealed interface Source permits ImageSource, PaintSource {
	}

	public record ImageSource(Image image) implements Source {
		public ImageSource {
			if (image == null) {
				throw new NullPointerException("image");
			}
		}
	}

	public record PaintSource(PaintValue paint) implements Source {
		public PaintSource {
			if (paint == null) {
				throw new NullPointerException("paint");
			}
		}
	}

	/** 数値の解釈。MIXEDは {@code absolute + ratio * 基準寸法} です。 */
	public enum Unit {
		NUMBER, ABSOLUTE, RELATIVE, MIXED, AUTO
	}

	public record Component(double absolute, double ratio, Unit unit) {
		public Component {
			if (unit == null) {
				throw new NullPointerException("unit");
			}
		}

		public static Component number(double value) {
			return new Component(value, 0, Unit.NUMBER);
		}

		public static Component absolute(double value) {
			return new Component(value, 0, Unit.ABSOLUTE);
		}

		public static Component relative(double ratio) {
			return new Component(0, ratio, Unit.RELATIVE);
		}

		public static Component mixed(double absolute, double ratio) {
			return new Component(absolute, ratio, Unit.MIXED);
		}

		public static Component auto() {
			return new Component(0, 0, Unit.AUTO);
		}
	}

	public record Quad(Component top, Component right, Component bottom, Component left) {
		public Quad {
			if (top == null || right == null || bottom == null || left == null) {
				throw new NullPointerException("quad");
			}
		}

	}

	public enum Repeat {
		STRETCH, REPEAT, ROUND, SPACE
	}

	private final Source source;
	private final Quad slice;
	private final boolean fill;
	private final Quad width;
	private final Quad outset;
	private final Repeat horizontalRepeat;
	private final Repeat verticalRepeat;
	private final boolean top, right, bottom, left;

	public BorderImage(Source source, Quad slice, boolean fill, Quad width, Quad outset, Repeat horizontalRepeat,
			Repeat verticalRepeat) {
		this(source, slice, fill, width, outset, horizontalRepeat, verticalRepeat, true, true, true, true);
	}

	private BorderImage(Source source, Quad slice, boolean fill, Quad width, Quad outset, Repeat horizontalRepeat,
			Repeat verticalRepeat, boolean top, boolean right, boolean bottom, boolean left) {
		if (source == null || slice == null || width == null || outset == null || horizontalRepeat == null
				|| verticalRepeat == null) {
			throw new NullPointerException();
		}
		this.source = source;
		this.slice = slice;
		this.fill = fill;
		this.width = width;
		this.outset = outset;
		this.horizontalRepeat = horizontalRepeat;
		this.verticalRepeat = verticalRepeat;
		this.top = top;
		this.right = right;
		this.bottom = bottom;
		this.left = left;
	}

	public Source getSource() {
		return this.source;
	}

	public Quad getSlice() {
		return this.slice;
	}

	public boolean isFill() {
		return this.fill;
	}

	public Quad getWidth() {
		return this.width;
	}

	public Quad getOutset() {
		return this.outset;
	}

	public Repeat getHorizontalRepeat() {
		return this.horizontalRepeat;
	}

	public Repeat getVerticalRepeat() {
		return this.verticalRepeat;
	}

	public boolean hasTop() {
		return this.top;
	}

	public boolean hasRight() {
		return this.right;
	}

	public boolean hasBottom() {
		return this.bottom;
	}

	public boolean hasLeft() {
		return this.left;
	}

	public BorderImage cut(boolean top, boolean right, boolean bottom, boolean left) {
		if (top && right && bottom && left) {
			return this;
		}
		return new BorderImage(this.source, this.slice, this.fill, this.width, this.outset, this.horizontalRepeat,
				this.verticalRepeat, this.top && top, this.right && right, this.bottom && bottom, this.left && left);
	}
}
