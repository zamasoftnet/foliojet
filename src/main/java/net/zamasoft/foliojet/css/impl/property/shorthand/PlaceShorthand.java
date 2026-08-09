package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.grid.GridAlignmentProperty;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.BoxAlignmentValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code place-items} / {@code place-self} / {@code place-content}
 * ショートハンドです(2026-08-09)。{@code <align> [<justify>]}——
 * 1値なら両方に適用(CSS Box Alignment §6.4)。受理できる
 * キーワードは各longhand({@link GridAlignmentProperty})のサブセットに
 * 従う。NHKニュースのナビのボタンが{@code place-items:center}で
 * アイコンを中央寄せしており、未対応だと左上に寄っていた。
 *
 * @author MIYABE Tatsuhiko
 */
public class PlaceShorthand extends AbstractShorthandPropertyInfo {

	public static final ShorthandPropertyInfo ITEMS = new PlaceShorthand("place-items",
			GridAlignmentProperty.ALIGN_ITEMS, GridAlignmentProperty.JUSTIFY_ITEMS);

	public static final ShorthandPropertyInfo SELF = new PlaceShorthand("place-self",
			GridAlignmentProperty.ALIGN_SELF, GridAlignmentProperty.JUSTIFY_SELF);

	public static final ShorthandPropertyInfo CONTENT = new PlaceShorthand("place-content",
			GridAlignmentProperty.ALIGN_CONTENT, GridAlignmentProperty.JUSTIFY_CONTENT);

	private final GridAlignmentProperty align;

	private final GridAlignmentProperty justify;

	protected PlaceShorthand(final String name, final GridAlignmentProperty align,
			final GridAlignmentProperty justify) {
		super(name);
		this.align = align;
		this.justify = justify;
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException {
		final KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(this.align, global);
			primitives.set(this.justify, global);
			return;
		}
		final BoxAlignmentValue alignValue = this.align.eatValue(tokens);
		if (alignValue == null) {
			throw new PropertyException();
		}
		BoxAlignmentValue justifyValue = alignValue;
		if (tokens.hasNext()) {
			justifyValue = this.justify.eatValue(tokens);
			if (justifyValue == null || tokens.hasNext()) {
				throw new PropertyException();
			}
		}
		primitives.set(this.align, alignValue);
		primitives.set(this.justify, justifyValue);
	}
}
