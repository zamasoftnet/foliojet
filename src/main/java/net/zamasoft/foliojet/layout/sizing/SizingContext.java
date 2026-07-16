package net.zamasoft.foliojet.layout.sizing;

import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * サイズ決定の制約空間です。包含コンテキストから導出され、
 * %の解決可否は percentBase の確定/不定(NONE)で表現します。
 *
 * <ul>
 * <li>availableLine — 行方向の利用可能寸法(fit-content の上限の基準)</li>
 * <li>percentBaseLine — 行方向の%基準(不定なら {@link LayoutUtils#NONE})</li>
 * <li>percentBasePage — ページ方向の%基準(不定なら {@link LayoutUtils#NONE})</li>
 * </ul>
 *
 * @author MIYABE Tatsuhiko
 */
public record SizingContext(SizingMode mode, double availableLine, double percentBaseLine, double percentBasePage) {
	/**
	 * ページ方向の%が解決可能であればtrueを返します。
	 *
	 * @return ページ方向の%基準が確定していればtrue
	 */
	public boolean isPagePercentDefinite() {
		return !LayoutUtils.isNone(this.percentBasePage);
	}
}
