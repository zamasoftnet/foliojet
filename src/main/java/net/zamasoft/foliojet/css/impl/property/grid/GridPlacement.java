package net.zamasoft.foliojet.css.impl.property.grid;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.GridLineValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code grid-column-start/end}・{@code grid-row-start/end}です(Grid G0)。
 * {@code auto | <整数(非0)> | span <正整数>}のみ(named lineは宣言無効)。
 *
 * @author MIYABE Tatsuhiko
 */
public class GridPlacement extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo COLUMN_START = new GridPlacement("grid-column-start");

	public static final PrimitivePropertyInfo COLUMN_END = new GridPlacement("grid-column-end");

	public static final PrimitivePropertyInfo ROW_START = new GridPlacement("grid-row-start");

	public static final PrimitivePropertyInfo ROW_END = new GridPlacement("grid-row-end");

	public static GridLineValue get(CSSStyle style, PrimitivePropertyInfo info) {
		return (GridLineValue) style.get(info);
	}

	protected GridPlacement(final String name) {
		super(name);
	}

	public Value getDefault(CSSStyle style) {
		return GridLineValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final Value value = parseLine(tokens);
		if (value == null || tokens.hasNext()) {
			throw new PropertyException();
		}
		return value;
	}

	/** 1つの&lt;grid-line&gt;を読み取ります(shorthandと共用)。不正はnull。 */
	public static GridLineValue parseLine(final TokenStream tokens) {
		if (tokens.eat("auto")) {
			return GridLineValue.AUTO_VALUE;
		}
		if (tokens.eat("span")) {
			final CssToken.Num count = tokens.number();
			if (count == null || !count.integer() || count.intValue() < 1) {
				return null;
			}
			return GridLineValue.span(count.intValue());
		}
		final CssToken.Num number = tokens.number();
		if (number == null || !number.integer() || number.intValue() == 0) {
			return null;
		}
		return GridLineValue.line(number.intValue());
	}
}
