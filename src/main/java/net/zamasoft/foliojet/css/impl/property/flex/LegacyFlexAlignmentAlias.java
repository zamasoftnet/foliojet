package net.zamasoft.foliojet.css.impl.property.flex;

import java.net.URI;
import java.util.List;

import net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.BoxAlignmentValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Aliases mapping 2011 Flexbox alignment properties ({@code -ms-flex-pack}, etc.)
 * to standard names (added 2026-08-29; 18 occurrences across 50 real sites).
 *
 * <p>
 * The value vocabulary differs ({@code start}/{@code end}/{@code justify}/
 * {@code distribute}), so a simple {@code alias()} cannot handle it.
 * Maps {@code start}→{@code flex-start}, {@code end}→{@code flex-end},
 * {@code justify}→{@code space-between}, and {@code distribute}→
 * {@code space-around}, then delegates parsing to the standard property
 * (implemented as a shorthand for one longhand; CSS-wide keywords also pass through).
 * {@code center}/{@code stretch}/{@code baseline}/{@code auto} pass through unchanged.
 * </p>
 *
 * <p>
 * The 2009 {@code -webkit-box-pack}/{@code -webkit-box-align}/
 * {@code -webkit-box-orient} are excluded: {@code display: -webkit-box} maps to block
 * (not a flex container), so mapping alignment alone would be meaningless.
 * They remain on the ignore list ({@code box-pack}, etc. in
 * {@code PropertySet.IGNORED_PROPERTIES}).
 * </p>
 */
public final class LegacyFlexAlignmentAlias extends AbstractShorthandPropertyInfo {
	public static final LegacyFlexAlignmentAlias FLEX_PACK = new LegacyFlexAlignmentAlias("-ms-flex-pack",
			GridAlignmentProperty.JUSTIFY_CONTENT);

	public static final LegacyFlexAlignmentAlias FLEX_ALIGN = new LegacyFlexAlignmentAlias("-ms-flex-align",
			GridAlignmentProperty.ALIGN_ITEMS);

	public static final LegacyFlexAlignmentAlias FLEX_ITEM_ALIGN = new LegacyFlexAlignmentAlias(
			"-ms-flex-item-align", GridAlignmentProperty.ALIGN_SELF);

	public static final LegacyFlexAlignmentAlias FLEX_LINE_PACK = new LegacyFlexAlignmentAlias(
			"-ms-flex-line-pack", GridAlignmentProperty.ALIGN_CONTENT);

	public static List<LegacyFlexAlignmentAlias> all() {
		return List.of(FLEX_PACK, FLEX_ALIGN, FLEX_ITEM_ALIGN, FLEX_LINE_PACK);
	}

	private final GridAlignmentProperty target;

	private LegacyFlexAlignmentAlias(final String name, final GridAlignmentProperty target) {
		super(name);
		this.target = target;
	}

	public GridAlignmentProperty getTarget() {
		return this.target;
	}

	@Override
	protected PrimitivePropertyInfo[] longhands() {
		return new PrimitivePropertyInfo[] { this.target };
	}

	/** Maps 2011 vocabulary to standard vocabulary. Leaves other values unchanged. */
	static String translate(final String keyword) {
		switch (keyword) {
		case "start":
			return "flex-start";
		case "end":
			return "flex-end";
		case "justify":
			return "space-between";
		case "distribute":
			return "space-around";
		default:
			return keyword;
		}
	}

	@Override
	public void parseValues(final TokenStream tokens, final UserAgent ua, final URI uri,
			final Primitives primitives) throws PropertyException {
		final CssToken token = tokens.next();
		if (!(token instanceof CssToken.Ident ident) || tokens.hasNext()) {
			throw new PropertyException();
		}
		final TokenStream translated = new TokenStream(List.of(new CssToken.Ident(translate(ident.lower()))));
		final BoxAlignmentValue value = this.target.eatValue(translated);
		if (value == null || translated.hasNext()) {
			throw new PropertyException();
		}
		primitives.set(this.target, value);
	}
}
