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

		/** 設定済みの値。無ければnull(2026-08-29、多層背景の合成に使う)。 */
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
	 * このショートハンドが展開する最小単位の特性。{@code inherit}/
	 * {@code initial}/{@code unset}を全体キーワードとして受けるために使う
	 * (2026-08-29)。null(既定)なら{@link #parseValues}が自分で扱う
	 * ({@code background}等は既にそうしている)。
	 *
	 * <p>
	 * 以前は{@code padding: inherit}等が「不正値」として捨てられていた。
	 * 値パーサが{@code Keyword}トークンを長さとして解釈できずnullを返し、
	 * その後の{@code == KeywordValue.INHERIT}判定へ届かなかったため。
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
