package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class MaxWidth extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MaxWidth();

	public static Value get(CSSStyle style) {
		boolean image = CSSJInternalImage.getImage(style) != null;
		if (style.isDeclared(INFO)) {
			return style.get(INFO);
		}
		// 2026-07-20、-cssj-direction-mode廃止によりmax-inline-size/
		// max-block-sizeへ一本化。
		if (!image) {
			PrimitivePropertyInfo logicalInfo = BlockFlow.get(style).isVertical() ? MaxBlockSize.INFO : MaxInlineSize.INFO;
			if (style.isDeclared(logicalInfo)) {
				return style.get(logicalInfo);
			}
		}
		return style.get(INFO);
	}

	public static Length getLength(CSSStyle style) {
		return BoxValueUtils.toLength(MaxWidth.get(style));
	}

	private MaxWidth() {
		super("max-width");
	}

	private Value getDefault(UserAgent ua) {
		// return KeywordValue.NONE;
		return ua.getMaxSize();
	}

	public Value getDefault(CSSStyle style) {
		return this.getDefault(style.getUserAgent());
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isNone(lu)) {
			return this.getDefault(ua);
		}

		Value value = BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}