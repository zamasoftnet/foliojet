package net.zamasoft.foliojet.impl.css.property.table;

import net.zamasoft.foliojet.style.box.params.EmptyCellsMode;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.TableValueUtils;
import net.zamasoft.foliojet.css.value.EmptyCellsValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class EmptyCells extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new EmptyCells();

	public static EmptyCellsMode get(CSSStyle style) {
		EmptyCellsValue value = (EmptyCellsValue) style.get(INFO);
		return value.getEmptyCells();
	}

	protected EmptyCells() {
		super("empty-cells");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return EmptyCellsValue.SHOW_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value value = TableValueUtils.toEmptyCells(lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}