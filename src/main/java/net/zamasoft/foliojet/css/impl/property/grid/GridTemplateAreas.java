package net.zamasoft.foliojet.css.impl.property.grid;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.GridTemplateAreasValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code grid-template-areas}です(css-grid-1 §7.3、2026-08-29——
 * 50サイト掃過で170回/8サイト)。{@code none | <string>+}。各文字列は
 * 空白区切りのセルで、名前か{@code .}(連続する{@code .}も1個の空セル)。
 * 全行の列数が等しく、同名セルが矩形をなすことを検証する(不正は
 * 宣言無効=仕様どおり)。名前は{@code name-start}/{@code name-end}の
 * 暗黙線名としてレイアウト側({@code GridLineNameResolver})が使う。
 *
 * @author MIYABE Tatsuhiko
 */
public class GridTemplateAreas extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new GridTemplateAreas();

	public static GridTemplateAreasValue get(CSSStyle style) {
		return (GridTemplateAreasValue) style.get(INFO);
	}

	protected GridTemplateAreas() {
		super("grid-template-areas");
	}

	public Value getDefault(CSSStyle style) {
		return GridTemplateAreasValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.size() == 1 && tokens.eat("none")) {
			return GridTemplateAreasValue.NONE_VALUE;
		}
		final List<String[]> rows = new ArrayList<>();
		while (tokens.hasNext()) {
			final String text = tokens.string();
			if (text == null) {
				throw new PropertyException();
			}
			final String[] cells = splitCells(text);
			if (cells.length == 0) {
				throw new PropertyException();
			}
			rows.add(cells);
		}
		if (rows.isEmpty()) {
			throw new PropertyException();
		}
		final int columnCount = rows.get(0).length;
		final Map<String, int[]> bounds = new LinkedHashMap<>();
		for (int r = 0; r < rows.size(); ++r) {
			final String[] cells = rows.get(r);
			if (cells.length != columnCount) {
				throw new PropertyException();
			}
			for (int c = 0; c < columnCount; ++c) {
				final String name = cells[c];
				if (name == null) {
					continue;
				}
				final int[] b = bounds.get(name);
				if (b == null) {
					bounds.put(name, new int[] { r, c, r + 1, c + 1 });
				} else {
					b[0] = Math.min(b[0], r);
					b[1] = Math.min(b[1], c);
					b[2] = Math.max(b[2], r + 1);
					b[3] = Math.max(b[3], c + 1);
				}
			}
		}
		// 矩形性: 各領域のbounds内が全てその名前で埋まっていること
		final List<GridTemplateAreasValue.Area> areas = new ArrayList<>(bounds.size());
		for (final Map.Entry<String, int[]> e : bounds.entrySet()) {
			final int[] b = e.getValue();
			for (int r = b[0]; r < b[2]; ++r) {
				for (int c = b[1]; c < b[3]; ++c) {
					if (!e.getKey().equals(rows.get(r)[c])) {
						throw new PropertyException();
					}
				}
			}
			areas.add(new GridTemplateAreasValue.Area(e.getKey(), b[0], b[1], b[2], b[3]));
		}
		return GridTemplateAreasValue.create(areas, rows.size(), columnCount);
	}

	/** 1行の文字列をセルへ分割します(空セル{@code .}+はnull)。 */
	private static String[] splitCells(final String text) throws PropertyException {
		final List<String> cells = new ArrayList<>();
		final int n = text.length();
		int i = 0;
		while (i < n) {
			final char ch = text.charAt(i);
			if (Character.isWhitespace(ch)) {
				++i;
				continue;
			}
			final int start = i;
			if (ch == '.') {
				while (i < n && text.charAt(i) == '.') {
					++i;
				}
				if (i < n && !Character.isWhitespace(text.charAt(i))) {
					throw new PropertyException(); // ".a"のような混在
				}
				cells.add(null);
				continue;
			}
			while (i < n && !Character.isWhitespace(text.charAt(i))) {
				++i;
			}
			final String name = text.substring(start, i);
			if (name.indexOf('.') >= 0) {
				throw new PropertyException();
			}
			cells.add(name);
		}
		return cells.toArray(new String[0]);
	}
}
