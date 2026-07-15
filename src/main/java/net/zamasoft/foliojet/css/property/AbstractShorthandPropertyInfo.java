package net.zamasoft.foliojet.css.property;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * Shorthand特性です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractShorthandPropertyInfo.java 3806 2012-07-10 07:03:19Z
 *          miyabe $
 */
public abstract class AbstractShorthandPropertyInfo extends AbstractPropertyInfo implements ShorthandPropertyInfo {
	protected AbstractShorthandPropertyInfo(String name) {
		super(name);
	}

	/**
	 * 最小単位の特性と値のリストです。
	 * 
	 * @author MIYABE Tatsuhiko
	 * @version $Id: AbstractShorthandPropertyInfo.java 3806 2012-07-10 07:03:19Z
	 *          miyabe $
	 */
	protected static final class Primitives {
		private final List<Entry> entries = new ArrayList<Entry>();

		public void set(PrimitivePropertyInfo info, Value value) {
			Entry entry = new Entry(info, value);
			for (int i = 0; i < this.entries.size(); ++i) {
				Entry e = (Entry) this.entries.get(i);
				if (e.getPrimitivePropertyInfo() == info) {
					this.entries.set(i, entry);
					return;
				}
			}
			this.entries.add(entry);
		}

		public String toString() {
			StringBuilder buff = new StringBuilder();
			for (int i = 0; i < this.entries.size(); ++i) {
				Entry e = (Entry) this.entries.get(i);
				buff.append(e).append(' ');
			}
			return buff.toString();
		}
	}

	public Property parse(TokenStream tokens, UserAgent ua, URI uri, boolean important) throws PropertyException {
		Primitives primitives = new Primitives();
		this.parseValues(tokens, ua, uri, primitives);
		Entry[] entries = (Entry[]) primitives.entries.toArray(new Entry[primitives.entries.size()]);
		return new CompositeProperty(this.getName(), entries, uri, important);
	}

	/**
	 * 
	 * @param lu
	 * @param ua
	 * @param uri
	 * @param primitives
	 * @throws PropertyException
	 */
	/**
	 * 宣言値のトークン列を分解し、対応する単純プロパティ群を設定します。
	 */
	public abstract void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException;
}
