package net.zamasoft.foliojet.css.property;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * A shorthand property.
 *
 * @author MIYABE Tatsuhiko
 *          miyabe $
 */
public abstract class AbstractShorthandPropertyInfo extends AbstractPropertyInfo implements ShorthandPropertyInfo {
	protected AbstractShorthandPropertyInfo(String name) {
		super(name);
	}

	/**
	 * A list of primitive properties and their values.
	 *
	 * @author MIYABE Tatsuhiko
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

		/** The value already set, or null if absent (2026-08-29, used to compose multilayer backgrounds). */
		public Value get(PrimitivePropertyInfo info) {
			for (final Entry e : this.entries) {
				if (e.getPrimitivePropertyInfo() == info) {
					return e.getValue();
				}
			}
			return null;
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

	/**
	 * The primitive properties this shorthand expands into. Used to accept
	 * {@code inherit}/{@code initial}/{@code unset} as CSS-wide keywords
	 * (2026-08-29). If null (the default), {@link #parseValues} handles them itself
	 * (as {@code background}, etc. already do).
	 *
	 * <p>
	 * Previously, declarations such as {@code padding: inherit} were discarded as invalid values.
	 * The value parser could not interpret a {@code Keyword} token as a length and returned null,
	 * so execution never reached the subsequent {@code == KeywordValue.INHERIT} check.
	 * </p>
	 */
	protected PrimitivePropertyInfo[] longhands() {
		return null;
	}

	public Property parse(TokenStream tokens, UserAgent ua, URI uri, boolean important) throws PropertyException {
		Primitives primitives = new Primitives();
		final PrimitivePropertyInfo[] longhands = this.longhands();
		final net.zamasoft.foliojet.css.value.KeywordValue global = longhands == null ? null : tokens.globalKeyword();
		if (global != null) {
			for (final PrimitivePropertyInfo info : longhands) {
				primitives.set(info, global);
			}
		} else {
			this.parseValues(tokens, ua, uri, primitives);
		}
		Entry[] entries = (Entry[]) primitives.entries.toArray(new Entry[primitives.entries.size()]);
		return new CompositeProperty(this.getName(), entries, uri, important);
	}

	/**
	 * Decomposes the declaration's token sequence and sets the corresponding primitive properties.
	 */
	public abstract void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException;
}
