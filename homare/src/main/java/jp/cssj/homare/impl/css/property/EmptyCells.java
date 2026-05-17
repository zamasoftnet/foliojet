package jp.cssj.homare.impl.css.property;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractPrimitivePropertyInfo;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.TableValueUtils;
import jp.cssj.homare.css.value.EmptyCellsValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: EmptyCells.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class EmptyCells extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new EmptyCells();

	public static byte get(CSSStyle style) {
		EmptyCellsValue value = (EmptyCellsValue) style.get(INFO);
		return value.getEmptyCells();
	}

	protected EmptyCells() {
		super("empty-cells");
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value getDefault(CSSStyle style) {
		return EmptyCellsValue.SHOW_VALUE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		final Value value = TableValueUtils.toEmptyCells(lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}