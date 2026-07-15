package net.zamasoft.foliojet.impl.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.TableValueUtils;
import net.zamasoft.foliojet.css.value.BorderCollapseValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class BorderCollapse extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BorderCollapse();

	public static byte get(CSSStyle style) {
		BorderCollapseValue value = (BorderCollapseValue) style.get(INFO);
		return value.getBorderCollapse();
	}

	protected BorderCollapse() {
		super("border-collapse");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return BorderCollapseValue.SEPARATE_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value value = TableValueUtils.toBorderCollapse(lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}