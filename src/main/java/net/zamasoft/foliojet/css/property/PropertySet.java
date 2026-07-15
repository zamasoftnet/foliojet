package net.zamasoft.foliojet.css.property;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * ある文脈(要素・@page・@font-face)で解釈可能なプロパティの集合です。
 *
 * @author MIYABE Tatsuhiko
 */
public abstract class PropertySet {
	private static final Logger LOG = Logger.getLogger(PropertySet.class.getName());

	private final Map<String, PropertyInfo> nameToInfo = new HashMap<String, PropertyInfo>();

	/**
	 * プロパティを登録します。
	 */
	protected final void put(PropertyInfo... infos) {
		for (PropertyInfo info : infos) {
			this.nameToInfo.put(info.getName(), info);
		}
	}

	/**
	 * 別名(ベンダープレフィックス等)でプロパティを登録します。
	 */
	protected final void alias(String name, PropertyInfo info) {
		this.nameToInfo.put(name, info);
	}

	protected PropertyInfo getPropertyParser(String name) {
		return this.nameToInfo.get(name);
	}

	public final Property parseDeclaration(String name, List<CssToken> value, UserAgent ua, URI uri,
			boolean important) {
		PropertyInfo ph = this.getPropertyParser(name.toLowerCase());
		if (ph != null) {
			TokenStream tokens = new TokenStream(value);
			try {
				return ph.parse(tokens, ua, uri, important);
			} catch (PropertyException e) {
				String m = name + ":" + tokens + ":" + e.getMessage();
				LOG.log(Level.FINE, m, e);
				ua.message(MessageCodes.WARN_BAD_CSS_ARGMENTS, name, tokens.toString(), e.getMessage());
				return null;
			}
		}
		ua.message(MessageCodes.WARN_UNSUPPORTED_CSS_PROPERTY, name);
		return null;
	}
}
