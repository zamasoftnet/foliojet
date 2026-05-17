package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.TableValueUtils;
import jp.cssj.homare.css.value.BorderCollapseValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: BorderCollapse.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class BorderCollapse extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new BorderCollapse();

	public static byte get(CSSStyle style) {
		BorderCollapseValue value = (BorderCollapseValue) style.get(INFO);
		return value.getBorderCollapse();
	}

	protected BorderCollapse() {
		super("border-collapse");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return BorderCollapseValue.SEPARATE_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		final Value value = TableValueUtils.toBorderCollapse(lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}