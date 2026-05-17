package jp.cssj.homare.impl.css.property.css3;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.TextValueUtils;
import jp.cssj.homare.css.util.ValueUtils;
import jp.cssj.homare.css.value.AutoValue;
import jp.cssj.homare.css.value.TextAlignValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.impl.css.property.TextAlign;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TextAlignLast.java 1640 2023-10-04 03:06:26Z miyabe $
 */
public class TextAlignLast extends AbstractPrimitivePropertyInfo {

	public static final PrimitivePropertyInfo INFO = new TextAlignLast();

	public static byte get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value.getValueType() == Value.TYPE_AUTO) {
			final byte align = TextAlign.get(style);
			switch (align) {
			case TextAlignValue.JUSTIFY:
				value = TextAlignValue.START_VALUE;
				break;
			default:
				value = style.get(TextAlign.INFO);
				break;
			}
		}
		return TextValueUtils.toTextAlignParam((TextAlignValue)value, style);
	}

	protected TextAlignLast() {
		super("-cssj-text-align-last");
	}

	public Value getDefault(CSSStyle style) {
		return AutoValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (ValueUtils.isAuto(lu)) {
			return AutoValue.AUTO_VALUE;
		}
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			TextAlignValue value = TextValueUtils.toTextAlign(ident);
			if (value != null) {
				return value;
			}

		default:
			throw new PropertyException();
		}
	}

}