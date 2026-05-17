package jp.cssj.homare.style.box.params;

public class Insets {
	public static final short TYPE_ABSOLUTE = 1;
	public static final short TYPE_RELATIVE = 2;
	public static final short TYPE_AUTO = 3;

	public static final Insets NULL_INSETS = new Insets(0, 0, 0, 0, TYPE_ABSOLUTE, TYPE_ABSOLUTE, TYPE_ABSOLUTE,
			TYPE_ABSOLUTE);

	public static final Insets AUTO_INSETS = new Insets(0, 0, 0, 0, TYPE_AUTO, TYPE_AUTO, TYPE_AUTO, TYPE_AUTO);

	private final double top;
	private final double right;
	private final double bottom;
	private final double left;
	private final byte flags;

	public static Insets create(double top, double right, double bottom, double left, short topType, short rightType,
			short bottomType, short leftType) {
		if (topType == TYPE_AUTO && rightType == TYPE_AUTO && bottomType == TYPE_AUTO && leftType == TYPE_AUTO) {
			return AUTO_INSETS;
		}
		if (topType != TYPE_AUTO && rightType != TYPE_AUTO && bottomType != TYPE_AUTO && leftType != TYPE_AUTO
				&& top == 0 && right == 0 && bottom == 0 && left == 0) {
			return NULL_INSETS;
		}
		return new Insets(top, right, bottom, left, topType, rightType, bottomType, leftType);
	}

	private Insets(double top, double right, double bottom, double left, short topType, short rightType,
			short bottomType, short leftType) {
		this.top = top;
		this.right = right;
		this.bottom = bottom;
		this.left = left;
		this.flags = (byte) (topType | (rightType << 2) | (bottomType << 4) | (leftType << 6));
	}

	public short getTopType() {
		return (short) (this.flags & 3);
	}

	public short getRightType() {
		return (short) ((this.flags >> 2) & 3);
	}

	public short getBottomType() {
		return (short) ((this.flags >> 4) & 3);
	}

	public short getLeftType() {
		return (short) ((this.flags >> 6) & 3);
	}

	public boolean isNull() {
		return (this.getTopType() != TYPE_AUTO && this.getTop() == 0)
				&& (this.getRightType() != TYPE_AUTO && this.getRight() == 0)
				&& (this.getBottomType() != TYPE_AUTO && this.getBottom() == 0)
				&& (this.getLeftType() != TYPE_AUTO && this.getLeft() == 0);
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
		StringBuffer buff = new StringBuffer();
		buff.append("[top=");
		switch (this.getTopType()) {
		case TYPE_ABSOLUTE:
			buff.append(this.top);
			break;
		case TYPE_RELATIVE:
			buff.append(this.top * 100).append('%');
			break;
		case TYPE_AUTO:
			buff.append("auto");
			break;
		default:
			throw new IllegalStateException();
		}
		buff.append(",right=");
		switch (this.getRightType()) {
		case TYPE_ABSOLUTE:
			buff.append(this.right);
			break;
		case TYPE_RELATIVE:
			buff.append(this.right * 100).append('%');
			break;
		case TYPE_AUTO:
			buff.append("auto");
			break;
		default:
			throw new IllegalStateException();
		}
		buff.append(",bottom=");
		switch (this.getBottomType()) {
		case TYPE_ABSOLUTE:
			buff.append(this.bottom);
			break;
		case TYPE_RELATIVE:
			buff.append(this.bottom * 100).append('%');
			break;
		case TYPE_AUTO:
			buff.append("auto");
			break;
		default:
			throw new IllegalStateException();
		}
		buff.append(",left=");
		switch (this.getLeftType()) {
		case TYPE_ABSOLUTE:
			buff.append(this.left);
			break;
		case TYPE_RELATIVE:
			buff.append(this.left * 100).append('%');
			break;
		case TYPE_AUTO:
			buff.append("auto");
			break;
		default:
			throw new IllegalStateException();
		}
		buff.append(']');
		return buff.toString();
	}

	public Insets cut(boolean top, boolean right, boolean bottom, boolean left) {
		Insets insets = this.isNull() ? this
				: Insets.create(top ? this.getTop() : 0, right ? this.getRight() : 0, bottom ? this.getBottom() : 0,
						left ? this.getLeft() : 0, top ? this.getTopType() : Insets.TYPE_ABSOLUTE,
						right ? this.getRightType() : Insets.TYPE_ABSOLUTE,
						bottom ? this.getBottomType() : Insets.TYPE_ABSOLUTE,
						left ? this.getLeftType() : Insets.TYPE_ABSOLUTE);
		return insets;
	}
}
