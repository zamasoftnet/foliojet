package net.zamasoft.foliojet.css.util;

import net.zamasoft.foliojet.css.token.Unit;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;

/**
 * ビューポート単位({@code vw}/{@code vh}/{@code vmin}/{@code vmax}と、
 * {@link Unit#of}が同じ値へ畳むsmall/large/dynamic系とvi/vb)を絶対長さへ解決します
 * (2026-08-29)。
 *
 * <p>
 * ページ媒体の初期包含ブロックは<b>ページ領域</b>(ページ箱から
 * {@code @page}マージンを除いた版面)なので、その1%を1単位とします。
 * 解析時点では{@code @page}規則の集計が済んでいないため、寸法は
 * UAプロパティ({@code output.page-width}/{@code output.page-height}/
 * {@code output.page-margins}——メディアクエリの幅判定と同じ源泉)から
 * 取ります。文書側の{@code @page { size; margin }}で版面を変えた場合は
 * ずれる(記録済みの近似)。
 * </p>
 *
 * <p>
 * 実サイト50件の変換で、{@code min(192px, 100vh)}や
 * {@code calc(100vw - 2rem)}のようにcalc()の中に現れる例が多かった
 * (565件/45サイト)。calc()の葉は{@code ValueUtils.toAbsoluteLength}
 * を通るので、そこから呼ばれる本クラスで一元的に解決する。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ViewportUnits {
	private ViewportUnits() {
		// utility
	}

	/** UAプロパティが読めない場合の既定(A4、余白12.7mm)。 */
	private static final double DEFAULT_WIDTH_PT = 210 / 25.4 * 72;
	private static final double DEFAULT_HEIGHT_PT = 297 / 25.4 * 72;
	private static final double DEFAULT_MARGIN_PT = 12.7 / 25.4 * 72;

	/**
	 * 単位1つ分を解決します。
	 *
	 * @param ua    UA(ページ寸法のプロパティを読む)
	 * @param unit  VW/VH/VMIN/VMAXのいずれか
	 * @param value 単位に掛ける数
	 */
	public static AbsoluteLengthValue resolve(final UserAgent ua, final Unit unit, final double value) {
		final double[] area = contentArea(ua);
		final double base;
		switch (unit) {
		case VW:
			base = area[0];
			break;
		case VH:
			base = area[1];
			break;
		case VMIN:
			base = Math.min(area[0], area[1]);
			break;
		case VMAX:
			base = Math.max(area[0], area[1]);
			break;
		default:
			throw new IllegalArgumentException(unit.toString());
		}
		return AbsoluteLengthValue.create(ua, base * value / 100);
	}

	/** 版面(ページ領域)の幅と高さ(pt)。 */
	static double[] contentArea(final UserAgent ua) {
		double width = length(ua, UAProps.OUTPUT_PAGE_WIDTH, DEFAULT_WIDTH_PT);
		double height = length(ua, UAProps.OUTPUT_PAGE_HEIGHT, DEFAULT_HEIGHT_PT);
		// 余白はCSSのmarginと同じ1〜4値(上 右 下 左)
		double top = DEFAULT_MARGIN_PT, right = DEFAULT_MARGIN_PT, bottom = DEFAULT_MARGIN_PT,
				left = DEFAULT_MARGIN_PT;
		final String margins = property(ua, UAProps.OUTPUT_PAGE_MARGINS);
		if (margins != null) {
			final String[] values = margins.trim().split("[\s]+");
			final double[] m = new double[values.length];
			boolean ok = values.length >= 1 && values.length <= 4;
			for (int i = 0; ok && i < values.length; ++i) {
				final AbsoluteLengthValue l = ValueUtils.toAbsoluteLength(ua, false, values[i]);
				if (l == null) {
					ok = false;
				} else {
					m[i] = l.getLength();
				}
			}
			if (ok) {
				top = m[0];
				right = m.length > 1 ? m[1] : m[0];
				bottom = m.length > 2 ? m[2] : m[0];
				left = m.length > 3 ? m[3] : right;
			}
		}
		width = Math.max(0, width - left - right);
		height = Math.max(0, height - top - bottom);
		return new double[] { width, height };
	}

	private static double length(final UserAgent ua, final net.zamasoft.foliojet.ua.props.StringPropManager prop,
			final double fallback) {
		final String s = property(ua, prop);
		final AbsoluteLengthValue l = s == null ? null : ValueUtils.toAbsoluteLength(ua, false, s);
		return l == null ? fallback : l.getLength();
	}

	/**
	 * UAプロパティを読みます。単体テストのProxy UAのように
	 * {@code getProperty}を実装しない場合は既定値へ落とす。
	 */
	private static String property(final UserAgent ua, final net.zamasoft.foliojet.ua.props.StringPropManager prop) {
		try {
			return prop.getString(ua);
		} catch (RuntimeException e) {
			return null;
		}
	}
}
