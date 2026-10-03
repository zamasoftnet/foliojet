package net.zamasoft.foliojet.ua;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

/** 文書に一つの脚注領域です。ページ名・ページ擬似クラスには依存しません。 */
public final class FootnoteArea {
	public enum Position {
		BLOCK_END,

		/** 用紙の地の帯。 */
		BOTTOM,

		/** 用紙の天の帯(頭注、2026-09-11)。 */
		TOP
	}

	/**
	 * 用紙の端に帯を取る配置か(天か地)。
	 *
	 * <p>
	 * {@code BLOCK_END}と違い、帯はページ開始時に一度だけ予約され、段の
	 * 高さに差を作らない。天地どちらも版面の行方向(縦組みページなら用紙の
	 * 縦方向)を削るので、勘定はほぼ同じ経路を通る。
	 * </p>
	 */
	public boolean isPageBand() {
		return this.position == Position.BOTTOM || this.position == Position.TOP;
	}

	/** 天の帯(頭注)か。 */
	public boolean isHeadBand() {
		return this.position == Position.TOP;
	}

	/**
	 * 本文と脚注の間の区切り線です(2026-10-04、{@code @footnote}の{@code border-top}。
	 * TECH-20261003-004 の⑤)。指定が無ければ{@code null}で、UAの既定の線
	 * (0.5pt・黒・版面の行方向の 1/3)を引く。指定があれば領域の幅いっぱいに
	 * その太さ・色で引き、太さ 0(none 等)なら引かない。
	 */
	public record Separator(double thickness, net.zamasoft.pdfg2d.gc.paint.Color color) {
	}

	public static final FootnoteArea DEFAULT = new FootnoteArea(Position.BLOCK_END, null, null, 0, null);

	public final Position position;

	/** nullならページの書字方向に従います。 */
	public final WritingMode flow;

	/** pt単位の帯の寸法(間隙込み)。nullならautoです。 */
	public final Double height;

	/** pt単位の帯の下限です。 */
	public final double minHeight;

	/** 区切り線の指定(nullはUAの既定の線)。 */
	public final Separator separator;

	private FootnoteArea(final Position position, final WritingMode flow, final Double height, final double minHeight,
			final Separator separator) {
		this.position = java.util.Objects.requireNonNull(position);
		this.flow = flow;
		this.height = height;
		this.minHeight = minHeight;
		this.separator = separator;
	}

	public FootnoteArea withSeparator(final Separator separator) {
		return new FootnoteArea(this.position, this.flow, this.height, this.minHeight, separator);
	}

	public FootnoteArea withPosition(final Position position) {
		return this.position == position ? this : new FootnoteArea(position, this.flow, this.height, this.minHeight, this.separator);
	}

	public FootnoteArea withFlow(final WritingMode flow) {
		return this.flow == flow ? this : new FootnoteArea(this.position, flow, this.height, this.minHeight, this.separator);
	}

	public boolean isHeightFixed() {
		return this.height != null;
	}

	public FootnoteArea withHeight(final Double height) {
		if (height != null && (!Double.isFinite(height) || height < 0)) throw new IllegalArgumentException();
		return java.util.Objects.equals(this.height, height) ? this
				: new FootnoteArea(this.position, this.flow, height, this.minHeight, this.separator);
	}

	public FootnoteArea withMinHeight(final double minHeight) {
		if (!Double.isFinite(minHeight) || minHeight < 0) throw new IllegalArgumentException();
		return this.minHeight == minHeight ? this : new FootnoteArea(this.position, this.flow, this.height, minHeight, this.separator);
	}
}
