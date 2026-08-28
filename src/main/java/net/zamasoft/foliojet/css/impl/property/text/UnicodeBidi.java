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
				// css-writing-modes-3 §2.2。isolateは外側の双方向解決から
				// 中身を隔離する。静的組版では埋め込み(embed)で近似する
				// (2026-08-29)——捨てると要素ごとの方向指定が丸ごと失われる
				return UnicodeBidiValue.EMBED_VALUE;
			} else if (ident.equals("isolate-override")) {
				return UnicodeBidiValue.BIDI_OVERRIDE_VALUE;
			} else if (ident.equals("plaintext")) {
				// 段落ごとの一次方向の自動判定は先読みを要するため未対応。
				// normalへ退化させる
				return UnicodeBidiValue.NORMAL_VALUE;
			}
			// css-writing-modes-3の値と接頭辞つき別名(2026-08-29)。isolateは
			// embedで近似(隣接テキストへの影響の遮断は未対応)、
			// isolate-overrideはbidi-override、plaintextは段落方向の自動判定
			// が無いのでnormalで近似
			switch (ident) {
			case "isolate":
			case "-moz-isolate":
			case "-webkit-isolate":
				return UnicodeBidiValue.EMBED_VALUE;
			case "isolate-override":
			case "-moz-isolate-override":
			case "-webkit-isolate-override":
				return UnicodeBidiValue.BIDI_OVERRIDE_VALUE;
			case "plaintext":
			case "-moz-plaintext":
			case "-webkit-plaintext":
				return UnicodeBidiValue.NORMAL_VALUE;
			default:
				break;
			}
		}
		throw new PropertyException();
	}

}