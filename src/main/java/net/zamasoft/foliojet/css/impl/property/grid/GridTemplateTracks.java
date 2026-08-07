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
 * {@code grid-template-columns}/{@code grid-template-rows}です(Grid G0、
 * consult-codex-2026-07-31-grid.txt §2)。
 * {@code none | <track-size>+}——track-sizeは固定長・{@code auto}・
 * {@code <number>fr}のみ。{@code repeat(<正整数>, <track-size>+)}は解析時に
 * 展開する(展開後4096トラック上限=資源防御。超過は宣言無効)。
 * named line・auto-fill/fit・%は初期サブセット外(宣言無効)。
 *
 * <p>
 * <b>{@code minmax()}・{@code max()}/{@code min()}は仕様外の近似対応</b>
 * (2026-08-06)。本来のGrid track sizing algorithmは実装していない
 * ——{@code minmax(min, max)}は<b>最大値だけを採用し最小値は捨てる</b>
 * (実物コーパスで多い{@code minmax(0,1fr)}・{@code minmax(200px,1fr)}や、
 * yahoo.co.jpの{@code minmax(30px,auto)}に対し、厳密な仕様準拠より
 * 「見た目が近い」ことを優先する現実的な割り切り。詳細は
 * {@code docs/PLAN.md}ではなく本クラスのjavadocのみに記録——正式な
 * サブセット定義には含めない)。{@code max()}/{@code min()}は長さの
 * 引数だけを対象に、比較して1本の固定長トラックへ畳み込む
 * (yahoo.co.jpの{@code max(44px,4.4rem)}のような単純な用途のみ)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class GridTemplateTracks extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo COLUMNS = new GridTemplateTracks("grid-template-columns");

	public static final PrimitivePropertyInfo ROWS = new GridTemplateTracks("grid-template-rows");

	/** 展開後トラック数の上限(資源防御——レイアウト仕様ではない)。 */
	public static final int MAX_TRACKS = 4096;

	public static GridTrackListValue getColumns(CSSStyle style) {
		return (GridTrackListValue) style.get(COLUMNS);
	}

	public static GridTrackListValue getRows(CSSStyle style) {
		return (GridTrackListValue) style.get(ROWS);
	}

	protected GridTemplateTracks(final String name) {
		super(name);
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
		final List<GridTrackListValue.TrackSize> tracks = new ArrayList<>(raw.tracks.size());
		for (final Object t : raw.tracks) {
			if (t instanceof GridTrackListValue.TrackSize sized) {
				tracks.add(sized);
			} else if (t instanceof RawMinMaxFunc minMaxFunc) {
				tracks.add(new GridTrackListValue.Fixed(minMaxFunc.resolve(style)));
			} else {
				final Value abs = ValueUtils.emExToAbsoluteLength((Value) t, style);
				tracks.add(new GridTrackListValue.Fixed(((AbsoluteLengthValue) abs).getLength()));
			}
		}
		return GridTrackListValue.create(tracks);
	}

	/** 解析結果の中間形(固定長トラックはValueのまま=computedで絶対化)。 */
	private record RawTrackList(List<Object> tracks) implements Value {
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
				final Value abs = ValueUtils.emExToAbsoluteLength(arg, style);
				final double len = ((AbsoluteLengthValue) abs).getLength();
				if (this.isMax ? len > best : len < best) {
					best = len;
				}
			}
			return best;
		}
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.size() == 1 && tokens.eat("none")) {
			return GridTrackListValue.NONE_VALUE;
		}
		final List<Object> tracks = new ArrayList<>();
		while (tokens.hasNext()) {
			this.parseTrack(tokens, ua, tracks, true);
		}
		if (tracks.isEmpty()) {
			throw new PropertyException();
		}
		return new RawTrackList(tracks);
	}

	/** 1トラック(またはrepeat())を読み取ってtracksへ追加します。 */
	private void parseTrack(final TokenStream tokens, final UserAgent ua, final List<Object> tracks,
			final boolean allowRepeat) throws PropertyException {
		final CssToken token = tokens.peek();
		if (token instanceof CssToken.Func func && func.is("repeat")) {
			if (!allowRepeat) {
				throw new PropertyException();
			}
			tokens.next();
			final TokenStream inner = func.argStream();
			final CssToken.Num count = inner.number();
			if (count == null || !count.integer() || count.intValue() < 1 || !inner.eatComma()) {
				throw new PropertyException();
			}
			final List<Object> unit = new ArrayList<>();
			while (inner.hasNext()) {
				this.parseTrack(inner, ua, unit, false);
			}
			if (unit.isEmpty()) {
				throw new PropertyException();
			}
			if ((long) count.intValue() * unit.size() + tracks.size() > MAX_TRACKS) {
				throw new PropertyException();
			}
			for (int i = 0; i < count.intValue(); ++i) {
				tracks.addAll(unit);
			}
			return;
		}
		if (token instanceof CssToken.Func func && func.is("minmax")) {
			// 仕様外の近似: 最小値は捨て、最大値だけをトラックサイズとして
			// 採用する(クラスjavadoc参照)
			tokens.next();
			final List<TokenStream> args = func.argStream().splitComma();
			if (args.size() != 2) {
				throw new PropertyException();
			}
			final CssToken maxToken = args.get(1).next();
			if (maxToken == null || args.get(1).hasNext()) {
				throw new PropertyException();
			}
			this.parseLeafToken(maxToken, ua, tracks);
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
			tracks.add(new RawMinMaxFunc(isMax, lengths));
			if (tracks.size() > MAX_TRACKS) {
				throw new PropertyException();
			}
			return;
		}
		tokens.next();
		this.parseLeafToken(token, ua, tracks);
	}

	/** {@code auto}・{@code <flex>}・{@code <length>}の単一トラック片を読み取ってtracksへ追加します。 */
	private void parseLeafToken(final CssToken token, final UserAgent ua, final List<Object> tracks)
			throws PropertyException {
		if (token instanceof CssToken.Ident ident && ident.is("auto")) {
			tracks.add(GridTrackListValue.Auto.INSTANCE);
		} else if (token instanceof CssToken.Dim dim && dim.unitText().equalsIgnoreCase("fr")) {
			if (dim.value() < 0) {
				throw new PropertyException();
			}
			tracks.add(new GridTrackListValue.Fr(dim.value()));
		} else {
			if (token instanceof CssToken.Percent) {
				// %トラックは初期サブセット外(consult §2)
				throw new PropertyException();
			}
			final Value length = ValueUtils.toLength(ua, token);
			if (length == null) {
				throw new PropertyException();
			}
			tracks.add(length);
		}
		if (tracks.size() > MAX_TRACKS) {
			throw new PropertyException();
		}
	}
}
