package net.zamasoft.foliojet.layout.box.params;

/**
 * {@code float: footnote}の配置です(脚注F2、2026-07-31——設計は
 * consult-codex-2026-07-31-footnote.txt §3)。
 *
 * <p>
 * {@link FloatPos}を継承して{@code PosType.FLOAT}のまま流すことで、
 * {@code DocumentBuilder.startBox}の「本文から分離したbuilderで組む」
 * ライフサイクル(container builderのpush/pop・rangeのseal)を
 * そのまま再利用する。終了時({@code endBox}のFLOAT分岐)だけ、親への
 * {@code addBound}ではなくページ脚注台帳({@code RootBuilder})へ
 * 引き渡す点が左右floatと異なる。回り込み幾何(ExclusionSpace)には
 * 一切関与しない。
 * </p>
 */
public final class FootnotePos extends FloatPos {
}
