package net.zamasoft.foliojet.css.impl.property.ext;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.DisplayValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJRubyValue;
import net.zamasoft.foliojet.css.impl.property.box.Display;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class CSSJRuby extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new CSSJRuby();

	public static byte get(CSSStyle style) {
		CSSJRubyValue value = (CSSJRubyValue) style.get(INFO);
		return value.getRuby();
	}

	protected CSSJRuby() {
		super("-cssj-ruby");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		final byte ruby = ((CSSJRubyValue) value).getRuby();
		final byte display = Display.get(style);
		// 注釈付きテキスト方式(text.ruby=annotation、2026-07-25 F-1)では
		// ルビ要素は通常のINLINEとして流すため、display:inlineでも役割
		// マーカーを保持する。既定(box)の判定は従来どおり(挙動不変)。
		final boolean annotationInline = display == DisplayValue.INLINE
				&& "annotation".equals(net.zamasoft.foliojet.ua.props.UAProps.TEXT_RUBY.getString(style.getUserAgent()));
		switch (ruby) {
		case CSSJRubyValue.NONE:
			break;
		case CSSJRubyValue.RUBY:
			if (display != DisplayValue.INLINE_BLOCK && !annotationInline) {
				return CSSJRubyValue.NONE_VALUE;
			}
			break;
		case CSSJRubyValue.RB:
			if (display != DisplayValue.BLOCK && !annotationInline) {
				return CSSJRubyValue.NONE_VALUE;
			}
			break;
		case CSSJRubyValue.RT:
			if (display != DisplayValue.BLOCK && !annotationInline) {
				return CSSJRubyValue.NONE_VALUE;
			}
			break;
		default:
			throw new IllegalStateException();
		}
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return CSSJRubyValue.NONE_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("none")) {
				return CSSJRubyValue.NONE_VALUE;
			} else if (ident.equals("ruby")) {
				return CSSJRubyValue.RUBY_VALUE;
			} else if (ident.equals("rb")) {
				return CSSJRubyValue.RB_VALUE;
			} else if (ident.equals("rt")) {
				return CSSJRubyValue.RT_VALUE;
			}
		}
		throw new PropertyException();
	}

}