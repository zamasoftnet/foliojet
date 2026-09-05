package net.zamasoft.foliojet.css.value;

/**
 * {@code paint-order}の値です。省略された要素は通常順
 * ({@code fill stroke markers})で末尾へ補います。
 */
public final class PaintOrderValue implements Value {
	public static final byte FILL = 1;
	public static final byte STROKE = 2;
	public static final byte MARKERS = 3;

	public static final PaintOrderValue NORMAL = new PaintOrderValue(true,
			new byte[] { FILL, STROKE, MARKERS }, 0);

	private final boolean normal;
	private final byte[] order;
	private final int declaredCount;

	public static PaintOrderValue create(final byte[] declared, final int count) {
		final byte[] order = new byte[3];
		System.arraycopy(declared, 0, order, 0, count);
		int next = count;
		for (final byte value : new byte[] { FILL, STROKE, MARKERS }) {
			boolean present = false;
			for (int i = 0; i < count; ++i) {
				present |= declared[i] == value;
			}
			if (!present) {
				order[next++] = value;
			}
		}
		return new PaintOrderValue(false, order, count);
	}

	private PaintOrderValue(final boolean normal, final byte[] order, final int declaredCount) {
		this.normal = normal;
		this.order = order;
		this.declaredCount = declaredCount;
	}

	public boolean isNormal() {
		return this.normal;
	}

	/** ストロークを塗りより先に描く指定ならtrueです。 */
	public boolean isStrokeBeforeFill() {
		for (final byte value : this.order) {
			if (value == STROKE) {
				return true;
			}
			if (value == FILL) {
				return false;
			}
		}
		throw new IllegalStateException();
	}

	@Override
	public String toString() {
		if (this.normal) {
			return "normal";
		}
		final StringBuilder buff = new StringBuilder();
		for (int i = 0; i < this.declaredCount; ++i) {
			if (i > 0) {
				buff.append(' ');
			}
			buff.append(switch (this.order[i]) {
			case FILL -> "fill";
			case STROKE -> "stroke";
			case MARKERS -> "markers";
			default -> throw new IllegalStateException();
			});
		}
		return buff.toString();
	}
}
