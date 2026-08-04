package net.zamasoft.foliojet.css.impl.property.internal;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Unit;

/**
 * HTMLのテーブルcellpaddingに相当する内部特性です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class CSSJHtmlCellPadding extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJHtmlCellPadding();

	public static LengthValue get(CSSStyle style) {
		LengthValue value = (LengthValue) style.get(INFO);
		return value;
	}

	public static void set(CSSStyle style, LengthValue value) {
		style.set(INFO, value);
	}

	public CSSJHtmlCellPadding() {
		super("-cssj-html-cellpadding");
	}

	/**
	 * <b>宣言した要素(表)で解いてから継承させる</b>(2026-08-03)。
	 * {@code attr(cellpadding px)} や {@code em} は要素依存なので、
	 * 未解決のまま継承するとセル側で別の値になってしまう。
	 */
	public Value getComputedValue(Value value, CSSStyle style) {
		return net.zamasoft.foliojet.css.util.ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value getDefault(CSSStyle style) {
		return AbsoluteLengthValue.create(style.getUserAgent(), 1, Unit.PX);
	}

	public boolean isInherited() {
		return true;
	}

	/** CSSから書けるようにした(2026-08-03)。{@code <length>}(attr()も可)。 */
	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final Value value = net.zamasoft.foliojet.css.util.BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}