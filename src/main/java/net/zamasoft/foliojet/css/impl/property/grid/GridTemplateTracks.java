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

/**
 * {@code grid-template-columns}/{@code grid-template-rows}および
 * {@code grid-auto-columns}/{@code grid-auto-rows}です(Grid G0、
 * consult-codex-2026-07-31-grid.txt §2)。
 * {@code none | <track-size>+}——track-sizeは固定長・{@code auto}・
 * {@code <number>fr}。{@code repeat(<正整数>, <track-size>+)}は解析時に
 * 展開する(展開後4096トラック上限=資源防御。超過は宣言無効)。
 *
 * <p>
 * 2026-08-29の拡張(50サイト掃過で見つかった未対応値):
 * {@code %}(コンテナ内容幅基準、{@link GridTrackListValue.Percentage})、
 * {@code min-content}/{@code max-content}、線名{@code [a b]}、
 * {@code repeat(auto-fill|auto-fit, ...)}(コンテナ幅が決まるレイアウト時に
 * 展開、{@link GridTrackListValue.AutoRepeat})、{@code fit-content(x)}
 * (→{@code auto}の近似)、{@code subgrid}。{@code grid-auto-*}(implicit)では
 * 線名・{@code none}・{@code subgrid}・{@code repeat()}を受理しない。
 * </p>
 *
 * <p>
 * {@code minmax(min, max)}は2026-08-29から両端を保持する
 * ({@link GridTrackListValue.MinMax})——{@code BasicGridTrackSizing}が
 * css-grid-1 §11.5のtrack sizing algorithm(base size=min側、growth
 * limit=max側)で解く。それまでは最大値だけを採る近似だった
 * (2026-08-06、yahoo.co.jpの{@code minmax(30px,auto)}等)。
 * {@code repeat(auto-fill, minmax(min, max))}の回数判定にはminを使う。
 * {@code subgrid <line-name-list>?}は{@link GridTrackListValue#createSubgrid}
 * ——親gridの跨ぐトラックをレイアウト時に継ぐ({@code GridBuilder.bind}。
 * 継げない場合の近似はそちらのjavadoc)。
 * </p>
 *
 * <p>
 * <b>{@code max()}/{@code min()}は仕様外の近似対応</b>(2026-08-06):
 * 長さの引数だけを対象に、比較して1本の固定長トラックへ畳み込む
 * (yahoo.co.jpの{@code max(44px,4.4rem)}のような単純な用途のみ。
 * {@code docs/PLAN.md}ではなく本クラスのjavadocのみに記録——正式な
 * サブセット定義には含めない)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class GridTemplateTracks extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo COLUMNS = new GridTemplateTracks("grid-template-columns", false);

	public static final PrimitivePropertyInfo ROWS = new GridTemplateTracks("grid-template-rows", false);

	/** {@code grid-auto-columns}(2026-08-29)。既定{@code auto}(=NONE_VALUE)。 */
	public static final PrimitivePropertyInfo AUTO_COLUMNS = new GridTemplateTracks("grid-auto-columns", true);

	/** {@code grid-auto-rows}(2026-08-29)。既定{@code auto}(=NONE_VALUE)。 */
	public static final PrimitivePropertyInfo AUTO_ROWS = new GridTemplateTracks("grid-auto-rows", true);

	/** 展開後トラック数の上限(資源防御——レイアウト仕様ではない)。 */
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

	/** implicitトラック用({@code grid-auto-*})か。 */
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
		// 解析時はfont相対長が未解決(RawFixed)——computedで絶対化する
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

	/** minmax()の片側(TrackSizeまたは長さValue)を絶対化します。 */
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

	/** 解析結果の中間形(固定長トラックはValueのまま=computedで絶対化)。 */
	private record RawTrackList(List<Object> tracks, List<List<String>> lineNames) implements Value {
	}

	/**
	 * {@code max()}/{@code min()}をトラックサイズとして使った場合の中間形
	 * (仕様外の近似対応、クラスjavadoc参照)。引数はcomputedで絶対化してから
	 * 比較する(em/rem混在を正しく解決するため、解析時に比較しない)。
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
	 * {@code minmax(min, max)}の中間形(2026-08-29)。両側はTrackSize
	 * (auto/min-content/max-content/fr/%)または未絶対化の長さValue。
	 */
	private record RawMinMax(Object min, Object max) implements Value {
	}

	/**
	 * {@code repeat(auto-fill|auto-fit, ...)}の中間形(2026-08-29)。
	 * {@code mins}は回数判定用の各unitトラックの最小幅
	 * (長さ{@code Value}または%比{@code Double})。
	 */
	private record RawAutoRepeat(List<Object> unit, List<List<String>> unitLineNames, List<Object> mins,
			boolean fit) implements Value {
	}

	/** 解析中のトラック列と線名列(names.size()==tracks.size()+1を保つ)。 */
	private static final class Accumulator {
		final List<Object> tracks = new ArrayList<>();
		final List<List<String>> names = new ArrayList<>();
		/** auto-repeat内での最小幅収集先(auto-repeat外ではnull)。 */
		final List<Object> mins;
		boolean hasAutoRepeat;

		Accumulator(final List<Object> mins) {
			this.mins = mins;
			this.names.add(new ArrayList<>());
		}

		void addTrack(final Object track, final Object min) throws PropertyException {
			if (this.mins != null) {
				if (min == null) {
					// auto-repeatのunitは固定幅(またはminmaxの片側が固定)のみ
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
			// subgrid <line-name-list>?(css-grid-2 §7.1、2026-08-29)。線名は
			// 先頭の線から順に並ぶ。repeat()付きの線名列は未対応(捨てる)
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

	/** 1トラック(またはrepeat())を読み取ってaccへ追加します。 */
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
					throw new PropertyException(); // auto-repeatは1つまで・入れ子不可
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
			// minmax(min, max)は両端を保持する(2026-08-29。以前は最大値だけの
			// 近似)。min∈{長さ,%,min-content,max-content,auto}、
			// max∈{長さ,%,fr,min-content,max-content,auto}。auto-repeat内では
			// 固定側(min、無ければmax)を回数判定に使う
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
			// 仕様外の近似: 長さの引数だけを対象に比較し、1本の固定長
			// トラックへ畳み込む(クラスjavadoc参照)
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
			// fit-content(x)はautoの近似(2026-08-29。引数の上限は捨てる)
			tokens.next();
			acc.addTrack(GridTrackListValue.Auto.INSTANCE, null);
			return;
		}
		tokens.next();
		this.parseLeafToken(token, ua, acc, null);
	}

	/**
	 * minmax()の片側トークンを中間形にします(2026-08-29): auto/min-content/
	 * max-content/fr/%はTrackSize、長さは未絶対化のValue。それ以外はnull。
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
		return ValueUtils.toLength(ua, token);
	}

	/** 固定幅トークン(長さ・%)の回数判定用の値です(長さValueまたは%比Double。それ以外null)。 */
	private static Object fixedExtent(final UserAgent ua, final CssToken token) {
		if (token instanceof CssToken.Percent percent) {
			return percent.value() / 100.0;
		}
		if (token instanceof CssToken.Ident || token instanceof CssToken.Dim dim && dim.unitText().equalsIgnoreCase("fr")) {
			return null;
		}
		return ValueUtils.toLength(ua, token);
	}

	/**
	 * {@code auto}・{@code <flex>}・{@code min-content}・{@code max-content}・
	 * {@code %}・{@code <length>}の単一トラック片を読み取ってaccへ追加します。
	 *
	 * @param minOverride auto-repeat内で使う最小幅(nullなら自身の固定幅)
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
			final Value length = ValueUtils.toLength(ua, token);
			if (length == null) {
				throw new PropertyException();
			}
			acc.addTrack(length, minOverride != null ? minOverride : length);
		}
	}
}
