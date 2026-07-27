package net.zamasoft.foliojet.layout.sizing;

/**
 * 固有寸法(内容に由来する寸法)です。
 * <ul>
 * <li>minContent — 最小内容寸法(分割不能な最長ランの行方向寸法)</li>
 * <li>maxContent — 最大内容寸法(折り返しなしで並べた場合の行方向寸法)</li>
 * <li>minPage — 最小ページ方向寸法</li>
 * <li>columnInflated — {@code minContent}が<b>段数倍</b>を含むか</li>
 * </ul>
 *
 * <p>
 * <b>{@code columnInflated}が要る理由</b>(2026-07-28)。段組の最小内容寸法は
 * 「段数 × 中身の最小内容寸法 + 段間」で、入れ子にすれば積で効く。この値を
 * {@code fit-content}の下限にすると、<b>紙の行軸をいくらでも超える</b>——
 * 段が4つあるだけで4倍である。しかし<b>段は狭くできる</b>(行軸を段数で
 * 割り直すだけ)ので、この下限は守らなくてよい。
 * </p>
 *
 * <p>
 * 一方、{@code height:150mm}の画像のように<b>作者が明示した不可分な箱</b>から
 * 来た最小内容寸法は守るべきで、紙に収めようと縮めても中身が余計にはみ出す
 * だけである。両者は値からは区別できないので、<b>段数倍が効いたかどうかを
 * 測定側で記録して運ぶ</b>。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public record IntrinsicSizes(double minContent, double maxContent, double minPage, boolean columnInflated) {
	public static final IntrinsicSizes ZERO = new IntrinsicSizes(0, 0, 0);

	/** 段数倍を含まない固有寸法({@code columnInflated = false})。 */
	public IntrinsicSizes(final double minContent, final double maxContent, final double minPage) {
		this(minContent, maxContent, minPage, false);
	}
}
