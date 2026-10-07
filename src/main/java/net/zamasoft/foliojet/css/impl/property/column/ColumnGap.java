package net.zamasoft.foliojet.css.impl.property.column;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.GapValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;

/**
 * <a href="http://www.w3.org/TR/CSS21/box.html#propdef-border-left-width">
 * border-left-width property </a>.
 *
 * @author MIYABE Tatsuhiko
 */
public class ColumnGap extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ColumnGap();

	/**
	 * Used value for multi-column layout ({@code normal}=1em, the existing behavior).
	 * Grid G0 changed this to retain {@code normal} in the computed value:
	 * multicol resolves it to 1em here, and Grid resolves it to 0 via {@link #getForGrid}
	 * (consult-codex-2026-07-31-grid.txt §2).
	 */
	public static double get(CSSStyle style) {
		final Value value = style.get(INFO);
		if (value == net.zamasoft.foliojet.css.value.KeywordValue.NORMAL) {
			return net.zamasoft.foliojet.css.impl.property.font.FontSize.get(style);
		}
		return ((AbsoluteLengthValue) value).getLength();
	}

	/** Used value for Grid ({@code normal}=0). */
	public static double getForGrid(CSSStyle style) {
		final Value value = style.get(INFO);
		if (value == net.zamasoft.foliojet.css.value.KeywordValue.NORMAL) {
			return 0;
		}
		return ((AbsoluteLengthValue) value).getLength();
	}

	/** Whether the computed value is {@code normal}. Retain it separately from the Grid used value 0. */
	public static boolean isNormal(final CSSStyle style) {
		return style.get(INFO) == net.zamasoft.foliojet.css.value.KeywordValue.NORMAL;
	}

	protected ColumnGap() {
		super("-cssj-column-gap");
	}

	public Value getDefault(CSSStyle style) {
		return net.zamasoft.foliojet.css.value.KeywordValue.NORMAL;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		if (value == net.zamasoft.foliojet.css.value.KeywordValue.NORMAL) {
			return value;
		}
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isNormal(lu)) {
			return net.zamasoft.foliojet.css.value.KeywordValue.NORMAL;
		}
		// **Accept calc()** (2026-08-04). column-gap is shared by multicol and Grid/Flex.
		// Previously, it only passed through BorderValueUtils.toBorderWidth, so
		// calc() caused the entire declaration to be discarded. The entry point is GapValueUtils.
		final Value value = GapValueUtils.toGap(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}
