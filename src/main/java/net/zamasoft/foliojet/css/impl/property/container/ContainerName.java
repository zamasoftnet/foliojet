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
 * {@code container-name} property (css-contain-3, 2026-08-15 stage 2:
 * development record §5).
 * {@code none | <custom-ident>+} (space-separated; multiple names allowed).
 *
 * <p>
 * Stores {@code none} as {@link KeywordValue#NONE}, otherwise as {@link ValueListValue}
 * (a sequence of {@link StringValue}). Name resolution (matching the name in
 * {@code @container <name> (...)} in stage 6) is outside this class's responsibility;
 * this class only accepts and stores the syntax.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class ContainerName extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ContainerName();

	/** Returns an empty array for {@code none}, otherwise a sequence of names. */
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
