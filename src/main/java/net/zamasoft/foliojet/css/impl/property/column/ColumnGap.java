package net.zamasoft.foliojet.css.impl.property.column;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BorderValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.RelativeLengthValue;

/**
 * <a href="http://www.w3.org/TR/CSS21/box.html#propdef-border-left-width">
 * border-left-width 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class ColumnGap extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new ColumnGap();

	/**
	 * 段組用の使用値です({@code normal}=1em。従来挙動)。Grid G0で
	 * {@code normal}をcomputed valueに残す形へ変更した——multicolは
	 * ここで1emへ、Gridは{@link #getForGrid}で0へ解決する
	 * (consult-codex-2026-07-31-grid.txt §2)。
	 */
	public static double get(CSSStyle style) {
		final Value value = style.get(INFO);
		if (value == net.zamasoft.foliojet.css.value.KeywordValue.NORMAL) {
			return net.zamasoft.foliojet.css.impl.property.font.FontSize.get(style);
		}
		return ((AbsoluteLengthValue) value).getLength();
	}

	/** Grid用の使用値です({@code normal}=0)。 */
	public static double getForGrid(CSSStyle style) {
		final Value value = style.get(INFO);
		if (value == net.zamasoft.foliojet.css.value.KeywordValue.NORMAL) {
			return 0;
		}
		return ((AbsoluteLengthValue) value).getLength();
	}

	protected ColumnGap() {
		super("-cssj-column-gap");
	}

	public Value getDefault(CSSStyle style) {
		return net.zamasoft.foliojet.css.value.KeywordValue.NORMAL;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		if (value == net.zamasoft.foliojet.css.value.KeywordValue.NORMAL) {
			return value;
		}
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isNormal(lu)) {
			return net.zamasoft.foliojet.css.value.KeywordValue.NORMAL;
		}
		LengthValue value = BorderValueUtils.toBorderWidth(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}