package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.grid.GridPlacement;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.GridLineValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code grid-column}/{@code grid-row}ショートハンドです(Grid G0)。
 * {@code <grid-line> [/ <grid-line>]}——endを省略すると{@code auto}、
 * ただしstartが線名単独ならendも同じ名前(css-grid-1 §8.4、2026-08-29
 * ——{@code grid-column: content}は{@code content-start / content-end})。
 *
 * @author MIYABE Tatsuhiko
 */
public class GridLineShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo COLUMN = new GridLineShorthand("grid-column",
			GridPlacement.COLUMN_START, GridPlacement.COLUMN_END);

	public static final ShorthandPropertyInfo ROW = new GridLineShorthand("grid-row", GridPlacement.ROW_START,
			GridPlacement.ROW_END);

	private final PrimitivePropertyInfo start, end;

	protected GridLineShorthand(final String name, final PrimitivePropertyInfo start,
			final PrimitivePropertyInfo end) {
		super(name);
		this.start = start;
		this.end = end;
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException {
		final KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(this.start, global);
			primitives.set(this.end, global);
			return;
		}
		final GridLineValue startLine = GridPlacement.parseLine(tokens);
		if (startLine == null) {
			throw new PropertyException();
		}
		GridLineValue endLine = GridAreaShorthand.sameName(startLine);
		if (tokens.eatSlash()) {
			endLine = GridPlacement.parseLine(tokens);
			if (endLine == null) {
				throw new PropertyException();
			}
		}
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		primitives.set(this.start, startLine);
		primitives.set(this.end, endLine);
	}
}
