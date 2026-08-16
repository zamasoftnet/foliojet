package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.impl.property.container.ContainerName;
import net.zamasoft.foliojet.css.impl.property.container.ContainerType;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.ContainerTypeValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.StringValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ValueListValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code container}ショートハンドです(css-contain-3、2026-08-15段2——
 * docs/history/2026-08-15-container-queries-design.md §5)。
 * {@code <container-name> [/ <container-type>]?}——typeを省略すると
 * {@code normal}。
 *
 * @author MIYABE Tatsuhiko
 */
public class ContainerShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new ContainerShorthand();

	protected ContainerShorthand() {
		super("container");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException {
		final KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(ContainerName.INFO, global);
			primitives.set(ContainerType.INFO, global);
			return;
		}
		final Value name;
		if (tokens.eat("none")) {
			name = KeywordValue.NONE;
		} else {
			final List<Value> names = new ArrayList<Value>();
			for (String ident = tokens.ident(); ident != null; ident = tokens.ident()) {
				if ("none".equalsIgnoreCase(ident)) {
					throw new PropertyException();
				}
				names.add(new StringValue(ident));
			}
			if (names.isEmpty()) {
				throw new PropertyException();
			}
			name = new ValueListValue(names.toArray(new Value[names.size()]));
		}
		Value type = ContainerTypeValue.NORMAL_VALUE;
		if (tokens.eatSlash()) {
			if (tokens.eat("inline-size")) {
				type = ContainerTypeValue.INLINE_SIZE_VALUE;
			} else if (tokens.eat("size")) {
				type = ContainerTypeValue.SIZE_VALUE;
			} else if (tokens.eat("normal")) {
				type = ContainerTypeValue.NORMAL_VALUE;
			} else {
				throw new PropertyException();
			}
		}
		if (tokens.hasNext()) {
			throw new PropertyException();
		}
		primitives.set(ContainerName.INFO, name);
		primitives.set(ContainerType.INFO, type);
	}
}
