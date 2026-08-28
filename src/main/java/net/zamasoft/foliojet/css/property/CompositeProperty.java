package net.zamasoft.foliojet.css.property;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.value.Value;

/**
 * 複数のプロパティの組み合わせです。
 * 
 * <p>
 * shorthandプロパティを実装するために用います。
 * </p>
 * 
 * @author MIYABE Tatsuhiko
 */
public class CompositeProperty implements Property {
	private final String name;
	private final URI uri;
	private final boolean important;
	private final Entry[] entries;

	public static class Entry {
		private final PrimitivePropertyInfo info;
		private final Value value;

		public Entry(PrimitivePropertyInfo info, Value value) {
			this.info = info;
			this.value = value;
		}

		public PrimitivePropertyInfo getPrimitivePropertyInfo() {
			return this.info;
		}

		public Value getValue() {
			return this.value;
		}

		public String toString() {
			return this.info + "=" + this.value;
		}
	}

	protected CompositeProperty(String name, Entry[] entries, URI uri, boolean important) {
		this.name = name;
		this.entries = entries;
		this.uri = uri;
		this.important = important;
	}

	public String getName() {
		return this.name;
	}

	/** 展開された最小単位の特性と値(テスト・検査用、2026-08-29)。 */
	public Entry[] getEntries() {
		return this.entries.clone();
	}

	public URI getURI() {
		return this.uri;
	}

	public boolean isImportant() {
		return this.important;
	}

	/**
	 * プロパティを適用します。
	 */
	public void applyProperty(CSSStyle style) {
		for (int i = 0; i < this.entries.length; ++i) {
			Entry entry = this.entries[i];
			PrimitivePropertyInfo info = entry.getPrimitivePropertyInfo();
			style.set(info.getEffectiveInfo(style), entry.getValue(),
					this.important ? CSSStyle.MODE_IMPORTANT : CSSStyle.MODE_NORMAL);
		}
	}

	public String toString() {
		StringBuilder buff = new StringBuilder(this.name + ":");
		for (int i = 0; i < this.entries.length; ++i) {
			Entry entry = this.entries[i];
			buff.append(' ');
			buff.append(entry.getValue());
		}
		buff.append((this.important ? " !" : "") + " [uri=" + this.uri + "]");
		return buff.toString();
	}
}