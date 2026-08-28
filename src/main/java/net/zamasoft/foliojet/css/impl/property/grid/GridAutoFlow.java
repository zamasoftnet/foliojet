package net.zamasoft.foliojet.css.impl.property.grid;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.GridAutoFlowValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code grid-auto-flow}です(css-grid-1 §7.7、2026-08-29——50サイト掃過で
 * 81回/13サイト)。{@code [ row | column ] || dense}。{@code column}は
 * 自動配置を列方向(行を埋めてから次の列)にし、必要な列を暗黙に作る。
 * {@code dense}は各itemの探索をグリッド先頭から始める。
 *
 * @author MIYABE Tatsuhiko
 */
public class GridAutoFlow extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new GridAutoFlow();

	public static GridAutoFlowValue get(CSSStyle style) {
		return (GridAutoFlowValue) style.get(INFO);
	}

	protected GridAutoFlow() {
		super("grid-auto-flow");
	}

	public Value getDefault(CSSStyle style) {
		return GridAutoFlowValue.ROW;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		boolean direction = false, column = false, dense = false;
		while (tokens.hasNext()) {
			if (!direction && tokens.eat("row")) {
				direction = true;
			} else if (!direction && tokens.eat("column")) {
				direction = true;
				column = true;
			} else if (!dense && tokens.eat("dense")) {
				dense = true;
			} else {
				throw new PropertyException();
			}
		}
		if (!direction && !dense) {
			throw new PropertyException();
		}
		return GridAutoFlowValue.of(column, dense);
	}
}
