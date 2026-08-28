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
 * {@code grid-area}ショートハンドです(css-grid-1 §8.4、2026-08-29——
 * 50サイト掃過で701回/15サイト)。
 * {@code <grid-line> [ / <grid-line> ]{0,3}}を
 * row-start / column-start / row-end / column-endの順に展開する。省略時は
 * 仕様の補完則: 対応するstartが線名単独ならその名前、そうでなければ
 * {@code auto}(column-startの省略はrow-startが線名単独なら4値全てに
 * その名前)。{@code grid-area: header}のような領域名参照はこの補完で
 * 4値とも{@code header}になり、レイアウト側が{@code header-start}/
 * {@code header-end}の暗黙線へ解決する。
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

	/** 省略値の補完: 線名単独ならその名前、それ以外はauto(grid-column/rowと共用)。 */
	static GridLineValue sameName(final GridLineValue start) {
		return start.isNameOnly() ? start : GridLineValue.AUTO_VALUE;
	}
}
