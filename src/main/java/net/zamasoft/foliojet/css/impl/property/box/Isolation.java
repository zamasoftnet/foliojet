package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code isolation: auto | isolate}です(compositing-1 §3、2026-08-29新設)。
 *
 * <p>
 * 受理するだけで効果はない(警告も出さない)。{@code mix-blend-mode}を
 * 描画要素ごとに適用する近似({@link MixBlendMode}参照)では分離
 * グループを作らないため、現状では意味を持たせられない。
 * </p>
 */
public class Isolation extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Isolation();

	/** {@code isolate}のキーワード値。 */
	public static final Value ISOLATE = new Value() {
		@Override
		public String toString() {
			return "isolate";
		}
	};

	protected Isolation() {
		super("isolation");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident && !tokens.hasNext()) {
			if (ident.is("auto")) {
				return KeywordValue.AUTO;
			}
			if (ident.is("isolate")) {
				return ISOLATE;
			}
		}
		throw new PropertyException();
	}
}
