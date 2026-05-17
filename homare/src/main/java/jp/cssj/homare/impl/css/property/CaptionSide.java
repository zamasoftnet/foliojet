package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.TableValueUtils;
import jp.cssj.homare.css.value.CaptionSideValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CaptionSide.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CaptionSide extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CaptionSide();

	public static byte get(CSSStyle style) {
		CaptionSideValue value = (CaptionSideValue) style.get(INFO);
		return value.getCaptionSide();
	}

	protected CaptionSide() {
		super("caption-side");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return CaptionSideValue.BEFORE_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		final Value value = TableValueUtils.toCaptionSide(lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}