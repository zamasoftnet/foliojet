package net.zamasoft.foliojet.css.impl.property.content;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.counterstyle.CounterStyles;
import net.zamasoft.foliojet.css.value.ListStyleTypeSource;
import net.zamasoft.foliojet.css.value.ListStyleTypeValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * @author MIYABE Tatsuhiko
 */
public class ListStyleType extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ListStyleType();

	public static short get(CSSStyle style) {
		ListStyleTypeSource value = (ListStyleTypeSource) style.get(INFO);
		return value.getListStyleType();
	}

	protected ListStyleType() {
		super("list-style-type");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return ListStyleTypeValue.DISC_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		final String text;
		if (lu instanceof CssToken.Ident ident) {
			text = ident.name();
		} else if (lu instanceof CssToken.Str str) {
			text = str.value();
		} else {
			throw new PropertyException();
		}
		// 組み込みにない名前は著者定義カウンタスタイル(@counter-style)
		// として扱う。定義が後から現れても、定義がまったく無くても
		// (その場合はdecimalへ落ちる)よい——CSSに出現順の制約はない
		return CounterStyles.styleValue(ua, text);
	}

}