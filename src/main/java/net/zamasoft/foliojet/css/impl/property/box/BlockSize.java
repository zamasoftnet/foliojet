package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * block-size 特性(論理プロパティ)です。writing-modeにより width か
 * height のいずれかに対応します(inline-sizeと逆軸。{@link InlineSize}参照)。
 */
public final class BlockSize extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BlockSize();

	public static Value get(CSSStyle style) {
		PrimitivePropertyInfo physicalInfo = BlockFlow.get(style).isVertical() ? Width.INFO : Height.INFO;
		if (style.isDeclared(physicalInfo)) {
			return style.get(physicalInfo);
		}
		if (style.isDeclared(INFO)) {
			return style.get(INFO);
		}
		return style.get(physicalInfo);
	}

	private BlockSize() {
		super("block-size");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isAuto(lu)) {
			return KeywordValue.AUTO;
		}
		Value value = BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}
}
