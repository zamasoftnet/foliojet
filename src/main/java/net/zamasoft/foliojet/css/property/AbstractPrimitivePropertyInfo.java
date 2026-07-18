package net.zamasoft.foliojet.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * 分解不可能なプロパティです。
 *
 * @author MIYABE Tatsuhiko
 *          miyabe $
 */
public abstract class AbstractPrimitivePropertyInfo extends AbstractPropertyInfo implements PrimitivePropertyInfo {

	protected AbstractPrimitivePropertyInfo(String name) {
		super(name);
	}

	public final Property parse(TokenStream tokens, UserAgent ua, URI uri, boolean important)
			throws PropertyException {
		Value value;
		KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			// inherit / initial / unset(CSSStyle.getで解決される)
			value = global;
		} else {
			value = this.parseValue(tokens, ua, uri);
		}
		return new CompositeProperty(this.getName(), new Entry[] { new Entry(this, value) }, uri, important);
	}

	public PrimitivePropertyInfo getEffectiveInfo(CSSStyle style) {
		return this;
	}

	/**
	 * 宣言値のトークン列を単一の値に解釈します。
	 *
	 * @param tokens 宣言値(inherit は処理済み)
	 * @param ua
	 * @param uri
	 * @return
	 * @throws PropertyException 値を解釈できない場合
	 */
	public abstract Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException;
}
