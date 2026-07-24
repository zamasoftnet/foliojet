package net.zamasoft.foliojet.css.impl.property.ext;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJRubyValue;
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
		// ルビは注釈付きテキスト(文字に付く飾り)であり箱ではない
		// (2026-07-25仕様裁定、docs/history/2026-07-25-ruby-annotation-
		// spec-decision.md)。役割マーカー(ruby/rb/rt)はdisplayに
		// 依存しない——ルビ関連要素はStyleBuilderが常にINLINEへ強制し、
		// 単位の組み立ては文字処理層(StyledTextUnitizer)が行う。
		// 旧箱方式のdisplayガード(INLINE_BLOCK/BLOCK要求)は撤去した。
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