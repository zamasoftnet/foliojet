package net.zamasoft.foliojet.css.value;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code grid-template-columns/rows}(および{@code grid-auto-columns/rows})の
 * トラックリストです(Grid G0、2026-07-31——consult-codex-2026-07-31-grid.txt
 * §2)。初期サブセットは固定長・{@code auto}・{@code fr}のみで、
 * {@code repeat(整数, ...)}は解析時に展開済み(トラック数上限4096は資源防御)。
 *
 * <p>
 * 2026-08-29の拡張: {@code %}({@link Percentage}——コンテナの内容幅基準で
 * レイアウト時に絶対化)、{@code min-content}/{@code max-content}、
 * {@code repeat(auto-fill|auto-fit, ...)}({@link AutoRepeat}——コンテナ幅が
 * 決まるレイアウト時に展開)、線名({@link #getLineNames})。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class GridTrackListValue implements Value {
	/** {@code none}(明示トラックなし=1列のimplicit auto)。 */
	public static final GridTrackListValue NONE_VALUE = new GridTrackListValue(List.of(), List.of(List.of()));

	/**
	 * トラック1本の寸法です。
	 */
	public sealed interface TrackSize permits Fixed, Auto, Fr, ZeroMinFr, Percentage, MinContent, MaxContent,
			AutoRepeat {
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

	/**
	 * {@code minmax(0, <fr>)}(2026-08-19)。frと同じ残余分配をするが、
	 * <b>最小値0が明示されている</b>ため内容のmin-contentで基礎幅が
	 * 膨らまない。実物のWebで頻出(Tailwindの{@code grid-cols-N}は
	 * {@code repeat(N, minmax(0,1fr))})で、これをただの{@code fr}へ
	 * 潰すと、コードブロック等の分割不能な長い行がトラックを押し広げ、
	 * <b>同じgrid内の本文の折り返し幅まで広がる</b>(tailwind-v4で実測:
	 * 版面523ptに対しトラック680pt、本文が右へはみ出す)。
	 */
	public record ZeroMinFr(double weight) implements TrackSize {
		@Override
		public String toString() {
			return "minmax(0," + this.weight + "fr)";
		}
	}

	/**
	 * {@code %}トラック(2026-08-29)。Gridコンテナのcontent-box行幅に
	 * 対する比({@code 25%}→0.25)。基準幅が未確定の固有寸法計測では
	 * 仕様どおり{@code auto}として扱う。
	 */
	public record Percentage(double ratio) implements TrackSize {
		@Override
		public String toString() {
			return (this.ratio * 100) + "%";
		}
	}

	/** {@code min-content}(2026-08-29)——内容のmin-contentで固定、伸びない。 */
	public record MinContent() implements TrackSize {
		public static final MinContent INSTANCE = new MinContent();

		@Override
		public String toString() {
			return "min-content";
		}
	}

	/** {@code max-content}(2026-08-29)——内容のmax-contentで固定、残余stretchしない。 */
	public record MaxContent() implements TrackSize {
		public static final MaxContent INSTANCE = new MaxContent();

		@Override
		public String toString() {
			return "max-content";
		}
	}

	/**
	 * {@code repeat(auto-fill|auto-fit, <unit>)}(2026-08-29)。コンテナ幅が
	 * 決まるレイアウト時に「収まるだけ」の回数へ展開する
	 * ({@code GridBuilder})。回数の判定には各unitトラックの<b>最小幅</b>
	 * ({@code minmax(min, max)}のmin——{@code unitMinLength}+
	 * {@code unitMinRatio}×基準幅)を使い、展開後のトラック自体は
	 * {@code unit}(minmaxは既存どおり最大値側の近似)を並べる。
	 *
	 * @param unit          1回分のトラック列
	 * @param unitLineNames unit内の線名(unit.size()+1要素)
	 * @param unitMinLength 1回分の最小幅の絶対長部分(pt、gap抜き)
	 * @param unitMinRatio  1回分の最小幅の%部分(基準幅に対する比)
	 * @param fit           auto-fit(item無しの末尾トラックを潰す)か
	 */
	public record AutoRepeat(List<TrackSize> unit, List<List<String>> unitLineNames, double unitMinLength,
			double unitMinRatio, boolean fit) implements TrackSize {
		@Override
		public String toString() {
			return "repeat(" + (this.fit ? "auto-fit" : "auto-fill") + "," + this.unit + ")";
		}
	}

	private final List<TrackSize> tracks;

	/** 各線の名前(tracks.size()+1要素。名前の無い線は空リスト)。 */
	private final List<List<String>> lineNames;

	private GridTrackListValue(final List<TrackSize> tracks, final List<List<String>> lineNames) {
		this.tracks = List.copyOf(tracks);
		this.lineNames = List.copyOf(lineNames);
	}

	public static GridTrackListValue create(final List<TrackSize> tracks) {
		return create(tracks, null);
	}

	/**
	 * @param lineNames 各線の名前(tracks.size()+1要素)。nullなら線名なし
	 */
	public static GridTrackListValue create(final List<TrackSize> tracks, final List<List<String>> lineNames) {
		if (tracks.isEmpty()) {
			return NONE_VALUE;
		}
		List<List<String>> names = lineNames;
		if (names == null || names.size() != tracks.size() + 1) {
			names = emptyLineNames(tracks.size());
		}
		return new GridTrackListValue(tracks, names);
	}

	/** {@code trackCount+1}本の空の線名リストです。 */
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

	/** 各線の名前です(tracks.size()+1要素、2026-08-29)。 */
	public List<List<String>> getLineNames() {
		return this.lineNames;
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
