package net.zamasoft.foliojet.css.impl.property.box;

import java.awt.geom.AffineTransform;
import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.TransformValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 個別変換プロパティ{@code translate}です(css-transforms-2 §7、
 * 2026-08-29新設)。
 *
 * <p>
 * {@code none | <length-percentage> [ <length-percentage> <length>? ]?}。
 * 3つ目(z)は読み捨てる。値は{@code transform}と同じ
 * {@link TransformValue}で持ち、割合は{@code translate()}と同じく
 * 要素自身の寸法に掛ける係数として運ぶ——個別プロパティは変換列の
 * 先頭(translate→rotate→scale→transform の順、§7.3)に来るので、
 * 割合の前に合成される行列は恒等で、係数はそのままW→x・H→yに載る。
 * 実際の合成は{@code BoxStyleMapper.setupParams}で行う。
 * </p>
 */
public class Translate extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Translate();

	public static TransformValue get(final CSSStyle style) {
		return (TransformValue) style.get(INFO);
	}

	protected Translate() {
		super("translate");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		if (value == KeywordValue.NONE) {
			return TransformValue.IDENTITY_TRANSFORM_VALUE;
		}
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken first = tokens.next();
		if (first instanceof CssToken.Ident) {
			if (ValueUtils.isNone(first) && !tokens.hasNext()) {
				return KeywordValue.NONE;
			}
			throw new PropertyException();
		}
		final double[] pct = new double[2];
		final double tx = Transform.lengthOrRatio(ua, first, pct, 0);
		double ty = 0;
		if (tokens.hasNext()) {
			ty = Transform.lengthOrRatio(ua, tokens.next(), pct, 1);
			if (tokens.hasNext()) {
				// z成分。紙面へ射影できないので長さとして検査だけする
				Transform.toLength(ua, tokens.next());
			}
		}
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		return TransformValue.create(AffineTransform.getTranslateInstance(tx, ty), pct[0], pct[1]);
	}
}
