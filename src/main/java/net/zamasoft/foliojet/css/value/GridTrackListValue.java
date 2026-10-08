package net.zamasoft.foliojet.css.value;

import java.util.ArrayList;
import java.util.List;

/**
 * A track list for {@code grid-template-columns/rows} (and {@code grid-auto-columns/rows})
 * (Grid G0, 2026-07-31: consult-codex-2026-07-31-grid.txt §2). The initial subset supports
 * only fixed lengths, {@code auto}, and {@code fr}. Parsing expands {@code repeat(integer, ...)}
 * (the 4096-track limit protects resources).
 *
 * <p>
 * Extensions on 2026-08-29: {@code %} ({@link Percentage}, made absolute at layout time
 * against the container's content width), {@code min-content}/{@code max-content},
 * {@code repeat(auto-fill|auto-fit, ...)} ({@link AutoRepeat}, expanded at layout time
 * when the container width is known), and line names ({@link #getLineNames}).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class GridTrackListValue implements Value {
	/** {@code none} (no explicit tracks = one implicit auto column). */
	public static final GridTrackListValue NONE_VALUE = new GridTrackListValue(List.of(), List.of(List.of()), false);

	/** The size of a single track. */
	public sealed interface TrackSize permits Fixed, Auto, Fr, MinMax, Percentage, MinContent, MaxContent,
			AutoRepeat {
	}

	/** An absolute length (made absolute at the computed-value stage, in pt). */
	public record Fixed(double length) implements TrackSize {
		@Override
		public String toString() {
			return this.length + "pt";
		}
	}

	/** Content-dependent. */
	public record Auto() implements TrackSize {
		public static final Auto INSTANCE = new Auto();

		@Override
		public String toString() {
			return "auto";
		}
	}


	/** Weight for distributing remaining space (nonnegative). */
	public record Fr(double weight) implements TrackSize {
		@Override
		public String toString() {
			return this.weight + "fr";
		}
	}

	/**
	 * {@code minmax(min, max)} (2026-08-29: css-grid-1 §7.2.1/§11.5).
	 * Retains both bounds; {@code BasicGridTrackSizing} resolves them with the specification's
	 * track sizing algorithm. The min side initializes the base size (fixed length: that value;
	 * min-content/auto: the content's min-content; max-content: the content's max-content).
	 * The max side initializes the growth limit (fixed length: that value; fr: infinity, for
	 * distributing remaining space; auto/max-content: the content's max-content;
	 * min-content: the content's min-content).
	 *
	 * <p>
	 * The {@code ZeroMinFr} added on 2026-08-19 ({@code minmax(0, <fr>)}, the form produced
	 * by Tailwind's {@code grid-cols-N}) was folded into this general form:
	 * {@code MinMax(Fixed(0), Fr(w))}. Without preserving a minimum of zero, a long unbreakable
	 * line widens the track and even increases the wrapping width of the body text.
	 * </p>
	 *
	 * @param min Fixed/Percentage/MinContent/MaxContent/Auto
	 * @param max Fixed/Percentage/Fr/MinContent/MaxContent/Auto
	 */
	public record MinMax(TrackSize min, TrackSize max) implements TrackSize {
		public MinMax {
			if (min instanceof Fr || min instanceof MinMax || min instanceof AutoRepeat || max instanceof MinMax
					|| max instanceof AutoRepeat) {
				throw new IllegalArgumentException("minmax(" + min + "," + max + ")");
			}
		}

		@Override
		public String toString() {
			return "minmax(" + this.min + "," + this.max + ")";
		}
	}

	/**
	 * A {@code %} track (2026-08-29). A ratio to the Grid container's content-box inline size
	 * ({@code 25%} becomes 0.25), plus an absolute part for {@code calc()} ({@link #resolve}). During intrinsic
	 * sizing, when the reference width is indefinite, treats it as {@code auto}, as the specification requires.
	 */
	public record Percentage(double ratio, double offset) implements TrackSize {
		/** A plain {@code %} track. */
		public Percentage(final double ratio) {
			this(ratio, 0);
		}

		/**
		 * The track size against reference width {@code reference}: {@code offset} is the absolute part of a
		 * {@code calc()} mixing a percentage and a length ({@code calc(50% + 10px)}, in pt; 2026-10-08). Such a
		 * track used to drop the whole {@code grid-template-columns} declaration. A negative result counts as 0.
		 */
		public double resolve(final double reference) {
			return Math.max(0, this.ratio * reference + this.offset);
		}

		@Override
		public String toString() {
			return this.offset == 0 ? (this.ratio * 100) + "%"
					: "calc(" + (this.ratio * 100) + "% + " + this.offset + "pt)";
		}
	}

	/** {@code min-content} (2026-08-29): fixed to the content's min-content, without growth. */
	public record MinContent() implements TrackSize {
		public static final MinContent INSTANCE = new MinContent();

		@Override
		public String toString() {
			return "min-content";
		}
	}

	/** {@code max-content} (2026-08-29): fixed to the content's max-content, without stretching into remaining space. */
	public record MaxContent() implements TrackSize {
		public static final MaxContent INSTANCE = new MaxContent();

		@Override
		public String toString() {
			return "max-content";
		}
	}

	/**
	 * {@code repeat(auto-fill|auto-fit, <unit>)} (2026-08-29). At layout time, when the container
	 * width is known, expands to as many repetitions as fit ({@code GridBuilder}).
	 * The repetition count uses the <b>minimum width</b> of each unit track
	 * (the min in {@code minmax(min, max)}: {@code unitMinLength} +
	 * {@code unitMinRatio} × reference width). The expanded tracks themselves repeat
	 * {@code unit} (minmax retains the existing approximation using its maximum side).
	 *
	 * @param unit          the track sequence for one repetition
	 * @param unitLineNames line names within the unit (unit.size()+1 elements)
	 * @param unitMinLength the absolute-length part of one repetition's minimum width (pt, excluding gaps)
	 * @param unitMinRatio  the percentage part of one repetition's minimum width (a ratio to the reference width)
	 * @param fit           whether this is auto-fit (collapses trailing tracks without items)
	 */
	public record AutoRepeat(List<TrackSize> unit, List<List<String>> unitLineNames, double unitMinLength,
			double unitMinRatio, boolean fit) implements TrackSize {
		@Override
		public String toString() {
			return "repeat(" + (this.fit ? "auto-fit" : "auto-fill") + "," + this.unit + ")";
		}
	}

	private final List<TrackSize> tracks;

	/**
	 * Names for each line (tracks.size()+1 elements; an empty list for unnamed lines).
	 * For subgrid, the line-name sequence in {@code subgrid [a] [b] ...} (any number of elements).
	 */
	private final List<List<String>> lineNames;

	/** {@code subgrid} (css-grid-2, 2026-08-29): uses the spanned tracks of the parent grid as its own tracks. */
	private final boolean subgrid;

	private GridTrackListValue(final List<TrackSize> tracks, final List<List<String>> lineNames,
			final boolean subgrid) {
		this.tracks = List.copyOf(tracks);
		this.lineNames = List.copyOf(lineNames);
		this.subgrid = subgrid;
	}

	public static GridTrackListValue create(final List<TrackSize> tracks) {
		return create(tracks, null);
	}

	/** @param lineNames names for each line (tracks.size()+1 elements); null means no line names */
	public static GridTrackListValue create(final List<TrackSize> tracks, final List<List<String>> lineNames) {
		if (tracks.isEmpty()) {
			return NONE_VALUE;
		}
		List<List<String>> names = lineNames;
		if (names == null || names.size() != tracks.size() + 1) {
			names = emptyLineNames(tracks.size());
		}
		return new GridTrackListValue(tracks, names, false);
	}

	/**
	 * {@code subgrid <line-name-list>?} (2026-08-29). Has no tracks of its own
	 * (inherits the spanned parent tracks at layout time: {@code GridBuilder.bind}),
	 * and retains only the line-name sequence.
	 *
	 * @param lineNames names for each line, starting at the first line (null means none)
	 */
	public static GridTrackListValue createSubgrid(final List<List<String>> lineNames) {
		return new GridTrackListValue(List.of(), lineNames == null ? List.of() : lineNames, true);
	}

	/** {@code trackCount+1} empty line-name lists. */
	public static List<List<String>> emptyLineNames(final int trackCount) {
		final List<List<String>> names = new ArrayList<>(trackCount + 1);
		for (int i = 0; i <= trackCount; ++i) {
			names.add(List.of());
		}
		return names;
	}

	public List<TrackSize> getTracks() {
		return this.tracks;
	}

	/** Names for each line (tracks.size()+1 elements, 2026-08-29). */
	public List<List<String>> getLineNames() {
		return this.lineNames;
	}

	public boolean isNone() {
		return this.tracks.isEmpty() && !this.subgrid;
	}

	/** Whether this is {@code subgrid} (2026-08-29). If true, {@link #getTracks} is empty. */
	public boolean isSubgrid() {
		return this.subgrid;
	}

	@Override
	public String toString() {
		if (this.subgrid) {
			final StringBuilder buff = new StringBuilder("subgrid");
			for (final List<String> names : this.lineNames) {
				buff.append(" [").append(String.join(" ", names)).append(']');
			}
			return buff.toString();
		}
		if (this.isNone()) {
			return "none";
		}
		final StringBuilder buff = new StringBuilder();
		for (int i = 0; i < this.tracks.size(); ++i) {
			if (i > 0) {
				buff.append(' ');
			}
			if (!this.lineNames.get(i).isEmpty()) {
				buff.append('[').append(String.join(" ", this.lineNames.get(i))).append("] ");
			}
			buff.append(this.tracks.get(i));
		}
		if (!this.lineNames.get(this.tracks.size()).isEmpty()) {
			buff.append(" [").append(String.join(" ", this.lineNames.get(this.tracks.size()))).append(']');
		}
		return buff.toString();
	}
}
