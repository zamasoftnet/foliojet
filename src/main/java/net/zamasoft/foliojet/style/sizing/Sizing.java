package net.zamasoft.foliojet.style.sizing;

/**
 * サイズ決定の共通計算です。
 *
 * @author MIYABE Tatsuhiko
 */
public final class Sizing {
	private Sizing() {
		// utility
	}

	/**
	 * fit-content(shrink-to-fit)寸法を返します。 SPEC CSS2.1 10.3.5
	 * {@code max(preferredMin, min(available, preferred))}
	 *
	 * @param preferredMin 最小内容寸法
	 * @param preferred    最大内容寸法(推奨寸法)
	 * @param available    利用可能寸法
	 * @return fit-content 寸法
	 */
	public static double fitContent(double preferredMin, double preferred, double available) {
		return Math.max(preferredMin, Math.min(available, preferred));
	}
}
