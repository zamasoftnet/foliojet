package net.zamasoft.foliojet.layout.sizing;

import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * The constraint space for sizing. Derived from the containing context;
 * a definite or indefinite (NONE) percentBase determines whether percentages can be resolved.
 *
 * <ul>
 * <li>availableLine — Available line-axis size (basis for the fit-content upper bound)</li>
 * <li>percentBaseLine — Line-axis percentage basis ({@link LayoutUtils#NONE} if indefinite)</li>
 * <li>percentBasePage — Page-axis percentage basis ({@link LayoutUtils#NONE} if indefinite)</li>
 * </ul>
 *
 * @author MIYABE Tatsuhiko
 */
public record SizingContext(SizingMode mode, double availableLine, double percentBaseLine, double percentBasePage) {
	/**
	 * Returns true if page-axis percentages can be resolved.
	 *
	 * @return True if the page-axis percentage basis is definite
	 */
	public boolean isPagePercentDefinite() {
		return !LayoutUtils.isNone(this.percentBasePage);
	}
}
