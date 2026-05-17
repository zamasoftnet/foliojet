package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.OverflowValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: Overflow.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class Overflow extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Overflow();

	public static byte get(CSSStyle style) {
		OverflowValue value = (OverflowValue) style.get(INFO);
		return value.getOverflow();
	}

	private Overflow() {
		super("overflow");
	}

	public Value getDefault(CSSStyle style) {
		return OverflowValue.VISIBLE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("visible")) {
				return OverflowValue.VISIBLE_VALUE;
			} else if (ident.equals("hidden")) {
				return OverflowValue.HIDDEN_VALUE;
			} else if (ident.equals("scroll")) {
				return OverflowValue.SCROLL_VALUE;
			} else if (ident.equals("auto")) {
				return OverflowValue.AUTO_VALUE;
			}

		default:
			throw new PropertyException();
		}
	}

}