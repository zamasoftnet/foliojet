package net.zamasoft.foliojet.css.impl.property.grid;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.GridTrackListValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.util.CalcValueUtils;
import net.zamasoft.foliojet.css.value.LengthValue;

/**
 * {@code grid-template-columns}/{@code grid-template-rows} and
 * {@code grid-auto-columns}/{@code grid-auto-rows} (Grid G0,
 * consult-codex-2026-07-31-grid.txt §2).
 * {@code none | <track-size>+}: track-size is a fixed length, {@code auto}, or
 * {@code <number>fr}. Expands {@code repeat(<positive integer>, <track-size>+)} during parsing
 * (limit of 4096 expanded tracks for resource protection; exceeding it invalidates the declaration).
 *
 * <p>
 * Extensions on 2026-08-29 (unsupported values found in the 50-site sweep):
 * {@code %} (relative to container content width, {@link GridTrackListValue.Percentage}),
 * {@code min-content}/{@code max-content}, line names {@code [a b]},
 * {@code repeat(auto-fill|auto-fit, ...)} (expanded during layout once container width is known,
 * {@link GridTrackListValue.AutoRepeat}), {@code fit-content(x)}
 * (approximated as {@code auto}), and {@code subgrid}. {@code grid-auto-*} (implicit)
 * rejects line names, {@code none}, {@code subgrid}, and {@code repeat()}.
 * </p>
 *
 * <p>
 * Since 2026-08-29, {@code minmax(min, max)} retains both bounds
 * ({@link GridTrackListValue.MinMax}). {@code BasicGridTrackSizing} resolves them using
 * the css-grid-1 §11.5 track sizing algorithm (base size=min, growth limit=max).
 * Previously, it approximated using only the maximum
 * (2026-08-06, e.g. {@code minmax(30px,auto)} on yahoo.co.jp).
 * Uses min to determine the count for {@code repeat(auto-fill, minmax(min, max))}.
 * {@code subgrid <line-name-list>?} uses {@link GridTrackListValue#createSubgrid},
 * inheriting the spanned parent grid tracks during layout ({@code GridBuilder.bind};
 * see its Javadoc for the approximation when inheritance is impossible).
 * </p>
 *
 * <p>
 * <b>{@code max()}/{@code min()} have nonstandard approximate support</b> (2026-08-06):
 * compares only length arguments and collapses them into a single fixed-length track
 * (for simple uses such as {@code max(44px,4.4rem)} on yahoo.co.jp).
 * Recorded only in this class's Javadoc, not in the development plan;
 * excluded from the formal subset definition.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class GridTemplateTracks extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo COLUMNS = new GridTemplateTracks("grid-template-columns", false);

	public static final PrimitivePropertyInfo ROWS = new GridTemplateTracks("grid-template-rows", false);

	/** {@code grid-auto-columns} (2026-08-29). Defaults to {@code auto} (=NONE_VALUE). */
	public static final PrimitivePropertyInfo AUTO_COLUMNS = new GridTemplateTracks("grid-auto-columns", true);

	/** {@code grid-auto-rows} (2026-08-29). Defaults to {@code auto} (=NONE_VALUE). */
	public static final PrimitivePropertyInfo AUTO_ROWS = new GridTemplateTracks("grid-auto-rows", true);

	/** Maximum expanded track count (resource protection, not a layout specification). */
	public static final int MAX_TRACKS = 4096;

	public static GridTrackListValue getColumns(CSSStyle style) {
		return (GridTrackListValue) style.get(COLUMNS);
	}

	public static GridTrackListValue getRows(CSSStyle style) {
		return (GridTrackListValue) style.get(ROWS);
	}

	public static GridTrackListValue get(CSSStyle style, PrimitivePropertyInfo info) {
		return (GridTrackListValue) style.get(info);
	}

	/** Whether this is for implicit tracks ({@code grid-auto-*}). */
	private final boolean implicit;

	protected GridTemplateTracks(final String name, final boolean implicit) {
		super(name);
		this.implicit = implicit;
	}

	public Value getDefault(CSSStyle style) {
		return GridTrackListValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		// Font-relative lengths are unresolved during parsing (RawFixed); make them absolute at computation.
		if (!(value instanceof RawTrackList raw)) {
			return value;
		}
		return GridTrackListValue.create(resolveTracks(raw.tracks, style), raw.lineNames);
	}

	private static List<GridTrackListValue.TrackSize> resolveTracks(final List<Object> rawTracks,
			final CSSStyle style) {
		final List<GridTrackListValue.TrackSize> tracks = new ArrayList<>(rawTracks.size());
		for (final Object t : rawTracks) {
			if (t instanceof GridTrackListValue.TrackSize sized) {
				tracks.add(sized);
			} else if (t instanceof RawMinMaxFunc minMaxFunc) {
				tracks.add(new GridTrackListValue.Fixed(minMaxFunc.resolve(style)));
			} else if (t instanceof RawMinMax minMax) {
				tracks.add(new GridTrackListValue.MinMax(resolveLeaf(minMax.min, style),
						resolveLeaf(minMax.max, style)));
			} else if (t instanceof RawAutoRepeat autoRepeat) {
				double minLength = 0, minRatio = 0;
				for (final Object min : autoRepeat.mins) {
					if (min instanceof Double ratio) {
						minRatio += ratio;
					} else {
						minLength += toAbsolute((Value) min, style);
					}
				}
				tracks.add(new GridTrackListValue.AutoRepeat(resolveTracks(autoRepeat.unit, style),
						autoRepeat.unitLineNames, minLength, minRatio, autoRepeat.fit));
			} else {
				tracks.add(new GridTrackListValue.Fixed(toAbsolute((Value) t, style)));
			}
		}
		return tracks;
	}

	/** Converts one minmax() bound (TrackSize or length Value) to an absolute value. */
	private static GridTrackListValue.TrackSize resolveLeaf(final Object raw, final CSSStyle style) {
		if (raw instanceof GridTrackListValue.TrackSize sized) {
			return sized;
		}
		return new GridTrackListValue.Fixed(toAbsolute((Value) raw, style));
	}

	private static double toAbsolute(final Value raw, final CSSStyle style) {
		final Value abs = ValueUtils.emExToAbsoluteLength(raw, style);
		return ((AbsoluteLengthValue) abs).getLength();
	}

	/** Intermediate parsed form (fixed-length tracks remain Value; made absolute at computation). */
	private record RawTrackList(List<Object> tracks, List<List<String>> lineNames) implements Value {
	}

	/**
	 * Intermediate form for {@code max()}/{@code min()} used as a track size
	 * (nonstandard approximation; see class Javadoc). Makes arguments absolute at computation
	 * before comparing them (not during parsing, to resolve mixed em/rem correctly).
	 */
	private record RawMinMaxFunc(boolean isMax, List<Value> args) implements Value {
		double resolve(CSSStyle style) {
			double best = this.isMax ? -Double.MAX_VALUE : Double.MAX_VALUE;
			for (final Value arg : this.args) {
				final double len = toAbsolute(arg, style);
				if (this.isMax ? len > best : len < best) {
					best = len;
				}
			}
			return best;
		}
	}

	/**
	 * Intermediate form for {@code minmax(min, max)} (2026-08-29). Each bound is a TrackSize
	 * (auto/min-content/max-content/fr/%) or a length Value not yet made absolute.
	 */
	private record RawMinMax(Object min, Object max) implements Value {
	}

	/**
	 * Intermediate form for {@code repeat(auto-fill|auto-fit, ...)} (2026-08-29).
	 * {@code mins} contains each unit track's minimum width for determining the count
	 * (a length {@code Value} or percentage ratio {@code Double}).
	 */
	private record RawAutoRepeat(List<Object> unit, List<List<String>> unitLineNames, List<Object> mins,
			boolean fit) implements Value {
	}

	/** Track and line-name sequences during parsing (maintains names.size()==tracks.size()+1). */
	private static final class Accumulator {
		final List<Object> tracks = new ArrayList<>();
		final List<List<String>> names = new ArrayList<>();
		/** Destination for collecting minimum widths inside auto-repeat (null outside auto-repeat). */
		final List<Object> mins;
		boolean hasAutoRepeat;

		Accumulator(final List<Object> mins) {
			this.mins = mins;
			this.names.add(new ArrayList<>());
		}

		void addTrack(final Object track, final Object min) throws PropertyException {
			if (this.mins != null) {
				if (min == null) {
					// auto-repeat units must have fixed widths (or one fixed minmax bound).
					throw new PropertyException();
				}
				this.mins.add(min);
			}
			this.tracks.add(track);
			this.names.add(new ArrayList<>());
			if (this.tracks.size() > MAX_TRACKS) {
				throw new PropertyException();
			}
		}

		void addNames(final List<String> lineNames) {
			this.names.get(this.names.size() - 1).addAll(lineNames);
		}
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (!this.implicit && tokens.size() == 1 && tokens.eat("none")) {
			return GridTrackListValue.NONE_VALUE;
		}
		if (!this.implicit && tokens.peek() instanceof CssToken.Ident ident && ident.is("subgrid")) {
			// subgrid <line-name-list>? (css-grid-2 §7.1, 2026-08-29). Line names run
			// in order from the first line. Line-name lists with repeat() are unsupported (discarded).
			tokens.next();
			final List<List<String>> lineNames = new ArrayList<>();
			while (tokens.hasNext()) {
				final CssToken token = tokens.next();
				if (token instanceof CssToken.LineNames names) {
					lineNames.add(List.copyOf(names.names()));
				} else if (!(token instanceof CssToken.Func func && func.is("repeat"))) {
					throw new PropertyException();
				}
			}
			return GridTrackListValue.createSubgrid(lineNames);
		}
		final Accumulator acc = new Accumulator(null);
		while (tokens.hasNext()) {
			if (tokens.peek() instanceof CssToken.LineNames lineNames) {
				if (this.implicit) {
					throw new PropertyException();
				}
				tokens.next();
				acc.addNames(lineNames.names());
				continue;
			}
			this.parseTrack(tokens, ua, acc, !this.implicit);
		}
		if (acc.tracks.isEmpty()) {
			throw new PropertyException();
		}
		return new RawTrackList(acc.tracks, acc.names);
	}

	/** Reads one track (or repeat()) and adds it to acc. */
	private void parseTrack(final TokenStream tokens, final UserAgent ua, final Accumulator acc,
			final boolean allowRepeat) throws PropertyException {
		final CssToken token = tokens.peek();
		if (token instanceof CssToken.Func func && func.is("repeat")) {
			if (!allowRepeat) {
				throw new PropertyException();
			}
			tokens.next();
			final TokenStream inner = func.argStream();
			final CssToken.Num count = inner.number();
			boolean autoRepeat = false, fit = false;
			if (count == null) {
				if (inner.eat("auto-fill")) {
					autoRepeat = true;
				} else if (inner.eat("auto-fit")) {
					autoRepeat = true;
					fit = true;
				} else {
					throw new PropertyException();
				}
				if (acc.hasAutoRepeat || acc.mins != null) {
					throw new PropertyException(); // At most one auto-repeat; no nesting.
				}
			} else if (!count.integer() || count.intValue() < 1) {
				throw new PropertyException();
			}
			if (!inner.eatComma()) {
				throw new PropertyException();
			}
			final Accumulator unit = new Accumulator(autoRepeat ? new ArrayList<>() : null);
			while (inner.hasNext()) {
				if (inner.peek() instanceof CssToken.LineNames lineNames) {
					inner.next();
					unit.addNames(lineNames.names());
					continue;
				}
				this.parseTrack(inner, ua, unit, false);
			}
			if (unit.tracks.isEmpty()) {
				throw new PropertyException();
			}
			if (autoRepeat) {
				acc.hasAutoRepeat = true;
				acc.addTrack(new RawAutoRepeat(unit.tracks, unit.names, unit.mins, fit), null);
				return;
			}
			if ((long) count.intValue() * unit.tracks.size() + acc.tracks.size() > MAX_TRACKS) {
				throw new PropertyException();
			}
			for (int i = 0; i < count.intValue(); ++i) {
				acc.addNames(unit.names.get(0));
				for (int k = 0; k < unit.tracks.size(); ++k) {
					acc.addTrack(unit.tracks.get(k), null);
					acc.addNames(unit.names.get(k + 1));
				}
			}
			return;
		}
		if (token instanceof CssToken.Func func && func.is("minmax")) {
			// minmax(min, max) retains both bounds (2026-08-29; previously approximated
			// using only the maximum). min∈{length,%,min-content,max-content,auto},
			// max∈{length,%,fr,min-content,max-content,auto}. Inside auto-repeat,
			// use the fixed bound (min, otherwise max) to determine the count.
			tokens.next();
			final List<TokenStream> args = func.argStream().splitComma();
			if (args.size() != 2) {
				throw new PropertyException();
			}
			final CssToken minToken = args.get(0).next();
			final CssToken maxToken = args.get(1).next();
			if (minToken == null || args.get(0).hasNext() || maxToken == null || args.get(1).hasNext()) {
				throw new PropertyException();
			}
			final Object min = rawLeaf(ua, minToken);
			final Object max = rawLeaf(ua, maxToken);
			if (min == null || max == null || min instanceof GridTrackListValue.Fr) {
				throw new PropertyException();
			}
			Object repeatMin = null;
			if (acc.mins != null) {
				repeatMin = fixedExtent(ua, minToken);
				if (repeatMin == null) {
					repeatMin = fixedExtent(ua, maxToken);
				}
			}
			acc.addTrack(new RawMinMax(min, max), repeatMin);
			return;
		}
		if (token instanceof CssToken.Func func && (func.is("max") || func.is("min"))) {
			// Nonstandard approximation: compare only length arguments and collapse them
			// into a single fixed-length track (see class Javadoc).
			tokens.next();
			final boolean isMax = func.is("max");
			final List<TokenStream> args = func.argStream().splitComma();
			if (args.isEmpty()) {
				throw new PropertyException();
			}
			final List<Value> lengths = new ArrayList<>(args.size());
			for (final TokenStream arg : args) {
				final CssToken argToken = arg.next();
				if (argToken == null || arg.hasNext() || argToken instanceof CssToken.Percent) {
					throw new PropertyException();
				}
				final Value length = ValueUtils.toLength(ua, argToken);
				if (length == null) {
					throw new PropertyException();
				}
				lengths.add(length);
			}
			final RawMinMaxFunc raw = new RawMinMaxFunc(isMax, lengths);
			acc.addTrack(raw, acc.mins != null ? lengths.get(0) : null);
			return;
		}
		if (token instanceof CssToken.Func func && func.is("fit-content")) {
			// fit-content(x) approximates auto (2026-08-29; discards the upper-bound argument).
			tokens.next();
			acc.addTrack(GridTrackListValue.Auto.INSTANCE, null);
			return;
		}
		tokens.next();
		this.parseLeafToken(token, ua, acc, null);
	}

	/**
	 * Converts one minmax() bound token to an intermediate form (2026-08-29): auto/min-content/
	 * max-content/fr/% become TrackSize; lengths remain non-absolute Value. Null otherwise.
	 */
	private static Object rawLeaf(final UserAgent ua, final CssToken token) throws PropertyException {
		if (token instanceof CssToken.Ident ident) {
			if (ident.is("auto")) {
				return GridTrackListValue.Auto.INSTANCE;
			}
			if (ident.is("min-content")) {
				return GridTrackListValue.MinContent.INSTANCE;
			}
			if (ident.is("max-content")) {
				return GridTrackListValue.MaxContent.INSTANCE;
			}
			return null;
		}
		if (token instanceof CssToken.Dim dim && dim.unitText().equalsIgnoreCase("fr")) {
			if (dim.value() < 0) {
				throw new PropertyException();
			}
			return new GridTrackListValue.Fr(dim.value());
		}
		if (token instanceof CssToken.Percent percent) {
			if (percent.value() < 0) {
				throw new PropertyException();
			}
			return new GridTrackListValue.Percentage(percent.value() / 100.0);
		}
		if (token instanceof CssToken.Dim dim && dim.value() < 0 || token instanceof CssToken.Num num && num.value() < 0) {
			throw new PropertyException();
		}
		return toTrackLength(ua, token);
	}

	/**
	 * Resolves a track component length. Passes expressions such as {@code calc()} through
	 * the same entry point as other length properties ({@link CalcValueUtils#toCalc})
	 * (2026-08-30). Previously, only {@code ValueUtils.toLength} was tried, so
	 * {@code grid-template-columns: calc(20mm - 5mm) …} was discarded entirely;
	 * the spacer column became 0 and the type area shifted sideways (user report E-2).
	 */
	private static Value toTrackLength(final UserAgent ua, final CssToken token) {
		final Value length = ValueUtils.toLength(ua, token);
		if (length != null) {
			return length;
		}
		final Value calc = CalcValueUtils.toCalc(ua, token);
		return calc instanceof LengthValue ? calc : null;
	}

	/** Fixed-width token (length or %) for count calculation: length Value or % ratio Double; null otherwise. */
	private static Object fixedExtent(final UserAgent ua, final CssToken token) {
		if (token instanceof CssToken.Percent percent) {
			return percent.value() / 100.0;
		}
		if (token instanceof CssToken.Ident || token instanceof CssToken.Dim dim && dim.unitText().equalsIgnoreCase("fr")) {
			return null;
		}
		return toTrackLength(ua, token);
	}

	/**
	 * Reads a single track component of {@code auto}, {@code <flex>}, {@code min-content},
	 * {@code max-content}, {@code %}, or {@code <length>} and adds it to acc.
	 *
	 * @param minOverride minimum width inside auto-repeat (its own fixed width if null)
	 */
	private void parseLeafToken(final CssToken token, final UserAgent ua, final Accumulator acc,
			final Object minOverride) throws PropertyException {
		if (token instanceof CssToken.Ident ident && ident.is("auto")) {
			acc.addTrack(GridTrackListValue.Auto.INSTANCE, minOverride);
		} else if (token instanceof CssToken.Ident ident && ident.is("min-content")) {
			acc.addTrack(GridTrackListValue.MinContent.INSTANCE, minOverride);
		} else if (token instanceof CssToken.Ident ident && ident.is("max-content")) {
			acc.addTrack(GridTrackListValue.MaxContent.INSTANCE, minOverride);
		} else if (token instanceof CssToken.Dim dim && dim.unitText().equalsIgnoreCase("fr")) {
			if (dim.value() < 0) {
				throw new PropertyException();
			}
			acc.addTrack(new GridTrackListValue.Fr(dim.value()), minOverride);
		} else if (token instanceof CssToken.Percent percent) {
			if (percent.value() < 0) {
				throw new PropertyException();
			}
			final double ratio = percent.value() / 100.0;
			acc.addTrack(new GridTrackListValue.Percentage(ratio), minOverride != null ? minOverride : ratio);
		} else {
			final Value length = toTrackLength(ua, token);
			if (length == null) {
				throw new PropertyException();
			}
			acc.addTrack(length, minOverride != null ? minOverride : length);
		}
	}
}
