package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.TableValueUtils;
import jp.cssj.homare.css.value.TableLayoutValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: TableLayout.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class TableLayout extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TableLayout();

	public static byte get(CSSStyle style) {
		TableLayoutValue value = (TableLayoutValue) style.get(INFO);
		return value.getTableLayout();
	}

	protected TableLayout() {
		super("table-layout");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return TableLayoutValue.AUTO_VALUE;
	}

	public boolean isInherited() {
		return false;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		final Value value = TableValueUtils.toTableLayout(lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}