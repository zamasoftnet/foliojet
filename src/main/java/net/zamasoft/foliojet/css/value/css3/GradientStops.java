package net.zamasoft.foliojet.css.value.css3;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.util.ColorUtils;

/**
 * A sequence of gradient color stops (css-images-3 §3.4, added on 2026-08-29).
 *
 * <p>
 * Stop positions are not determined during parsing: {@code 50%} is a ratio to the gradient line's
 * length, whereas {@code 10px} is an absolute length. The gradient line's length is known only
 * once the box to fill is determined. Thus, stores ratios ({@link #ratio}) and absolute lengths
 * ({@link #abs}, pt) separately; {@link #resolve} converts them to positions in 0..1 using the box size.
 * Stops with omitted positions use {@link #auto} and are spaced evenly between surrounding stops,
 * as the specification requires.
 * </p>
 *
 * <p>
 * PDF shading has no <b>repetition</b> ({@code repeating-*-gradient}), so expands the period
 * (the distance between the first and last stops) into a stop sequence covering the gradient line.
 * Caps expansion at {@link #MAX_REPEATS} periods to prevent an explosion of stops for extremely
 * short periods. Finer stripes are invisible even in print. Only the capped case is an approximation,
 * reported through {@link Resolved#capped()}. If the destination supports periodic repetition
 * ({@code REPEATING_GRADIENT}: Java2D and SVG), the exact approach is to create just one period
 * with {@link #resolvePeriod} and paint with {@code SpreadMethod.REPEAT} (2026-08-29).
 * </p>
 */
public final class GradientStops {
	/** The repetition expansion limit (number of periods). */
	public static final int MAX_REPEATS = 64;

	/**
	 * The minimum gap for the strictly increasing positions required by AWT/PDF. Meets this
	 * requirement while preserving hard stops ({@code red 50%, blue 50%})
	 * (moved from normalizeGradientStops of 2026-08-16).
	 */
	private static final double EPSILON = 1e-5;

	private final Color[] colors;
	private final double[] ratio;
	private final double[] abs;
	private final boolean[] auto;

	/**
	 * A resolved stop sequence (positions strictly increase within 0..1). {@code capped} means
	 * repetition expansion hit {@link #MAX_REPEATS} (coverage is incomplete and thus approximate).
	 */
	public record Resolved(double[] fractions, Color[] colors, boolean capped) {
		public Resolved(final double[] fractions, final Color[] colors) {
			this(fractions, colors, false);
		}
	}

	/**
	 * A stop sequence for one repetition period (2026-08-29). Positions map the period to 0..1
	 * and start at the gradient line's start (phase 0). Even if the original stops start elsewhere,
	 * wrapping by the period aligns the phase.
	 *
	 * @param fractions 0..1 (strictly increasing)
	 * @param colors    the color at each position
	 * @param length    the period length (as a ratio to the gradient line's length)
	 */
	public record Period(double[] fractions, Color[] colors, double length) {
	}

	public GradientStops(final Color[] colors, final double[] ratio, final double[] abs, final boolean[] auto) {
		if (colors.length == 0 || colors.length != ratio.length || colors.length != abs.length
				|| colors.length != auto.length) {
			throw new IllegalArgumentException();
		}
		this.colors = colors;
		this.ratio = ratio;
		this.abs = abs;
		this.auto = auto;
	}

	/** Creates a stop sequence with all positions specified as ratios (or auto). */
	public static GradientStops ofFractions(final double[] fractions, final Color[] colors) {
		final double[] abs = new double[fractions.length];
		final boolean[] auto = new boolean[fractions.length];
		for (int i = 0; i < fractions.length; ++i) {
			auto[i] = fractions[i] < 0;
		}
		return new GradientStops(colors, fractions, abs, auto);
	}

	public int size() {
		return this.colors.length;
	}

	public Color lastColor() {
		return this.colors[this.colors.length - 1];
	}

