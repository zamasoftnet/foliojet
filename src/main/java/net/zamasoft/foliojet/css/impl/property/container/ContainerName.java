package net.zamasoft.foliojet.css.impl.property.container;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code container-name}プロパティです(css-contain-3、2026-08-15段2——
 * docs/history/2026-08-15-container-queries-design.md §5)。
 * {@code none | <custom-ident>+}(空白区切り、複数可)。
 *
 * <p>
 * {@code none}は{@link KeywordValue#NONE}、それ以外は{@link ValueListValue}
 * (中身は{@link StringValue}の並び)として保持する。名前解決(段6で
 * {@code @container <name> (...)}の名前と照合する側)は本クラスの
 * 責務外——ここでは構文の受理と保持だけを行う。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class ContainerName extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ContainerName();

	/** 値が{@code none}なら空配列、それ以外は名前の並びを返します。 */
	public static String[] get(CSSStyle style) {
		Value value = style.get(INFO);
		if (value == KeywordValue.NONE) {
			return EMPTY;
		}
		Value[] values = ((ValueListValue) value).getValues();
		String[] names = new String[values.length];
		for (int i = 0; i < values.length; ++i) {
			names[i] = ((StringValue) values[i]).getString();
		}
		return names;
	}

	private static final String[] EMPTY = new String[0];

	protected ContainerName() {
		super("container-name");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.eat("none")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return KeywordValue.NONE;
		}
		final java.util.List<Value> names = new java.util.ArrayList<Value>();
		while (tokens.hasNext()) {
			final String name = tokens.ident();
			if (name == null || "none".equalsIgnoreCase(name)) {
				throw new PropertyException();
			}
			names.add(new StringValue(name));
		}
		if (names.isEmpty()) {
			throw new PropertyException();
		}
		return new ValueListValue(names.toArray(new Value[names.size()]));
	}
}
