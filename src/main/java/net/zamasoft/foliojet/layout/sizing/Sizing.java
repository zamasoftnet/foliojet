package net.zamasoft.foliojet.layout.sizing;

/**
 * Shared sizing calculations.
 *
 * @author MIYABE Tatsuhiko
 */
public final class Sizing {
	private Sizing() {
		// utility
	}

	/**
	 * Returns the fit-content (shrink-to-fit) size. SPEC CSS2.1 10.3.5
	 * {@code max(preferredMin, min(available, preferred))}
	 *
	 * @param preferredMin Min-content size
	 * @param preferred    Max-content size (preferred size)
	 * @param available    Available size
	 * @return fit-content size
	 */
	public static double fitContent(double preferredMin, double preferred, double available) {
		return Math.max(preferredMin, Math.min(available, preferred));
	}
}
