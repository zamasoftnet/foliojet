package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.grid.GridPlacement;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.GridLineValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code grid-area} shorthand (css-grid-1 §8.4, 2026-08-29:
 * 701 occurrences on 15 sites in a 50-site sweep).
 * Expands {@code <grid-line> [ / <grid-line> ]{0,3}} in
 * row-start / column-start / row-end / column-end order.
 * Fills omissions as specified: use the corresponding start's name if it is a bare line name,
 * otherwise {@code auto} (if column-start is omitted and row-start is a bare line name,
 * all four values use that name). Thus, an area reference such as {@code grid-area: header}
 * fills all four values with {@code header}, which layout resolves to the implicit
 * {@code header-start}/{@code header-end} lines.
 *
 * @author MIYABE Tatsuhiko
 */
public class GridAreaShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new GridAreaShorthand();

	protected GridAreaShorthand() {
		super("grid-area");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException {
		final KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(GridPlacement.ROW_START, global);
			primitives.set(GridPlacement.COLUMN_START, global);
			primitives.set(GridPlacement.ROW_END, global);
			primitives.set(GridPlacement.COLUMN_END, global);
			return;
		}
		final GridLineValue[] lines = new GridLineValue[4];
		int count = 0;
		while (true) {
			if (count == 4) {
				throw new PropertyException();
			}
			final GridLineValue line = GridPlacement.parseLine(tokens);
			if (line == null) {
				throw new PropertyException();
			}
			lines[count++] = line;
			if (!tokens.hasNext()) {
				break;
			}
			if (!tokens.eatSlash()) {
				throw new PropertyException();
			}
		}
		final GridLineValue rowStart = lines[0];
		final GridLineValue columnStart = count > 1 ? lines[1] : sameName(rowStart);
		final GridLineValue rowEnd = count > 2 ? lines[2] : sameName(rowStart);
		final GridLineValue columnEnd = count > 3 ? lines[3] : sameName(columnStart);
		primitives.set(GridPlacement.ROW_START, rowStart);
		primitives.set(GridPlacement.COLUMN_START, columnStart);
		primitives.set(GridPlacement.ROW_END, rowEnd);
		primitives.set(GridPlacement.COLUMN_END, columnEnd);
	}

	/** Fills an omitted value: the same name for a bare line name, otherwise auto (shared with grid-column/row). */
	static GridLineValue sameName(final GridLineValue start) {
		return start.isNameOnly() ? start : GridLineValue.AUTO_VALUE;
	}
}
