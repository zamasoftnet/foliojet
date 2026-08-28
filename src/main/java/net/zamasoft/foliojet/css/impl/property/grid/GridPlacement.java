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
 * {@code auto | <custom-ident> | [ <integer> && <custom-ident>? ] |
 * [ span && [ <integer> || <custom-ident> ] ]}(css-grid-1 §8.3)。
 * 線名は2026-08-29から受理する(数値化はレイアウト側)。
 * {@code span 0}は仕様では無効だが、実物のWebで見かける
 * ({@code grid-column: span 0})ため{@code span 1}として受理する
 * (2026-08-29——宣言無効で単一列へ落ちるより見た目が近い)。
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

	/**
	 * 1つの&lt;grid-line&gt;を読み取ります(shorthandと共用)。不正はnull。
	 * スラッシュ(shorthandの区切り)の手前で止まる。
	 */
	public static GridLineValue parseLine(final TokenStream tokens) {
		if (tokens.eat("auto")) {
			return GridLineValue.AUTO_VALUE;
		}
		boolean span = false;
		Integer number = null;
		String name = null;
		while (tokens.hasNext() && tokens.peek() != CssToken.Op.SLASH) {
			final CssToken token = tokens.peek();
			if (token instanceof CssToken.Ident ident) {
				if (ident.is("span")) {
					if (span) {
						return null;
					}
					span = true;
				} else if (ident.is("auto")) {
					return null;
				} else if (name != null) {
					return null;
				} else {
					name = ident.name();
				}
				tokens.next();
			} else if (token instanceof CssToken.Num num) {
				if (number != null || !num.integer()) {
					return null;
				}
				number = num.intValue();
				tokens.next();
			} else {
				return null;
			}
		}
		if (span) {
			if (number == null && name == null) {
				return null;
			}
			int count = number == null ? 1 : number;
			if (count < 0) {
				return null;
			}
			if (count == 0) {
				count = 1; // span 0→span 1(クラスjavadoc)
			}
			return name == null ? GridLineValue.span(count) : GridLineValue.span(count, name);
		}
		if (number != null) {
			if (number == 0) {
				return null;
			}
			return name == null ? GridLineValue.line(number) : GridLineValue.line(number, name);
		}
		if (name == null) {
			return null;
		}
		return GridLineValue.named(name);
	}
}
