package net.zamasoft.foliojet.css.impl.property.flex;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.FlexWrapValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code flex-wrap}です(Flex F1a、2026-08-02)。
 *
 * @author MIYABE Tatsuhiko
 */
public class FlexWrapProperty extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new FlexWrapProperty();

	public static FlexWrapValue get(CSSStyle style) {
		return (FlexWrapValue) style.get(INFO);
	}

	private FlexWrapProperty() {
		super("flex-wrap");
	}

	public Value getDefault(CSSStyle style) {
		return FlexWrapValue.NOWRAP;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final FlexWrapValue value = parseKeyword(tokens);
		if (value == null || tokens.hasNext()) {
			throw new PropertyException();
		}
		return value;
	}

	/** 1つのキーワードを読み取ります(flex-flowと共用)。不正はnull。 */
	public static FlexWrapValue parseKeyword(final TokenStream tokens) {
		if (tokens.eat("nowrap")) {
			return FlexWrapValue.NOWRAP;
		}
		if (tokens.eat("wrap")) {
			return FlexWrapValue.WRAP;
		}
		if (tokens.eat("wrap-reverse")) {
			return FlexWrapValue.WRAP_REVERSE;
		}
		return null;
	}
}
