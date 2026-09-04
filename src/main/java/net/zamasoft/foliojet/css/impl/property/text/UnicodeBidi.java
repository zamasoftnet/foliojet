package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.value.UnicodeBidiValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class UnicodeBidi extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new UnicodeBidi();

	public static byte get(CSSStyle style) {
		UnicodeBidiValue value = (UnicodeBidiValue) style.get(INFO);
		return value.getUnicodeBidi();
	}

	private UnicodeBidi() {
		super("unicode-bidi");
	}

	public Value getDefault(CSSStyle style) {
		return UnicodeBidiValue.NORMAL_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident) {
			String ident = ((CssToken.Ident) lu).lower();
			if (ident.equals("normal")) {
				return UnicodeBidiValue.NORMAL_VALUE;
			} else if (ident.equals("embed")) {
				return UnicodeBidiValue.EMBED_VALUE;
			} else if (ident.equals("bidi-override")) {
				return UnicodeBidiValue.BIDI_OVERRIDE_VALUE;
			} else if (ident.equals("isolate")) {
				// css-writing-modes-3 §2.2。段落単位のUBA(2026-09-04、
				// bidi-isolation-design.md)まで値をそのまま保つ。旧近似(embed
				// への潰し込み)はレイアウト側のflag OFF経路が担う
				return UnicodeBidiValue.ISOLATE_VALUE;
			} else if (ident.equals("isolate-override")) {
				return UnicodeBidiValue.ISOLATE_OVERRIDE_VALUE;
			} else if (ident.equals("plaintext")) {
				return UnicodeBidiValue.PLAINTEXT_VALUE;
			}
			// 接頭辞つき別名(2026-08-29)は標準の値と同じ
			switch (ident) {
			case "-moz-isolate":
			case "-webkit-isolate":
				return UnicodeBidiValue.ISOLATE_VALUE;
			case "-moz-isolate-override":
			case "-webkit-isolate-override":
				return UnicodeBidiValue.ISOLATE_OVERRIDE_VALUE;
			case "-moz-plaintext":
			case "-webkit-plaintext":
				return UnicodeBidiValue.PLAINTEXT_VALUE;
			default:
				break;
			}
		}
		throw new PropertyException();
	}

}