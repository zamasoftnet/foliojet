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
public class MaxHeight extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MaxHeight();

	public static Value get(CSSStyle style) {
		boolean image = CSSJInternalImage.getImage(style) != null;
		// 2026-07-20: abolished -cssj-direction-mode and consolidated support into max-inline-size/
		// max-block-size.
		if (!image) {
			PrimitivePropertyInfo logicalInfo = BlockFlow.get(style).isVertical() ? MaxInlineSize.INFO : MaxBlockSize.INFO;
			// The later of the two declarations wins (2026-10-08, CSS Logical 1 §4)
			if (LogicalSide.logicalWins(style, INFO, logicalInfo)) {
				return style.get(logicalInfo);
			}
		}
		return style.get(INFO);
	}

	public static Length getLength(CSSStyle style) {
		return BoxValueUtils.toLength(MaxHeight.get(style));
	}

	private MaxHeight() {
		super("max-height");
	}

	private Value getDefault(UserAgent ua) {
		// **The initial value is none** (2026-08-17). Previously this returned the UA's
		// {@code getMaxSize()} (=14400 pt, the PDF <b>paper</b> dimension limit),
		// but that is not a box height limit. Blocks taller than 14400 pt
		// (long tables, etc.) were truncated to this value by {@code AbstractBlockBox},
		// preventing pagination from ever advancing. The w3c-jlreq glossary table
		// livelocked after 32 page breaks without progress, failing the conversion.
		return KeywordValue.NONE;
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
		// Intrinsic sizing keywords max-content/min-content/fit-content(L) (2026-08-29).
		final Value intrinsic = BoxValueUtils.toIntrinsicSize(ua, lu);
		if (intrinsic != null) {
			return intrinsic;
		}
		Value value = BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}