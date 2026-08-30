package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.CalcFontRelativeValue;
import net.zamasoft.foliojet.css.value.CalcLengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * <a href="http://www.w3.org/TR/CSS21/visudet.html#propdef-line-height"> line-
 * height 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class LineHeight extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new LineHeight();

	public static double get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value instanceof RealValue real) {
			return real.getReal() * FontSize.get(style);
		}
		if (value == KeywordValue.NORMAL) {
			return style.getUserAgent().getNormalLineHeight() * FontSize.get(style);
		}
		return ((AbsoluteLengthValue) value).getLength();
	}

	protected LineHeight() {
		super("line-height");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NORMAL;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		// SPEC css-inline-3: line-heightは負にならない。calc()のlh折り込み等で
		// 負へ落ちた結果は宣言無効ではなく0へクランプする(css-values-4の
		// range checking。2026-08-27)
		return clampNonNegative(this.computeValue(value, style));
	}

	private static Value clampNonNegative(Value value) {
		if (value instanceof AbsoluteLengthValue length && length.getLength() < 0) {
			return AbsoluteLengthValue.ZERO;
		}
		return value;
	}

	/**
	 * この単位がline-height自身の値として自己参照になるかを返します。
	 * {@code lh}は常に、{@code rlh}は根要素のline-heightに書かれたときだけ
	 * 自己参照になる(子孫からは計算済みの根の値を安全に読める)。
	 */
	private static boolean isSelfReferentialLineHeightUnit(net.zamasoft.foliojet.css.token.Unit unit,
			CSSStyle style) {
		if (unit == net.zamasoft.foliojet.css.token.Unit.LH) {
			return true;
		}
		return unit == net.zamasoft.foliojet.css.token.Unit.RLH && style.getRootStyle() == style;
	}

	private Value computeValue(Value value, CSSStyle style) {
		if (value == KeywordValue.NORMAL || value instanceof RealValue) {
			return value;
		}
		// lh単位がline-height自身に書かれた場合は、自己参照を避けるため
		// 継承値(親のline-height、根ではUAのnormal)を基準に先に畳む
		// (SPEC css-values-4 §6.1.2)。ここで畳んでおくことで、他プロパティの
		// lh解決(RelativeLengthValue.toAbsoluteLength→LineHeight.get)が
		// 再帰しないことが保証される
		if (value instanceof RelativeLengthValue rel && isSelfReferentialLineHeightUnit(rel.getUnit(), style)) {
			return AbsoluteLengthValue.create(style.getUserAgent(), inheritedLineHeight(style) * rel.getValue());
		}
		if (value instanceof CalcFontRelativeValue lhCalc && lhCalc.getLh() != 0) {
			value = lhCalc.resolveLh(style.getUserAgent(), inheritedLineHeight(style));
		}
		if (value instanceof CalcFontRelativeValue fontRelative) {
			// フォント相対成分を自要素のフォントで解いてから、残った%成分を
			// 下の分岐で解決する。ここで解かずに末尾のemExToAbsoluteLengthへ
			// 落とすと、%が残ったCalcLengthValueが計算値として確定してしまい、
			// LineHeight.getのキャストで落ちる(calc(50% + 0.5em)で実測)
			value = fontRelative.resolve(style);
		}
		if (value instanceof PercentageValue percentage) {
			return AbsoluteLengthValue.create(style.getUserAgent(), percentage.getRatio() * FontSize.get(style));
		}
		if (value instanceof CalcLengthValue calc) {
			// calc()が絶対長さと割合を混在させた場合(例: calc(50% + 10pt))。
			// line-heightの%はfont-size同様、親ではなく自要素のfont-sizeを
			// 基準に今ここで解決できるため、PercentageValueと同じ扱いにして
			// AbsoluteLengthValueへ完全に還元する。
			return AbsoluteLengthValue.create(style.getUserAgent(),
					calc.getAbsolute() + calc.getRatio() * FontSize.get(style));
		}
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	/**
	 * lh単位の基準になる継承line-height(根要素ではUAのnormal相当)です。
	 *
	 * <p>
	 * 深い継承連鎖(特にボックスを作らない{@code display:contents}の連鎖)で
	 * 各層が{@code line-height:1lh}を持つと、素朴な{@code get(parent)}の
	 * 再帰は祖先の数だけスタックを積む。ルート側から順に計算値を確定させて
	 * キャッシュを埋めることで、再帰深度を親1段に抑える(2026-08-27、
	 * 独立レビュー指摘)。
	 * </p>
	 */
	private static double inheritedLineHeight(CSSStyle style) {
		final CSSStyle parent = style.getParentStyle();
		if (parent == null) {
			return style.getUserAgent().getNormalLineHeight() * FontSize.get(style);
		}
		final java.util.ArrayList<CSSStyle> chain = new java.util.ArrayList<>();
		for (CSSStyle s = parent; s != null; s = s.getParentStyle()) {
			chain.add(s);
		}
		for (int i = chain.size() - 1; i >= 1; --i) {
			get(chain.get(i));
		}
		return get(parent);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value lineHeight = BoxValueUtils.toLineHeight(ua, lu);
		if (lineHeight == null) {
			throw new PropertyException();
		}
		return lineHeight;
	}

}