	/**
	 * Determines stop positions.
	 *
	 * @param length    the gradient line's length (pt), used to convert ratios and absolute lengths;
	 *                  if zero or less, ignores absolute lengths
	 * @param repeating whether to repeat
	 * @param cover     the range the paint must cover (a multiple of the gradient line's length, at least 1);
	 *                  returned positions scale this range to 0..1, used for radial repetition
	 *                  to expand periods as far as the most distant corner
	 */
	public Resolved resolve(final double length, final boolean repeating, final double cover) {
		final int n = this.colors.length;
		final double[] pos = this.positions(length);
		final double span = Math.max(1.0, cover);
		final List<double[]> positions = new ArrayList<double[]>();
		final List<Color> colorList = new ArrayList<Color>();
		final double period = pos[n - 1] - pos[0];
		boolean capped = false;
		if (repeating && period > 1e-6) {
			// Repeat periods starting at or below zero until they extend past cover.
			int first = (int) Math.floor(-pos[0] / period);
			int last = (int) Math.ceil((span - pos[0]) / period);
			if (last - first > MAX_REPEATS) {
				last = first + MAX_REPEATS;
				capped = true;
			}
			for (int k = first; k <= last; ++k) {
				final double offset = k * period;
				// At period boundaries, the first color shares the position of the previous period's last color
				// (wraps as a hard stop). normalize inserts the minimum gap.
				for (int i = 0; i < n; ++i) {
					positions.add(new double[] { pos[i] + offset });
					colorList.add(this.colors[i]);
				}
			}
		} else {
			for (int i = 0; i < n; ++i) {
				positions.add(new double[] { pos[i] });
				colorList.add(this.colors[i]);
			}
		}
		final double[] ps = new double[positions.size()];
		for (int i = 0; i < ps.length; ++i) {
			ps[i] = positions.get(i)[0];
		}
		final Resolved r = clip(ps, colorList.toArray(new Color[colorList.size()]), 0, span);
		return capped ? new Resolved(r.fractions(), r.colors(), true) : r;
	}

	/**
	 * Returns one repetition period with its phase starting at the gradient line's start.
	 * If the period collapses (all stops at the same position), returns null; the caller
	 * falls back to expansion with {@link #resolve}.
	 *
	 * @param length the gradient line's length (pt)
	 */
	public Period resolvePeriod(final double length) {
		final int n = this.colors.length;
		final double[] pos = this.positions(length);
		final double period = pos[n - 1] - pos[0];
		if (!(period > 1e-6)) {
			return null;
		}
		// Original stop positions within the period (the first is zero).
		final double[] offset = new double[n];
		for (int i = 0; i < n; ++i) {
			offset[i] = pos[i] - pos[0];
		}
		// Where the gradient line's start (absolute position 0) lies in the period: shift = pos[0] mod period.
		final double shift = pos[0] - Math.floor(pos[0] / period) * period;
		if (shift <= 1e-9) {
			// The phase is aligned (the gradient line starts at the period's start). Use one period
			// as is without wrapping: wrapping moves the last stop to position zero and breaks
			// the ordering across the hard stop (found by measurement on 2026-08-29).
			final double[] direct = new double[n];
			for (int i = 0; i < n; ++i) {
				direct[i] = offset[i] / period;
			}
			normalize(direct);
			return new Period(direct, this.colors.clone(), period);
		}
		// Wrap each stop to its position in a period starting at the line's start. At the same position,
		// place wrapped stops before unwrapped stops: the wrapped side represents the color
		// entering that position from the left (=the immediately preceding color).
		final List<double[]> folded = new ArrayList<double[]>(n + 2);
		for (int i = 0; i < n; ++i) {
			double t = offset[i] + shift;
			final boolean wrapped = t >= period;
			if (wrapped) {
				t -= period;
			}
			folded.add(new double[] { t, wrapped ? 0 : 1, i });
		}
		folded.sort((a, b) -> a[0] != b[0] ? Double.compare(a[0], b[0])
				: a[1] != b[1] ? Double.compare(a[1], b[1]) : Double.compare(a[2], b[2]));
		// Both ends use the periodic function's color at the start (first and last colors if shift=0).
		final double u = shift > 0 ? period - shift : 0;
		final List<Double> outPos = new ArrayList<Double>(n + 2);
		final List<Color> outColors = new ArrayList<Color>(n + 2);
		outPos.add(0.0);
		// The start uses the color proceeding forward from there: the right-hand color at a hard stop.
		outColors.add(colorAtRight(offset, this.colors, u));
		for (final double[] f : folded) {
			outPos.add(f[0] / period);
			outColors.add(this.colors[(int) f[2]]);
		}
		outPos.add(1.0);
		outColors.add(colorAt(offset, this.colors, shift > 0 ? period - shift : period));
		final double[] fractions = new double[outPos.size()];
		for (int i = 0; i < fractions.length; ++i) {
			fractions[i] = outPos.get(i);
		}
		normalize(fractions);
		return new Period(fractions, outColors.toArray(new Color[outColors.size()]), period);
	}

