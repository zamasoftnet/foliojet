package jp.cssj.homare.impl.css.property.internal;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.BoxValueUtils;
import jp.cssj.homare.css.util.ValueUtils;
import jp.cssj.homare.css.value.AutoValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.impl.css.property.Width;
import jp.cssj.homare.style.box.params.Length;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * HTMLの水平アラインメント相当する内部特性です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJAutoWidth.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSJAutoWidth extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJAutoWidth();

	public static Value get(CSSStyle style) {
		return style.get(INFO);
	}

	public static Length getLength(CSSStyle style) {
		return BoxValueUtils.toLength(Width.get(style));
	}

	protected CSSJAutoWidth() {
		super("-cssj-auto-width");
	}

	public Value getDefault(CSSStyle style) {
		return AutoValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (ValueUtils.isAuto(lu)) {
			return AutoValue.AUTO_VALUE;
		}

		Value value = BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

	public int getPriority() {
		return 2;
	}
}