package net.zamasoft.foliojet.layout.box.params;

/**
 * ページフロート({@code float: top} / {@code float: bottom})の配置です
 * (2026-08-02——PLAN §2の1位。書籍組版の図表をページ端へ寄せる)。
 *
 * <p>
 * {@link FootnotePos}と同じく{@link FloatPos}を継承して
 * {@code PosType.FLOAT}のまま流し、分離builderのライフサイクル
 * (container builderのpush/pop・rangeのseal)を再利用する。終了時に
 * 親への{@code addBound}ではなくページ台帳({@code RootBuilder})へ
 * 渡る点だけが左右floatと異なる。上端フロートは配置後、Root座標の
 * 行走査に限って二次元排除域として使われる。
 * </p>
 */
public final class PageFloatPos extends FloatPos {

	/** ページ上端へ寄せるか(falseは下端)。 */
	public final boolean top;

	/**
	 * 上下が物理の向きか({@code top}・{@code bottom}。falseは論理の {@code block-start}・{@code block-end})。
	 * 横組みでは同じ。縦組みの下端は、物理なら用紙の下(行の進む向きの末尾)に置いて行を短くし、論理なら
	 * ブロックの末尾(縦書きの左端/右端)に置く(2026-10-05)。縦組みの上端はどちらも行の始まり側で同じ。
	 */
	public final boolean physical;

	public PageFloatPos(final boolean top, final boolean physical) {
		this.top = top;
		this.physical = physical;
	}
}
