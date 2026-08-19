package net.zamasoft.foliojet.css.value;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.token.Unit;

/**
 * <b>フォント相対単位を含む calc() の途中結果</b>です(2026-08-03新設)。
 *
 * <p>
 * {@code calc(3.5rem - 26px)} のような式は、解析時には解けない——{@code em}は
 * その要素の、{@code rem}は根要素の、確定した{@code font-size}が要るためである。
 * この値は絶対成分・割合成分・フォント相対成分を分けたまま計算値の段階まで
 * 持ち回り、{@link net.zamasoft.foliojet.css.util.ValueUtils#emExToAbsoluteLength}
 * (35個のプロパティが計算値時に通る単一の窓口)で解決される。
 *
 * <p>
 * <b>2026-08-03まではフォント相対単位を含む calc() を丸ごと無効にしていた。</b>
 * その結果、W3C仕様書の自己リンク記号(¶)を左余白へ出す
 * {@code left: calc(-1 * (3.5rem - 26px))} が効かず、記号が本文に重なっていた。
 * {@code rem}を含むcalc()は現代のCSSでは極めてありふれており、実物大の文書を
 * 取り込んだ第1波で見つかった(PLAN §3)。
 */
public final class CalcFontRelativeValue implements QuantityValue {
	private final double absolute;
	private final double ratio;
	private final double em, ex, rem, ch;

	public static Value create(double absolute, double ratio, double em, double ex, double rem, double ch) {
		return new CalcFontRelativeValue(absolute, ratio, em, ex, rem, ch);
	}

	private CalcFontRelativeValue(double absolute, double ratio, double em, double ex, double rem, double ch) {
		this.absolute = absolute;
		this.ratio = ratio;
		this.em = em;
		this.ex = ex;
		this.rem = rem;
		this.ch = ch;
	}

	/**
	 * フォント相対成分をstyleで解決し、絶対成分と割合成分だけの値へ畳みます。
	 */
	public Value resolve(CSSStyle style) {
		double abs = this.absolute;
		abs += unit(Unit.EM, this.em, style);
		abs += unit(Unit.EX, this.ex, style);
		abs += unit(Unit.REM, this.rem, style);
		abs += unit(Unit.CH, this.ch, style);
		return CalcLengthValue.create(style.getUserAgent(), abs, this.ratio);
	}

	private static double unit(Unit unit, double value, CSSStyle style) {
		if (value == 0) {
			return 0;
		}
		return RelativeLengthValue.of(unit, value).toAbsoluteLength(style).getLength();
	}

	/** 割合成分です(2026-08-19、transformのtranslate%分解用)。 */
	public double getRatio() {
		return this.ratio;
	}

	/**
	 * フォント相対成分を<b>UAの既定フォント寸法(medium)で近似解決</b>した
	 * 絶対成分を返します(2026-08-19)。要素のfont-size文脈が無い解析段階
	 * (transformのtranslate等)のための近似で、メディアクエリのem/rem
	 * ({@code CSSStyleSheetBuilder.mediaFontRelativeLength})と同じ扱い。
	 * ex/chは慣行どおりemの半分とみなす。
	 */
	public double approximateAbsolute(net.zamasoft.foliojet.ua.UserAgent ua) {
		final double medium = ua.getFontSize(net.zamasoft.foliojet.ua.AbsoluteFontSize.MEDIUM);
		return this.absolute + (this.em + this.rem) * medium + (this.ex + this.ch) * medium * 0.5;
	}

	/**
	 * 絶対成分だけを倍率倍した値を返します。font-sizeプロパティはズーム倍率
	 * ({@link net.zamasoft.foliojet.ua.UserAgent#getFontMagnification})を絶対
	 * 長さにだけ適用する規約で、フォント相対成分は基準のフォント寸法自体が
	 * 倍率適用済みのため掛けない。
	 */
	public Value scaleAbsolute(double factor) {
		if (factor == 1 || this.absolute == 0) {
			return this;
		}
		return new CalcFontRelativeValue(this.absolute * factor, this.ratio, this.em, this.ex, this.rem, this.ch);
	}

	/**
	 * <b>フォント寸法が定まるまで零かどうかは分からない</b>ので、全成分が0の
	 * ときだけ零と答える(この型はそもそも成分が非零のときにしか作られない)。
	 */
	public boolean isZero() {
		return this.absolute == 0 && this.ratio == 0 && !hasFont();
	}

	/**
	 * <b>負であることが確実に分かるときだけ</b>真を返します——フォント寸法は
	 * 常に正なので、全成分が負(または0)なら結果も負である。混在しているときは
	 * 解決するまで決まらないので偽を返す(CalcLengthValueと同じ規約)。
	 */
	public boolean isNegative() {
		if (this.absolute > 0 || this.ratio > 0 || this.em > 0 || this.ex > 0 || this.rem > 0 || this.ch > 0) {
			return false;
		}
		return this.absolute < 0 || this.ratio < 0 || this.em < 0 || this.ex < 0 || this.rem < 0 || this.ch < 0;
	}

	private boolean hasFont() {
		return this.em != 0 || this.ex != 0 || this.rem != 0 || this.ch != 0;
	}

	public String toString() {
		// 負のゼロ(-1を掛けた0成分)は0として書く。表示の揺れを避けるため
		return "calc(" + z(this.absolute) + "pt + " + z(this.ratio * 100) + "% + " + z(this.em) + "em + "
				+ z(this.ex) + "ex + " + z(this.rem) + "rem + " + z(this.ch) + "ch)";
	}

	private static double z(double v) {
		return v == 0 ? 0.0 : v;
	}
}
