package net.zamasoft.foliojet.css.value;

import java.util.function.IntToDoubleFunction;

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
 *
 * <p>
 * 成分は単位ごとの名前付きフィールドだったが、{@code cap}/{@code rlh}の追加で
 * 位置引数が9個になったため{@link #UNITS}添字の配列へ改めた(2026-08-30)。
 * 加減は成分ごと、数との乗除は全成分に効く——どちらもフォント寸法に対して
 * 線形なので、後で寸法を掛けても等価である。
 *
 * <p>
 * 線形でない min()・max()・clamp() の部分は{@link Term}として別に持つ(2026-10-04)。
 */
public final class CalcFontRelativeValue implements QuantityValue {
	/**
	 * 大小がフォント寸法で決まる min()・max() の部分です(2026-10-04、出版の報告: {@code min(10mm, 3em)} が
	 * 不正な値になっていた)。
	 *
	 * <p>
	 * {@code min(10mm, 3em)} は em の寸法が定まるまでどちらを取るか分からないので、線形の成分と分けて
	 * 持ち、解くときに選ぶ。{@code unit} は{@link #UNITS}の i 番目の単位 1 つ分の長さ(pt)を返す。
	 * 引数はいつもすべて評価してから選ぶ——どの単位を使うかを問い合わせで知るため({@link #uses})。
	 * </p>
	 */
	@FunctionalInterface
	public interface Term {
		double length(IntToDoubleFunction unit);

		/** {@code absolute + Σ font[i]·unit(i) + term} です。 */
		static Term linear(double absolute, double[] font, Term term) {
			final double[] components = font.clone();
			return unit -> {
				double length = absolute + (term == null ? 0 : term.length(unit));
				for (int i = 0; i < components.length; ++i) {
					if (components[i] != 0) {
						length += components[i] * unit.applyAsDouble(i);
					}
				}
				return length;
			};
		}

		/** a と b の小さい方(min)・大きい方です。 */
		static Term extremum(boolean min, Term a, Term b) {
			return unit -> {
				final double x = a.length(unit);
				final double y = b.length(unit);
				return min ? Math.min(x, y) : Math.max(x, y);
			};
		}

		/** {@code a + sign·b} です。どちらかが null ならもう一方だけ。 */
		static Term sum(Term a, Term b, double sign) {
			if (b == null) {
				return a;
			}
			if (a == null) {
				return scaled(b, sign);
			}
			return unit -> a.length(unit) + sign * b.length(unit);
		}

		static Term scaled(Term term, double factor) {
			return term == null ? null : unit -> factor * term.length(unit);
		}

		/** 単位の添字 i を使うかどうかです。 */
		static boolean uses(Term term, int i) {
			final boolean[] used = { false };
			term.length(j -> {
				used[0] |= j == i;
				return 1;
			});
			return used[0];
		}
	}


	/** 成分配列の並びです。{@link #indexOf}で添字を引きます。 */
	public static final Unit[] UNITS = { Unit.EM, Unit.EX, Unit.REM, Unit.CH, Unit.LH, Unit.CAP, Unit.RLH };

	/** {@link Unit#LH}の添字です(line-height自身の自己参照回避で特別扱いする)。 */
	private static final int LH = 4;

	/** この単位の成分添字を返します。フォント相対でなければ -1。 */
	public static int indexOf(Unit unit) {
		for (int i = 0; i < UNITS.length; ++i) {
			if (UNITS[i] == unit) {
				return i;
			}
		}
		return -1;
	}

	/** 成分がすべて0の配列を作ります。 */
	public static double[] newComponents() {
		return new double[UNITS.length];
	}

	private final double absolute;
	private final double ratio;
	private final double[] font;
	/** 線形でない部分です。無ければ null。 */
	private final Term term;

	public static Value create(double absolute, double ratio, double[] font) {
		return create(absolute, ratio, font, null);
	}

	public static Value create(double absolute, double ratio, double[] font, Term term) {
		return new CalcFontRelativeValue(absolute, ratio, font.clone(), term);
	}

	private CalcFontRelativeValue(double absolute, double ratio, double[] font, Term term) {
		this.absolute = absolute;
		this.ratio = ratio;
		this.font = font;
		this.term = term;
	}

	/**
	 * フォント相対成分をstyleで解決し、絶対成分と割合成分だけの値へ畳みます。
	 */
	public Value resolve(CSSStyle style) {
		double abs = this.absolute;
		for (int i = 0; i < UNITS.length; ++i) {
			if (this.font[i] != 0) {
				abs += RelativeLengthValue.of(UNITS[i], this.font[i]).toAbsoluteLength(style).getLength();
			}
		}
		if (this.term != null) {
			abs += this.term.length(i -> RelativeLengthValue.of(UNITS[i], 1).toAbsoluteLength(style).getLength());
		}
		return CalcLengthValue.create(style.getUserAgent(), abs, this.ratio);
	}

	/** lh を使うかどうかです(line-height自身の自己参照回避用)。 */
	public boolean usesLh() {
		return this.font[LH] != 0 || this.term != null && Term.uses(this.term, LH);
	}

	/** {@code 100% - <unit値>}を表す値です(&lt;position&gt;の端オフセット用)。 */
	public static Value fullMinus(Unit unit, double v) {
		final int i = indexOf(unit);
		if (i < 0) {
			return null;
		}
		final double[] font = newComponents();
		font[i] = -v;
		return new CalcFontRelativeValue(0, 1, font, null);
	}

	/** {@code 100% - この値}を返します(&lt;position&gt;の端オフセット用)。 */
	public Value subtractedFromFull() {
		return new CalcFontRelativeValue(-this.absolute, 1 - this.ratio, negated(this.font),
				Term.scaled(this.term, -1));
	}

	/** lh成分を、与えられた基準line-heightで絶対成分へ畳んだ値を返します。 */
	public Value resolveLh(net.zamasoft.foliojet.ua.UserAgent ua, double lineHeight) {
		if (!this.usesLh()) {
			return this;
		}
		final double abs = this.absolute + this.font[LH] * lineHeight;
		final double[] font = this.font.clone();
		font[LH] = 0;
		if (this.term != null) {
			final Term term = this.term;
			return new CalcFontRelativeValue(abs, this.ratio, font,
					unit -> term.length(i -> i == LH ? lineHeight : unit.applyAsDouble(i)));
		}
		if (!hasFont(font)) {
			return CalcLengthValue.create(ua, abs, this.ratio);
		}
		return new CalcFontRelativeValue(abs, this.ratio, font, null);
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
	 * ex/chは慣行どおりemの半分、capは0.7em、lh/rlhはUAのnormalとみなす。
	 */
	public double approximateAbsolute(net.zamasoft.foliojet.ua.UserAgent ua) {
		final double medium = ua.getFontSize(net.zamasoft.foliojet.ua.AbsoluteFontSize.MEDIUM);
		double abs = this.absolute;
		for (int i = 0; i < UNITS.length; ++i) {
			abs += this.font[i] * medium * approximateRatio(UNITS[i], ua);
		}
		if (this.term != null) {
			abs += this.term.length(i -> medium * approximateRatio(UNITS[i], ua));
		}
		return abs;
	}

	private static double approximateRatio(Unit unit, net.zamasoft.foliojet.ua.UserAgent ua) {
		switch (unit) {
		case EM:
		case REM:
			return 1;
		case EX:
		case CH:
			return 0.5;
		case CAP:
			return 0.7;
		case LH:
		case RLH:
			return ua.getNormalLineHeight();
		default:
			return 0;
		}
	}

	/**
	 * 絶対成分だけを倍率倍した値を返します。font-sizeプロパティはズーム倍率
	 * ({@link net.zamasoft.foliojet.ua.UserAgent#getFontMagnification})を絶対
	 * 長さにだけ適用する規約で、フォント相対成分は基準のフォント寸法自体が
	 * 倍率適用済みのため掛けない。
	 * <p>
	 * min()・max() の項の中の絶対長さにも掛ける。項は絶対長さと単位の長さの両方を同じ正の数倍すると
	 * その数倍になる(min・max・加減・数との乗除のどれも保つ)ので、単位の長さを倍率で割って解き、
	 * 結果に倍率を掛ければよい。
	 * </p>
	 */
	public Value scaleAbsolute(double factor) {
		if (factor == 1 || this.absolute == 0 && this.term == null) {
			return this;
		}
		final Term term = this.term;
		return new CalcFontRelativeValue(this.absolute * factor, this.ratio, this.font, term == null ? null
				: unit -> factor * term.length(i -> unit.applyAsDouble(i) / factor));
	}

	/**
	 * <b>フォント寸法が定まるまで零かどうかは分からない</b>ので、全成分が0の
	 * ときだけ零と答える(この型はそもそも成分が非零のときにしか作られない)。
	 */
	public boolean isZero() {
		return this.absolute == 0 && this.ratio == 0 && !hasFont(this.font) && this.term == null;
	}

	/**
	 * <b>負であることが確実に分かるときだけ</b>真を返します——フォント寸法は
	 * 常に正なので、全成分が負(または0)なら結果も負である。混在しているときは
	 * 解決するまで決まらないので偽を返す(CalcLengthValueと同じ規約)。
	 */
	public boolean isNegative() {
		if (this.term != null || this.absolute > 0 || this.ratio > 0) {
			return false;
		}
		for (final double v : this.font) {
			if (v > 0) {
				return false;
			}
		}
		if (this.absolute < 0 || this.ratio < 0) {
			return true;
		}
		for (final double v : this.font) {
			if (v < 0) {
				return true;
			}
		}
		return false;
	}

	private static boolean hasFont(double[] font) {
		for (final double v : font) {
			if (v != 0) {
				return true;
			}
		}
		return false;
	}

	private static double[] negated(double[] font) {
		final double[] result = new double[font.length];
		for (int i = 0; i < font.length; ++i) {
			result[i] = -font[i];
		}
		return result;
	}

	public String toString() {
		// 負のゼロ(-1を掛けた0成分)は0として書く。表示の揺れを避けるため
		final StringBuilder buff = new StringBuilder("calc(").append(z(this.absolute)).append("pt + ")
				.append(z(this.ratio * 100)).append('%');
		for (int i = 0; i < UNITS.length; ++i) {
			buff.append(" + ").append(z(this.font[i])).append(UNITS[i].name().toLowerCase(java.util.Locale.ROOT));
		}
		if (this.term != null) {
			buff.append(" + min/max(...)");
		}
		return buff.append(')').toString();
	}

	private static double z(double v) {
		return v == 0 ? 0.0 : v;
	}
}
