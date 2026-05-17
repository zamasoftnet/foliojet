package jp.cssj.homare.impl.css.property.css3;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.value.Value;

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