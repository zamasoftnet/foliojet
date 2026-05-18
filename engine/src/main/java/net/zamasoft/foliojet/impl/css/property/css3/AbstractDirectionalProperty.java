package net.zamasoft.foliojet.impl.css.property.css3;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.value.Value;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: AbstractDirectionalProperty.java 3804 2012-07-10 06:53:45Z
 *          miyabe $
 */
public abstract class AbstractDirectionalProperty extends AbstractPrimitivePropertyInfo {
	protected AbstractDirectionalProperty(String name) {
		super(name);
	}

	public final Value getDefault(CSSStyle style) {
		throw new UnsupportedOperationException();
	}

	public final boolean isInherited() {
		throw new UnsupportedOperationException();
	}

	public final Value getComputedValue(Value value, CSSStyle style) {
		throw new UnsupportedOperationException();
	}

	public final int getPriority() {
		return 1;
	}
}