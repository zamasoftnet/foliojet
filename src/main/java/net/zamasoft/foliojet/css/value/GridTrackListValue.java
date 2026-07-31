package net.zamasoft.foliojet.css.value;

import java.util.List;

/**
 * {@code grid-template-columns/rows}のトラックリストです(Grid G0、
 * 2026-07-31——consult-codex-2026-07-31-grid.txt §2)。初期サブセットは
 * 固定長・{@code auto}・{@code fr}のみで、{@code repeat(整数, ...)}は
 * 解析時に展開済み(トラック数上限4096は資源防御)。
 *
 * @author MIYABE Tatsuhiko
 */
public final class GridTrackListValue implements Value {
	/** {@code none}(明示トラックなし=1列のimplicit auto)。 */
	public static final GridTrackListValue NONE_VALUE = new GridTrackListValue(List.of());

	/**
	 * トラック1本の寸法です。
	 */
	public sealed interface TrackSize permits Fixed, Auto, Fr {
	}

	/** 絶対長(computed時に絶対化済み、pt)。 */
	public record Fixed(double length) implements TrackSize {
		@Override
		public String toString() {
			return this.length + "pt";
		}
	}

	/** 内容依存。 */
	public record Auto() implements TrackSize {
		public static final Auto INSTANCE = new Auto();

		@Override
		public String toString() {
			return "auto";
		}
	}

	/** 残余分配の重み(非負)。 */
	public record Fr(double weight) implements TrackSize {
		@Override
		public String toString() {
			return this.weight + "fr";
		}
	}

	private final List<TrackSize> tracks;

	private GridTrackListValue(final List<TrackSize> tracks) {
		this.tracks = List.copyOf(tracks);
	}

	public static GridTrackListValue create(final List<TrackSize> tracks) {
		return tracks.isEmpty() ? NONE_VALUE : new GridTrackListValue(tracks);
	}

	public List<TrackSize> getTracks() {
		return this.tracks;
	}

	public boolean isNone() {
		return this.tracks.isEmpty();
	}

	@Override
	public String toString() {
		if (this.isNone()) {
			return "none";
		}
		final StringBuilder buff = new StringBuilder();
		for (int i = 0; i < this.tracks.size(); ++i) {
			if (i > 0) {
				buff.append(' ');
			}
			buff.append(this.tracks.get(i));
		}
		return buff.toString();
	}
}
