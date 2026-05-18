package net.zamasoft.foliojet.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.value.InheritValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.sac.css.LexicalUnit;

/**
 * 分解不可能なプロパティです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractPrimitivePropertyInfo.java 3806 2012-07-10 07:03:19Z
 *          miyabe $
 */
public abstract class AbstractPrimitivePropertyInfo extends AbstractPropertyInfo implements PrimitivePropertyInfo {

	protected AbstractPrimitivePropertyInfo(String name) {
		super(name);
	}

	public final Property parseProperty(LexicalUnit lu, UserAgent ua, URI uri, boolean important)
			throws PropertyException {
		Value value;
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			// 継承
			value = InheritValue.INHERIT_VALUE;
		} else {
			value = this.parseProperty(lu, ua, uri);
		}
		return new PrimitiveProperty(this, value, uri, important);
	}

	public PrimitivePropertyInfo getEffectiveInfo(CSSStyle style) {
		return this;
	}

	public abstract Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException;
}