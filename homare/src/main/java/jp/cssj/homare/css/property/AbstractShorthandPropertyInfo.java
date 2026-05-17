package jp.cssj.homare.css.property;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.homare.css.property.CompositeProperty.Entry;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

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
			StringBuffer buff = new StringBuffer();
			for (int i = 0; i < this.entries.size(); ++i) {
				Entry e = (Entry) this.entries.get(i);
				buff.append(e).append(' ');
			}
			return buff.toString();
		}
	}

	public Property parseProperty(LexicalUnit lu, UserAgent ua, URI uri, boolean important) throws PropertyException {
		Primitives primitives = new Primitives();
		this.parseProperty(lu, ua, uri, primitives);
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
	public abstract void parseProperty(LexicalUnit lu, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException;
}
