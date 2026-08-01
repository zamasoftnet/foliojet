package net.zamasoft.foliojet.css.impl.property.flex;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.IntegerValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code order}です(Flex F5a、2026-08-02——
 * consult-codex-2026-08-02-flexbox.txt F5a)。整数(負可)、既定0。
 * 視覚順序のみを変える——Tagged PDFの読み順・構造はソース順のまま
 * (FlexBuilderのbindがソース順を維持する)。
 *
 * @author MIYABE Tatsuhiko
 */
public class OrderProperty extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new OrderProperty();

	public static int get(CSSStyle style) {
		return ((IntegerValue) style.get(INFO)).getInteger();
	}

	private OrderProperty() {
		super("order");
	}

	public Value getDefault(CSSStyle style) {
		return IntegerValue.ZERO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.next() instanceof CssToken.Num num && num.integer() && !tokens.hasNext()) {
			return IntegerValue.create(num.intValue());
		}
		throw new PropertyException();
	}
}
