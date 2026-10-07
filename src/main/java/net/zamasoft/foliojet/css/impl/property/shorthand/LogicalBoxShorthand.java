package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.box.Inset;
import net.zamasoft.foliojet.css.impl.property.box.Margin;
import net.zamasoft.foliojet.css.impl.property.box.Padding;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Logical shorthands {@code margin-block} / {@code margin-inline} /
 * {@code padding-block} / {@code padding-inline} / {@code inset-block} /
 * {@code inset-inline} (css-logical-1 §4, 2026-08-29).
 *
 * <p>
 * Logical longhands ({@code margin-inline-start}, etc.) already existed, but these shorthands,
 * which merely distribute two values to start/end, were missing, so declarations were discarded entirely.
 * Accepts one or two values (start end). The base class handles CSS-wide keywords via {@link #longhands()}.
 * </p>
 */
public final class LogicalBoxShorthand extends AbstractShorthandPropertyInfo {
	/** Parsing strategy for each value type. */
	@FunctionalInterface
	private interface Reader {
		Value read(UserAgent ua, CssToken token) throws PropertyException;
	}

	public static final ShorthandPropertyInfo MARGIN_BLOCK = new LogicalBoxShorthand("margin-block",
			Margin.BLOCK_START, Margin.BLOCK_END, BoxValueUtils::toMarginWidth);
	public static final ShorthandPropertyInfo MARGIN_INLINE = new LogicalBoxShorthand("margin-inline",
			Margin.INLINE_START, Margin.INLINE_END, BoxValueUtils::toMarginWidth);
	public static final ShorthandPropertyInfo PADDING_BLOCK = new LogicalBoxShorthand("padding-block",
			Padding.BLOCK_START, Padding.BLOCK_END, BoxValueUtils::toPositiveLength);
	public static final ShorthandPropertyInfo PADDING_INLINE = new LogicalBoxShorthand("padding-inline",
			Padding.INLINE_START, Padding.INLINE_END, BoxValueUtils::toPositiveLength);
	public static final ShorthandPropertyInfo INSET_BLOCK = new LogicalBoxShorthand("inset-block",
			Inset.BLOCK_START, Inset.BLOCK_END, BoxValueUtils::toMarginWidth);
	public static final ShorthandPropertyInfo INSET_INLINE = new LogicalBoxShorthand("inset-inline",
			Inset.INLINE_START, Inset.INLINE_END, BoxValueUtils::toMarginWidth);

	public static ShorthandPropertyInfo[] all() {
		return new ShorthandPropertyInfo[] { MARGIN_BLOCK, MARGIN_INLINE, PADDING_BLOCK, PADDING_INLINE, INSET_BLOCK,
				INSET_INLINE };
	}

	private final PrimitivePropertyInfo start, end;
	private final Reader reader;

	private LogicalBoxShorthand(final String name, final PrimitivePropertyInfo start,
			final PrimitivePropertyInfo end, final Reader reader) {
		super(name);
		this.start = start;
		this.end = end;
		this.reader = reader;
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		return new PrimitivePropertyInfo[] { this.start, this.end };
	}

	@Override
	public void parseValues(final TokenStream tokens, final UserAgent ua, final URI uri, final Primitives primitives)
			throws PropertyException {
		final Value first = this.reader.read(ua, tokens.next());
		if (first == null) {
			throw new PropertyException();
		}
		Value second = first;
		if (tokens.hasNext()) {
			second = this.reader.read(ua, tokens.next());
			if (second == null || tokens.hasNext()) {
				throw new PropertyException();
			}
		}
		primitives.set(this.start, first);
		primitives.set(this.end, second);
	}
}
