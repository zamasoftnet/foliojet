package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.WritingModeVariantValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Internal property carrying the sideways variant independently.
 *
 * <p>
 * Settings from the standard {@code writing-mode} are enabled at a later stage.
 * At this stage, this is a hidden longhand that preserves state through params,
 * replay, and fragmentation.
 * </p>
 */
public class WritingModeVariant extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new WritingModeVariant();

	public static net.zamasoft.foliojet.layout.box.params.WritingModeVariant get(final CSSStyle style) {
		final WritingModeVariantValue value = (WritingModeVariantValue) style.get(INFO);
		return value.getWritingModeVariant();
	}

	private WritingModeVariant() {
		super("-cssj-writing-mode-variant");
	}

	@Override
	public Value getDefault(final CSSStyle style) {
		return WritingModeVariantValue.NORMAL_VALUE;
	}

	@Override
	public boolean isInherited() {
		return true;
	}

	@Override
	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	@Override
	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident && ident.is("normal")) {
			return WritingModeVariantValue.NORMAL_VALUE;
		}
		throw new PropertyException();
	}
}
