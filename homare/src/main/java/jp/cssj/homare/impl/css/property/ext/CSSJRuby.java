package jp.cssj.homare.impl.css.property.ext;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.value.DisplayValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.css.value.ext.CSSJRubyValue;
import jp.cssj.homare.impl.css.property.Display;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSJRuby.java 1552 2018-04-26 01:43:24Z miyabe $
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
		switch (ruby) {
		case CSSJRubyValue.NONE:
			break;
		case CSSJRubyValue.RUBY:
			if (display != DisplayValue.INLINE_BLOCK) {
				return CSSJRubyValue.NONE_VALUE;
			}
			break;
		case CSSJRubyValue.RB:
			if (display != DisplayValue.BLOCK) {
				return CSSJRubyValue.NONE_VALUE;
			}
			break;
		case CSSJRubyValue.RT:
			if (display != DisplayValue.BLOCK) {
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

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		short luType = lu.getLexicalUnitType();
		switch (luType) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("none")) {
				return CSSJRubyValue.NONE_VALUE;
			} else if (ident.equals("ruby")) {
				return CSSJRubyValue.RUBY_VALUE;
			} else if (ident.equals("rb")) {
				return CSSJRubyValue.RB_VALUE;
			} else if (ident.equals("rt")) {
				return CSSJRubyValue.RT_VALUE;
			}

		default:
			throw new PropertyException();
		}
	}

}