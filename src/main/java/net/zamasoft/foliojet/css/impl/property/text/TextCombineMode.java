package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.TextCombineValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * 縦中横の種別を運ぶ内部プロパティです(2026-08-11)。
 *
 * <p>
 * {@code -cssj-text-combine}ショートハンド(標準名{@code
 * text-combine-upright}のエイリアスを含む)が展開時に設定する。作者が
 * 直接書くためのものではないが、他の内部プロパティと同じく解析可能に
 * しておく(値は{@code none}/{@code horizontal}/{@code all})。
 * 継承しない——縦中横は指定した要素だけの性質である。
 * </p>
 *
 * @see net.zamasoft.foliojet.css.value.TextCombineValue
 * @author MIYABE Tatsuhiko
 */
public class TextCombineMode extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextCombineMode();

	public static byte get(CSSStyle style) {
		TextCombineValue value = (TextCombineValue) style.get(INFO);
		return value.getTextCombine();
	}

	protected TextCombineMode() {
		super("-cssj-text-combine-mode");
	}

	public Value getDefault(CSSStyle style) {
		return TextCombineValue.NONE_VALUE;
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
			if (ident.equals("none")) {
				return TextCombineValue.NONE_VALUE;
			} else if (ident.equals("horizontal")) {
				return TextCombineValue.HORIZONTAL_VALUE;
			} else if (ident.equals("all")) {
				return TextCombineValue.ALL_VALUE;
			}
		}
		throw new PropertyException();
	}
}