	/** Determines stop positions (including css-images-3 §3.4.3 fixup, before repetition expansion). */
	private double[] positions(final double length) {
		final int n = this.colors.length;
		final double[] pos = new double[n];
		for (int i = 0; i < n; ++i) {
			if (this.auto[i]) {
				pos[i] = Double.NaN;
			} else {
				pos[i] = this.ratio[i] + (length > 0 ? this.abs[i] / length : 0);
			}
		}
		// css-images-3 §3.4.3 fixup: omitted first/last positions become 0/1; raise backward positions
		// to the preceding position; space omitted positions evenly between surrounding stops.
		if (Double.isNaN(pos[0])) {
			pos[0] = 0;
		}
		if (Double.isNaN(pos[n - 1])) {
			pos[n - 1] = 1;
		}
		double max = pos[0];
		for (int i = 1; i < n; ++i) {
			if (!Double.isNaN(pos[i])) {
				if (pos[i] < max) {
					pos[i] = max;
				}
				max = pos[i];
			}
		}
		for (int i = 1; i < n; ++i) {
			if (Double.isNaN(pos[i])) {
				int j = i + 1;
				while (Double.isNaN(pos[j])) {
					++j;
				}
				final double a = pos[i - 1];
				final double step = (pos[j] - a) / (j - i + 1);
				for (int k = i; k < j; ++k) {
					pos[k] = a + step * (k - i + 1);
				}
				i = j;
			}
		}
		return pos;
	}

	/**
	 * Clips the position sequence to [lo,hi] and maps it to 0..1. Drops stops outside the range
	 * and adds interpolated colors at the ends (needed when absolute-length stops extend beyond
	 * the gradient line, or expanded repetition periods extend below zero or above cover).
	 */
	private static Resolved clip(final double[] pos, final Color[] colors, final double lo, final double hi) {
		final int n = pos.length;
		final List<Double> outPos = new ArrayList<Double>(n + 2);
		final List<Color> outColors = new ArrayList<Color>(n + 2);
		final double range = hi - lo;
		// Endpoint colors.
		if (pos[0] > lo) {
			outPos.add(0.0);
			outColors.add(colors[0]);
		} else if (pos[0] < lo) {
			outPos.add(0.0);
			outColors.add(colorAt(pos, colors, lo));
		}
		for (int i = 0; i < n; ++i) {
			if (pos[i] < lo || pos[i] > hi) {
				continue;
			}
			outPos.add((pos[i] - lo) / range);
			outColors.add(colors[i]);
		}
		if (pos[n - 1] < hi) {
			outPos.add(1.0);
			outColors.add(colors[n - 1]);
		} else if (pos[n - 1] > hi) {
			outPos.add(1.0);
			outColors.add(colorAt(pos, colors, hi));
		}
		final double[] fractions = new double[outPos.size()];
		for (int i = 0; i < fractions.length; ++i) {
			fractions[i] = outPos.get(i);
		}
		normalize(fractions);
		return new Resolved(fractions, outColors.toArray(new Color[outColors.size()]));
	}

	/** The interpolated color at position {@code t} (positions must be nondecreasing). */
	public static Color colorAt(final double[] pos, final Color[] colors, final double t) {
		if (t <= pos[0]) {
			return colors[0];
		}
		final int n = pos.length;
		if (t >= pos[n - 1]) {
			return colors[n - 1];
		}
		for (int i = 1; i < n; ++i) {
			if (t <= pos[i]) {
				final double d = pos[i] - pos[i - 1];
				if (d <= 0) {
					return colors[i];
				}
				return mix(colors[i - 1], colors[i], (t - pos[i - 1]) / d);
			}
		}
		return colors[n - 1];
	}

	/**
	 * At a hard stop, returns the <b>right-hand</b> color (the one proceeding forward from there).
	 * {@link #colorAt} returns the left-hand color (the one entering the position), so use this
	 * when the color from this point onward is needed, such as at a period's start (2026-08-29).
	 */
	private static Color colorAtRight(final double[] pos, final Color[] colors, final double t) {
		for (int i = colors.length - 1; i >= 0; --i) {
			if (pos[i] == t) {
				return colors[i];
			}
		}
		return colorAt(pos, colors, t);
	}

