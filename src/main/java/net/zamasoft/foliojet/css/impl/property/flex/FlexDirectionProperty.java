package net.zamasoft.foliojet.css.impl.property.flex;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.FlexDirectionValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code flex-direction}です(Flex F1a、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt)。
 *
 * @author MIYABE Tatsuhiko
 */
public class FlexDirectionProperty extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FlexDirectionProperty();

	public static FlexDirectionValue get(CSSStyle style) {
		return (FlexDirectionValue) style.get(INFO);
	}

	private FlexDirectionProperty() {
		super("flex-direction");
	}

	public Value getDefault(CSSStyle style) {
		return FlexDirectionValue.ROW;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final FlexDirectionValue value = parseKeyword(tokens);
		if (value == null || tokens.hasNext()) {
			throw new PropertyException();
		}
		return value;
	}

	/** 1つのキーワードを読み取ります(flex-flowと共用)。不正はnull。 */
	public static FlexDirectionValue parseKeyword(final TokenStream tokens) {
		if (tokens.eat("row")) {
			return FlexDirectionValue.ROW;
		}
		if (tokens.eat("row-reverse")) {
			return FlexDirectionValue.ROW_REVERSE;
		}
		if (tokens.eat("column")) {
			return FlexDirectionValue.COLUMN;
		}
		if (tokens.eat("column-reverse")) {
			return FlexDirectionValue.COLUMN_REVERSE;
		}
		return null;
	}
}
