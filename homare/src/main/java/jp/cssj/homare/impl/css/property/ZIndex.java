package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.AutoValue;
import jp.cssj.homare.css.value.IntegerValue;
import jp.cssj.homare.css.value.PositionValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.style.box.params.Params;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ZIndex.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class ZIndex extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ZIndex();

	public static int getValue(CSSStyle style) {
		Value value = style.get(INFO);
		return ((IntegerValue) value).getInteger();
	}

	public static byte getType(CSSStyle style) {
		Value value = style.get(INFO);
		if (value.getValueType() == Value.TYPE_AUTO || CSSPosition.get(style) == PositionValue.STATIC) {
			return Params.Z_INDEX_AUTO;
		}
		return Params.Z_INDEX_SPECIFIED;
	}

	private ZIndex() {
		super("z-index");
	}

	public Value getDefault(CSSStyle style) {
		return AutoValue.AUTO_VALUE;
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
			if (ident.equals("auto")) {
				return IntegerValue.ZERO;
			}
			break;

		case LexicalUnit.SAC_INTEGER:
			return IntegerValue.create(lu.getIntegerValue());
		}
		throw new PropertyException();
	}

}