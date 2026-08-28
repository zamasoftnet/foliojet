package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code shape-image-threshold}です(css-shapes-1 §4.2、2026-08-29新設)。
 *
 * <p>
 * {@code <number>}。{@code shape-outside: url()}の画像から形状を
 * 抽出するときの不透明度の閾値(この値<b>より大きい</b>画素が形状に
 * なる)。範囲外は0..1へ丸める(仕様どおりcomputed valueで丸める)。
 * 既定0・非継承。
 * </p>
 */
public class ShapeImageThreshold extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ShapeImageThreshold();

	public static double get(final CSSStyle style) {
		return ((RealValue) style.get(INFO)).getReal();
	}

	protected ShapeImageThreshold() {
		super("shape-image-threshold");
	}

	public Value getDefault(final CSSStyle style) {
		return RealValue.ZERO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final RealValue real = ValueUtils.toReal(tokens.next());
		if (real == null || tokens.hasNext()) {
			throw new PropertyException();
		}
		final double clamped = Math.max(0, Math.min(1, real.getReal()));
		return clamped == real.getReal() ? real : RealValue.create(clamped);
	}
}
