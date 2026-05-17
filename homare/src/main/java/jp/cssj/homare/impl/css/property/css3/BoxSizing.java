package jp.cssj.homare.impl.css.property.css3;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.css.value.css3.BoxSizingValue;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: WordWrap.java 1485 2016-12-16 06:41:11Z miyabe $
 */
public class BoxSizing extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BoxSizing();

	public static byte get(CSSStyle style) {
		return ((BoxSizingValue) style.get(INFO)).getBoxSizing();
	}

	protected BoxSizing() {
		super("box-sizing");
	}

	public Value getDefault(CSSStyle style) {
		return BoxSizingValue.CONTENT_BOX_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("content-box")) {
				return BoxSizingValue.CONTENT_BOX_VALUE;
			} else if (ident.equals("border-box")) {
				return BoxSizingValue.BORDER_BOX_VALUE;
			}

		default:
			throw new PropertyException();
		}
	}

}