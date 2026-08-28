package net.zamasoft.foliojet.css.impl.property.font;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code font-stretch}(css-fonts-4では{@code font-width}、
 * {@code font-stretch}は旧名)です(2026-08-29新設)。
 *
 * <p>
 * {@code normal | ultra-condensed | extra-condensed | condensed |
 * semi-condensed | semi-expanded | expanded | extra-expanded |
 * ultra-expanded | <percentage [0,∞]>}。継承、既定{@code normal}(100%)。
 * 値は割合({@link PercentageValue})で持ち、{@link #getWidthClass}で
 * OpenType OS/2の{@code usWidthClass}(1..9)へ丸める(仕様§2.3の対応表。
 * 表にない割合は最も近い級)。
 * </p>
 *
 * <p>
 * <b>効き方</b>: 幅級は{@code FontStyleImpl.widthClass}として運ばれ、
 * pdfg2dの書体選択({@code PDFFontSourceManager.lookup})がitalic/weightの
 * 同点の中から幅級の近い面を選ぶ(要求が通常幅以下なら狭い側を先に、
 * 広ければ広い側を先に——css-fonts-4 §5.2)。面の幅級は{@code <font-dir>}
 * 走査ではOS/2 {@code usWidthClass}、{@code @font-face}では
 * {@code font-stretch}ディスクリプタから来る。幅の合成(字形の伸縮)は
 * しないので、幅級の違う面が無ければ見た目は変わらない。
 * </p>
 */
public class FontStretch extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FontStretch();

	/** {@code normal}に対応するusWidthClass。 */
	public static final int NORMAL_WIDTH_CLASS = 5;

	/** usWidthClass 1..9 に対応する割合(css-fonts-4 §2.3)。 */
	private static final double[] CLASS_PERCENTAGES = { 50, 62.5, 75, 87.5, 100, 112.5, 125, 150, 200 };

	private static final String[] KEYWORDS = { "ultra-condensed", "extra-condensed", "condensed", "semi-condensed",
			"normal", "semi-expanded", "expanded", "extra-expanded", "ultra-expanded" };

	public static PercentageValue get(final CSSStyle style) {
		return (PercentageValue) style.get(INFO);
	}

	/** 計算値の割合をOS/2 usWidthClass(1..9)へ丸めます。 */
	public static int getWidthClass(final CSSStyle style) {
		return toWidthClass(get(style).getPercentage());
	}

	public static int toWidthClass(final double percentage) {
		int best = NORMAL_WIDTH_CLASS;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < CLASS_PERCENTAGES.length; ++i) {
			final double distance = Math.abs(CLASS_PERCENTAGES[i] - percentage);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i + 1;
			}
		}
		return best;
	}

	protected FontStretch() {
		super("font-stretch");
	}

	public Value getDefault(final CSSStyle style) {
		return PercentageValue.FULL;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken token = tokens.next();
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		if (token instanceof CssToken.Percent percent) {
			if (percent.value() < 0) {
				throw new PropertyException();
			}
			return PercentageValue.create(percent.value());
		}
		if (token instanceof CssToken.Ident ident) {
			for (int i = 0; i < KEYWORDS.length; ++i) {
				if (ident.is(KEYWORDS[i])) {
					return PercentageValue.create(CLASS_PERCENTAGES[i]);
				}
			}
		}
		throw new PropertyException();
	}
}
