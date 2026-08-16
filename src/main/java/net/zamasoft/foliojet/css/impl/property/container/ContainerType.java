package net.zamasoft.foliojet.css.impl.property.container;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.ContainerTypeValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code container-type}プロパティです(css-contain-3、2026-08-15段2——
 * docs/history/2026-08-15-container-queries-design.md §5)。
 * {@code normal | inline-size | size}。値の意味は{@link ContainerTypeValue}参照。
 *
 * @author MIYABE Tatsuhiko
 */
public class ContainerType extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ContainerType();

	public static byte get(CSSStyle style) {
		ContainerTypeValue value = (ContainerTypeValue) style.get(INFO);
		return value.getContainerType();
	}

	protected ContainerType() {
		super("container-type");
	}

	public Value getDefault(CSSStyle style) {
		return ContainerTypeValue.NORMAL_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.eat("normal")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return ContainerTypeValue.NORMAL_VALUE;
		}
		if (tokens.eat("inline-size")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return ContainerTypeValue.INLINE_SIZE_VALUE;
		}
		if (tokens.eat("size")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return ContainerTypeValue.SIZE_VALUE;
		}
		throw new PropertyException();
	}
}
