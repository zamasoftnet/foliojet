package jp.cssj.homare.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.value.InheritValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

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