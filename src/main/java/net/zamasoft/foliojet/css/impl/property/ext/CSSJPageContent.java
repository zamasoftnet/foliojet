package net.zamasoft.foliojet.css.impl.property.ext;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.PageRule;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.IntegerValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/** 再生成ボックスの名前と頁マスクを内部の二特性へ分解します。R1a では保持だけ行います。 */
public final class CSSJPageContent extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new CSSJPageContent();

	public static final PrimitivePropertyInfo INFO_NAME = new Component("-cssj-page-content-name", KeywordValue.NONE);

	public static final PrimitivePropertyInfo INFO_PAGES = new Component("-cssj-page-content-pages", IntegerValue.ZERO);

	private static final PrimitivePropertyInfo[] PRIMITIVES = { INFO_NAME, INFO_PAGES };

	private CSSJPageContent() {
		super("-cssj-page-content");
	}

	/** 未指定なら null、指定されていれば大文字小文字を保持した名前を返します。 */
	public static String getName(final CSSStyle style) {
		final Value value = style.get(INFO_NAME);
		return value instanceof StringValue name ? name.getString() : null;
	}

	/** 0 は全頁、それ以外は {@link PageRule} の頁マスクです。 */
	public static byte getPages(final CSSStyle style) {
		return (byte) ((IntegerValue) style.get(INFO_PAGES)).getInteger();
	}

	protected PrimitivePropertyInfo[] longhands() {
		return PRIMITIVES;
	}

	public void parseValues(final TokenStream tokens, final UserAgent ua, final URI uri,
			final Primitives primitives) throws PropertyException {
		final String name = parseName(tokens);
		if (name == null) {
			// 裸の none は無効化(3.2 と同じ。引用した 'none' は名前)
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			primitives.set(INFO_NAME, KeywordValue.NONE);
			primitives.set(INFO_PAGES, IntegerValue.ZERO);
			return;
		}
		byte pages = 0;
		while (tokens.hasNext()) {
			final CssToken token = tokens.next();
			if (!(token instanceof CssToken.Ident ident)) {
				throw new PropertyException();
			}
			pages |= switch (ident.lower()) {
			case "first" -> PageRule.PSEUDO_FIRST;
			case "left" -> PageRule.PSEUDO_LEFT;
			case "right" -> PageRule.PSEUDO_RIGHT;
			case "single" -> PageRule.PSEUDO_SINGLE;
			default -> throw new PropertyException();
			};
		}
		primitives.set(INFO_NAME, new StringValue(name));
		primitives.set(INFO_PAGES, IntegerValue.create(pages));
	}

	/** legacy の名前は識別子と文字列の両方を受け付けます。裸の {@code none} は {@code null}(無効化)。 */
	static String parseName(final TokenStream tokens) throws PropertyException {
		return switch (tokens.next()) {
		case CssToken.Ident ident -> "none".equalsIgnoreCase(ident.name()) ? null : ident.name();
		case CssToken.Str string -> string.value();
		case null, default -> throw new PropertyException();
		};
	}

	/** CSSStyle のカスケードで名前と頁条件を独立に保持する内部特性です。 */
	private static final class Component extends AbstractPrimitivePropertyInfo {
		private final Value defaultValue;

		Component(final String name, final Value defaultValue) {
			super(name);
			this.defaultValue = defaultValue;
		}

		public Value getDefault(final CSSStyle style) {
			return this.defaultValue;
		}

		public boolean isInherited() {
			return false;
		}

		public Value getComputedValue(final Value value, final CSSStyle style) {
			return value;
		}

		public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri)
				throws PropertyException {
			// 外部の宣言名としては登録しません。
			throw new PropertyException();
		}
	}
}
