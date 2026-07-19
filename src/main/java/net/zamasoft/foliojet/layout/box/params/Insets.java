package net.zamasoft.foliojet.layout.box.params;

public class Insets {
	public static final Insets NULL_INSETS = new Insets(0, 0, 0, 0, 0, 0, 0, 0, LengthType.ABSOLUTE,
			LengthType.ABSOLUTE, LengthType.ABSOLUTE, LengthType.ABSOLUTE);

	public static final Insets AUTO_INSETS = new Insets(0, 0, 0, 0, 0, 0, 0, 0, LengthType.AUTO, LengthType.AUTO,
			LengthType.AUTO, LengthType.AUTO);

	private final double top;
	private final double right;
	private final double bottom;
	private final double left;
	/** MIXED(calc()の絶対+割合混在)の場合のみ意味を持つ割合成分。それ以外は常に0。 */
	private final double topRatio;
	private final double rightRatio;
	private final double bottomRatio;
	private final double leftRatio;
	private final byte flags;

	public static Insets create(double top, double right, double bottom, double left, LengthType topType,
			LengthType rightType, LengthType bottomType, LengthType leftType) {
		return create(top, 0, right, 0, bottom, 0, left, 0, topType, rightType, bottomType, leftType);
	}

	/** 各辺がMIXEDの場合の割合成分付きの生成。 */
	public static Insets create(double top, double topRatio, double right, double rightRatio, double bottom,
			double bottomRatio, double left, double leftRatio, LengthType topType, LengthType rightType,
			LengthType bottomType, LengthType leftType) {
		if (topType == LengthType.AUTO && rightType == LengthType.AUTO && bottomType == LengthType.AUTO
				&& leftType == LengthType.AUTO) {
			return AUTO_INSETS;
		}
		if (topType != LengthType.AUTO && rightType != LengthType.AUTO && bottomType != LengthType.AUTO
				&& leftType != LengthType.AUTO && topType != LengthType.MIXED && rightType != LengthType.MIXED
				&& bottomType != LengthType.MIXED && leftType != LengthType.MIXED && top == 0 && right == 0
				&& bottom == 0 && left == 0) {
			return NULL_INSETS;
		}
		return new Insets(top, topRatio, right, rightRatio, bottom, bottomRatio, left, leftRatio, topType, rightType,
				bottomType, leftType);
	}

	private Insets(double top, double topRatio, double right, double rightRatio, double bottom, double bottomRatio,
			double left, double leftRatio, LengthType topType, LengthType rightType, LengthType bottomType,
			LengthType leftType) {
		this.top = top;
		this.right = right;
		this.bottom = bottom;
		this.left = left;
		this.topRatio = topRatio;
		this.rightRatio = rightRatio;
		this.bottomRatio = bottomRatio;
		this.leftRatio = leftRatio;
		this.flags = (byte) (topType.ordinal() | (rightType.ordinal() << 2) | (bottomType.ordinal() << 4)
				| (leftType.ordinal() << 6));
	}

	public LengthType getTopType() {
		return LengthType.VALUES[this.flags & 3];
	}

	public LengthType getRightType() {
		return LengthType.VALUES[(this.flags >> 2) & 3];
	}

	public LengthType getBottomType() {
		return LengthType.VALUES[(this.flags >> 4) & 3];
	}

	public LengthType getLeftType() {
		return LengthType.VALUES[(this.flags >> 6) & 3];
	}

	public double getTopRatio() {
		return this.topRatio;
	}

	public double getRightRatio() {
		return this.rightRatio;
	}

	public double getBottomRatio() {
		return this.bottomRatio;
	}

	public double getLeftRatio() {
		return this.leftRatio;
	}

	public boolean isNull() {
		// MIXEDはcreate()が絶対・割合いずれか0なら単純型へ縮退させるため、
		// 実際にMIXED型である値は常に両成分非0=非ゼロと分かっている。
		return (this.getTopType() != LengthType.AUTO && this.getTopType() != LengthType.MIXED && this.getTop() == 0)
				&& (this.getRightType() != LengthType.AUTO && this.getRightType() != LengthType.MIXED
						&& this.getRight() == 0)
				&& (this.getBottomType() != LengthType.AUTO && this.getBottomType() != LengthType.MIXED
						&& this.getBottom() == 0)
				&& (this.getLeftType() != LengthType.AUTO && this.getLeftType() != LengthType.MIXED
						&& this.getLeft() == 0);
	}

	public double getTop() {
		return top;
	}

	public double getRight() {
		return right;
	}

	public double getBottom() {
		return bottom;
	}

	public double getLeft() {
		return left;
	}

	public String toString() {
		StringBuilder buff = new StringBuilder();
		buff.append("[top=");
		append(buff, this.getTopType(), this.top, this.topRatio);
		buff.append(",right=");
		append(buff, this.getRightType(), this.right, this.rightRatio);
		buff.append(",bottom=");
		append(buff, this.getBottomType(), this.bottom, this.bottomRatio);
		buff.append(",left=");
		append(buff, this.getLeftType(), this.left, this.leftRatio);
		buff.append(']');
		return buff.toString();
	}

	private static void append(StringBuilder buff, LengthType type, double value, double ratio) {
		switch (type) {
		case ABSOLUTE:
			buff.append(value);
			break;
		case RELATIVE:
			buff.append(value * 100).append('%');
			break;
		case MIXED:
			buff.append(value).append('+').append(ratio * 100).append('%');
			break;
		case AUTO:
			buff.append("auto");
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public Insets cut(boolean top, boolean right, boolean bottom, boolean left) {
		Insets insets = this.isNull() ? this
				: Insets.create(top ? this.getTop() : 0, top ? this.topRatio : 0, right ? this.getRight() : 0,
						right ? this.rightRatio : 0, bottom ? this.getBottom() : 0, bottom ? this.bottomRatio : 0,
						left ? this.getLeft() : 0, left ? this.leftRatio : 0, top ? this.getTopType() : LengthType.ABSOLUTE,
						right ? this.getRightType() : LengthType.ABSOLUTE,
						bottom ? this.getBottomType() : LengthType.ABSOLUTE,
						left ? this.getLeftType() : LengthType.ABSOLUTE);
		return insets;
	}
}