	/** Mixes two colors by {@code f} (0 for the first, 1 for the second), using premultiplied interpolation. */
	public static Color mix(final Color a, final Color b, final double f) {
		final float fa = (float) f, ia = 1 - fa;
		final float aa = a.getAlpha(), ab = b.getAlpha();
		final float alpha = aa * ia + ab * fa;
		if (alpha <= 0) {
			return RGBAColor.create(0, 0, 0, 0);
		}
		final float r = (a.getRed() * aa * ia + b.getRed() * ab * fa) / alpha;
		final float g = (a.getGreen() * aa * ia + b.getGreen() * ab * fa) / alpha;
		final float bl = (a.getBlue() * aa * ia + b.getBlue() * ab * fa) / alpha;
		if (alpha >= 1) {
			return RGBColor.create(ColorUtils.clamp01(r), ColorUtils.clamp01(g), ColorUtils.clamp01(bl));
		}
		return RGBAColor.create(ColorUtils.clamp01(r), ColorUtils.clamp01(g), ColorUtils.clamp01(bl), alpha);
	}

	/**
	 * Normalizes color-stop positions to <b>strictly increasing order</b> (2026-08-16).
	 *
	 * <p>
	 * CSS raises any position smaller than the previous stop's to the previous position, and
	 * <b>multiple stops at the same position are valid</b> (hard stops, such as
	 * {@code linear-gradient(red 50%, blue 50%)}, which create a sharp boundary).
	 * However, {@code java.awt.MultipleGradientPaint}, which performs the actual painting,
	 * requires strictly increasing positions; otherwise, it throws
	 * {@code IllegalArgumentException: Keyframe fractions must be increasing}.
	 * This went beyond a drawing failure: <b>it aborted conversion of the entire page,
	 * losing all its content</b> (the reason elife-art and shadcn-docs in the real-site corpus
	 * failed conversion completely).
	 * </p>
	 *
	 * <p>
	 * Therefore, shifts coincident positions apart by only the smallest representable gap.
	 * The gap is {@code 1e-5}, less than 0.01 pt even for a 1000 pt-wide type area,
	 * satisfying AWT's requirement while visually preserving hard stops.
	 * </p>
	 */
	public static void normalize(final double[] ds) {
		for (int i = 0; i < ds.length; ++i) {
			if (Double.isNaN(ds[i])) {
				ds[i] = i == 0 ? 0 : ds[i - 1];
			}
			if (ds[i] < 0) {
				ds[i] = 0;
			} else if (ds[i] > 1) {
				ds[i] = 1;
			}
			if (i > 0 && ds[i] <= ds[i - 1]) {
				// CSS disallows backward positions (raises them to the preceding position). Then
				// insert the minimum gap for AWT.
				ds[i] = ds[i - 1] + EPSILON;
			}
		}
		// If the last position exceeds 1, pack backward to keep all positions at or below 1.
		if (ds[ds.length - 1] > 1) {
			ds[ds.length - 1] = 1;
			for (int i = ds.length - 2; i >= 0; --i) {
				if (ds[i] >= ds[i + 1]) {
					ds[i] = ds[i + 1] - EPSILON;
				}
			}
			if (ds[0] < 0) {
				ds[0] = 0;
			}
		}
	}

	/** For display-list dumps. A summary of the color count and position specifications. */
	@Override
	public String toString() {
		final StringBuilder s = new StringBuilder();
		for (int i = 0; i < this.colors.length; ++i) {
			if (i > 0) {
				s.append(',');
			}
			s.append(hex(this.colors[i]));
			if (!this.auto[i]) {
				s.append(' ');
				if (this.ratio[i] != 0 || this.abs[i] == 0) {
					s.append(String.format(java.util.Locale.ROOT, "%.0f%%", this.ratio[i] * 100));
				}
				if (this.abs[i] != 0) {
					s.append(String.format(java.util.Locale.ROOT, "%s%.2fpt", this.ratio[i] != 0 ? "+" : "",
							this.abs[i]));
				}
			}
		}
		return s.toString();
	}

	static String hex(final Color c) {
		final String rgb = String.format("#%02x%02x%02x", Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255),
				Math.round(c.getBlue() * 255));
		return c.getAlpha() < 1 ? rgb + String.format("%02x", Math.round(c.getAlpha() * 255)) : rgb;
	}
}
