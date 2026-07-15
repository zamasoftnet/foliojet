package net.zamasoft.foliojet.style.box.params;

public class Insets {
	public static final Insets NULL_INSETS = new Insets(0, 0, 0, 0, LengthType.ABSOLUTE, LengthType.ABSOLUTE,
			LengthType.ABSOLUTE, LengthType.ABSOLUTE);

	public static final Insets AUTO_INSETS = new Insets(0, 0, 0, 0, LengthType.AUTO, LengthType.AUTO, LengthType.AUTO,
			LengthType.AUTO);

	private final double top;
	private final double right;
	private final double bottom;
	private final double left;
	private final byte flags;

	public static Insets create(double top, double right, double bottom, double left, LengthType topType,
			LengthType rightType, LengthType bottomType, LengthType leftType) {
		if (topType == LengthType.AUTO && rightType == LengthType.AUTO && bottomType == LengthType.AUTO
				&& leftType == LengthType.AUTO) {
			return AUTO_INSETS;
		}
		if (topType != LengthType.AUTO && rightType != LengthType.AUTO && bottomType != LengthType.AUTO
				&& leftType != LengthType.AUTO && top == 0 && right == 0 && bottom == 0 && left == 0) {
			return NULL_INSETS;
		}
		return new Insets(top, right, bottom, left, topType, rightType, bottomType, leftType);
	}

	private Insets(double top, double right, double bottom, double left, LengthType topType, LengthType rightType,
			LengthType bottomType, LengthType leftType) {
		this.top = top;
		this.right = right;
		this.bottom = bottom;
		this.left = left;
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

	public boolean isNull() {
		return (this.getTopType() != LengthType.AUTO && this.getTop() == 0)
				&& (this.getRightType() != LengthType.AUTO && this.getRight() == 0)
				&& (this.getBottomType() != LengthType.AUTO && this.getBottom() == 0)
				&& (this.getLeftType() != LengthType.AUTO && this.getLeft() == 0);
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
		append(buff, this.getTopType(), this.top);
		buff.append(",right=");
		append(buff, this.getRightType(), this.right);
		buff.append(",bottom=");
		append(buff, this.getBottomType(), this.bottom);
		buff.append(",left=");
		append(buff, this.getLeftType(), this.left);
		buff.append(']');
		return buff.toString();
	}

	private static void append(StringBuilder buff, LengthType type, double value) {
		switch (type) {
		case ABSOLUTE:
			buff.append(value);
			break;
		case RELATIVE:
			buff.append(value * 100).append('%');
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
				: Insets.create(top ? this.getTop() : 0, right ? this.getRight() : 0, bottom ? this.getBottom() : 0,
						left ? this.getLeft() : 0, top ? this.getTopType() : LengthType.ABSOLUTE,
						right ? this.getRightType() : LengthType.ABSOLUTE,
						bottom ? this.getBottomType() : LengthType.ABSOLUTE,
						left ? this.getLeftType() : LengthType.ABSOLUTE);
		return insets;
	}
}
