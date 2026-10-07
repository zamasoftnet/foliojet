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
import net.zamasoft.foliojet.css.impl.property.internal.CSSJAutoWidth;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * <a href="http://www.w3.org/TR/CSS21/visudet.html#propdef-width"> width property
 * </a>.
 *
 * @author MIYABE Tatsuhiko
 */
public class Width extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Width();

	public static Value get(CSSStyle style) {
		boolean image = CSSJInternalImage.getImage(style) != null;
		if (style.isDeclared(INFO)) {
			return style.get(INFO);
		}
		// If width is not explicitly specified, fall back to the standard logical properties
		// inline-size/block-size (does not apply to images). On 2026-07-20,
		// -cssj-direction-mode was abolished and support was consolidated into inline-size/
		// block-size.
		if (!image) {
			PrimitivePropertyInfo logicalInfo = BlockFlow.get(style).isVertical() ? BlockSize.INFO : InlineSize.INFO;
			if (style.isDeclared(logicalInfo)) {
				return style.get(logicalInfo);
			}
		}
		return style.get(INFO);
	}

	public static Length getLength(CSSStyle style) {
		return BoxValueUtils.toLength(Width.get(style));
	}

	protected Width() {
		super("width");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	/**
	 * <b>Rejects negative lengths as invalid and falls back to the initial value</b> (2026-08-05).
	 *
	 * <p>
	 * CSS forbids negative width/height, and {@code toPositiveLength} in {@link #parseValue}
	 * rejects them. However, {@code attr()} and {@code calc()} <b>need the element's attributes
	 * and context</b>, so they resolve at the computed-value stage and bypass parsing-time checks.
	 * </p>
	 *
	 * <p>
	 * Actual impact: when table presentational attributes were moved to UA CSS on 2026-08-03
	 * as {@code table[width] { width: attr(width px) }}, {@code <table width="-500">}
	 * collapsed to 16.5 pt wide (the minimum content width), wrapping cell text vertically
	 * one character at a time. The equivalent {@code style="width:-500px"} was correctly ignored,
	 * so behavior differed depending on the path. <b>The baseline image difference was 0.35%,
	 * hidden by imageTest's 2% tolerance</b> (found visually on 2026-08-05).
	 * </p>
	 */
	public Value getComputedValue(Value value, CSSStyle style) {
		if (value == KeywordValue.AUTO) {
			value = CSSJAutoWidth.get(style);
		}
		final Value resolved = ValueUtils.emExToAbsoluteLength(value, style);
		if (resolved instanceof net.zamasoft.foliojet.css.value.QuantityValue q && q.isNegative()) {
			return ValueUtils.emExToAbsoluteLength(CSSJAutoWidth.get(style), style);
		}
		return resolved;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isAuto(lu)) {
			return KeywordValue.AUTO;
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