package net.zamasoft.foliojet.layout.sizing;

/**
 * 固有寸法(内容に由来する寸法)です。
 * <ul>
 * <li>minContent — 最小内容寸法(分割不能な最長ランの行方向寸法)</li>
 * <li>maxContent — 最大内容寸法(折り返しなしで並べた場合の行方向寸法)</li>
 * <li>minPage — 最小ページ方向寸法</li>
 * </ul>
 *
 * @author MIYABE Tatsuhiko
 */
public record IntrinsicSizes(double minContent, double maxContent, double minPage) {
	public static final IntrinsicSizes ZERO = new IntrinsicSizes(0, 0, 0);
}
