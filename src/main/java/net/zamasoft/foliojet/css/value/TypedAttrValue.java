package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.Unit;

/**
 * <b>型付き {@code attr()}</b>(CSS Values 5)の未解決値です(2026-08-03新設)。
 *
 * <p>
 * {@code width: attr(width px, auto)} のように、要素の属性を任意のプロパティで
 * 使えるようにする。属性はその要素のものなので、値が決まるのは計算値の段階
 * ——{@link net.zamasoft.foliojet.css.util.ValueUtils#emExToAbsoluteLength}
 * (35個のプロパティが通る単一の窓口)で解決する。{@code em}/{@code rem}や
 * {@code calc()}のフォント相対成分と同じ置き場所である。
 *
 * <p>
 * <b>なぜ実装するか</b>: HTMLの表現属性({@code <td width=200>}等)の写像は、
 * 3エンジンともC++のコードで書かれている。この製品はそれをCSSで書けるように
 * したい——Javaの中に埋もれた既定値は誰も見ないまま残るからである(2026-08-03に
 * {@code <button>}の{@code height:1em}で実際に踏んだ)。Chromeは133で型付き
 * {@code attr()}を出荷しており、<b>実在の仕様・実在の実装がある</b>。
 * 各社が長く動かなかった最大の理由(属性が動的に変わったときの再計算)は、
 * スクリプトを持たないこのエンジンには存在しない。
 *
 * <p>
 * <b>URLは作れない</b>(仕様どおり)。情報の持ち出し経路になるため、
 * {@code url()}の中では使えない。ここでは長さ・色・数値だけを扱う。
 */
public final class TypedAttrValue implements QuantityValue, PaintValue {
	/** 取り出す型。 */
	public enum Kind {
		LENGTH, COLOR, NUMBER, INTEGER
	}

	private final String name;
	private final Kind kind;
	/** {@link Kind#LENGTH}のとき、単位の付いていない数値に補う単位。 */
	private final Unit unit;
	/** 属性が無い・解釈できないときの値(未指定ならnull=宣言ごと無効)。 */
	private final Value fallback;

	public static TypedAttrValue create(String name, Kind kind, Unit unit, Value fallback) {
		return new TypedAttrValue(name, kind, unit, fallback);
	}

	private TypedAttrValue(String name, Kind kind, Unit unit, Value fallback) {
		this.name = name;
		this.kind = kind;
		this.unit = unit;
		this.fallback = fallback;
	}

	public String getName() {
		return this.name;
	}

	public Kind getKind() {
		return this.kind;
	}

	public Unit getUnit() {
		return this.unit;
	}

	public Value getFallback() {
		return this.fallback;
	}

	/**
	 * 属性を読んで値へ解決します。属性が無い・解釈できない場合はフォールバック、
	 * フォールバックも無ければ null(<b>使用値計算時に無効</b>——呼び出し側は
	 * この宣言を無視する)。
	 */
	public Value resolve(CSSStyle style) {
		final CSSElement ce = style.getCSSElement();
		final String raw = ce == null || ce.atts == null ? null : ce.atts.getValue(this.name);
		if (raw != null) {
			final Value value = parse(raw.trim(), style);
			if (value != null) {
				return value;
			}
		}
		return this.fallback;
	}

	private Value parse(String raw, CSSStyle style) {
		if (raw.isEmpty()) {
			return null;
		}
		switch (this.kind) {
		case COLOR: {
			// HTMLのbgcolor等は「red」も「#ff0000」も「ff0000」も来る
			Value named = net.zamasoft.foliojet.css.util.ColorValueUtils.toColorValue(raw);
			if (named != null) {
				return named;
			}
			return net.zamasoft.foliojet.css.util.ColorValueUtils
					.parseRGBHexColor(raw.startsWith("#") ? raw.substring(1) : raw);
		}
		case INTEGER:
		case NUMBER: {
			final double v = parseNumber(raw, this.kind == Kind.INTEGER);
			return Double.isNaN(v) ? null : RealValue.create(v);
		}
		case LENGTH:
		default: {
			// **割合も受ける**(2026-08-03)。HTMLの width="50%" は日常的
			if (raw.endsWith("%")) {
				final double pct = parseNumber(raw.substring(0, raw.length() - 1).trim(), false);
				return Double.isNaN(pct) ? null : PercentageValue.create(pct);
			}
			// 単位が付いていれば尊重し、無ければ指定された単位を補う
			// (HTMLの width="200" は 200px の意味)
			final Value length = net.zamasoft.foliojet.css.util.ValueUtils.toLength(style.getUserAgent(), false, raw);
			if (length != null) {
				return length;
			}
			final double v = parseNumber(raw, false);
			if (Double.isNaN(v)) {
				return null;
			}
			// **フォント相対単位は絶対長として作らない**(2026-08-03)。
			// em/ex/rem/ch はフォント寸法が要るので、相対長のまま返して
			// 同じ窓口(emExToAbsoluteLength)に解かせる
			switch (this.unit) {
			case EM:
			case EX:
			case REM:
			case CH:
				return RelativeLengthValue.of(this.unit, v);
			default:
				return AbsoluteLengthValue.create(style.getUserAgent(), v, this.unit);
			}
		}
		}
	}

	private static double parseNumber(String raw, boolean integer) {
		try {
			final double v = Double.parseDouble(raw);
			if (integer && v != Math.floor(v)) {
				return Double.NaN;
			}
			return v;
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}

	/**
	 * <b>解決前に塗りは取れない</b>。色の文脈でもこの型を返せるように
	 * {@link PaintValue}を名乗るが、ここへ来るのは計算値の解決を通っていない
	 * 場合だけで、それは実装の誤りである。
	 */
	public net.zamasoft.pdfg2d.gc.paint.Paint getPaint(java.awt.geom.Rectangle2D box) {
		throw new IllegalStateException("attr()が解決されないまま塗りとして使われた: " + this);
	}

	/** 解決前は零かどうか分からない。 */
	public boolean isZero() {
		return false;
	}

	/** 解決前は負かどうか分からない(負を拒む文脈では解決後に判定される)。 */
	public boolean isNegative() {
		return false;
	}

	public String toString() {
		return "attr(" + this.name + " " + this.kind + (this.fallback == null ? "" : ", " + this.fallback) + ")";
	}
}
