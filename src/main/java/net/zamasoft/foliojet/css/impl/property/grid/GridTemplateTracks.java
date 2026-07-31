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
 * named line・minmax()・auto-fill/fit・%は初期サブセット外(宣言無効)。
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
		tokens.next();
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
