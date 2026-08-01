package net.zamasoft.foliojet.css.impl.property.flex;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code flex-grow}(既定0)/{@code flex-shrink}(既定1)です
 * (Flex F1a、2026-08-02)。非負の数値のみ(負は宣言無効——§7.2.1/7.2.2)。
 *
 * @author MIYABE Tatsuhiko
 */
public class FlexFactor extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo GROW = new FlexFactor("flex-grow", RealValue.ZERO);

	public static final PrimitivePropertyInfo SHRINK = new FlexFactor("flex-shrink", RealValue.ONE);

	public static double get(CSSStyle style, PrimitivePropertyInfo info) {
		return ((RealValue) style.get(info)).getReal();
	}

	private final RealValue defaultValue;

	private FlexFactor(final String name, final RealValue defaultValue) {
		super(name);
		this.defaultValue = defaultValue;
	}

	public Value getDefault(CSSStyle style) {
		return this.defaultValue;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final RealValue value = parseFactor(tokens);
		if (value == null || tokens.hasNext()) {
			throw new PropertyException();
		}
		return value;
	}

	/** 1つの非負数値を読み取ります(flexショートハンドと共用)。不正はnull。 */
	public static RealValue parseFactor(final TokenStream tokens) {
		if (tokens.peek() instanceof CssToken.Num num && num.value() >= 0) {
			tokens.next();
			return RealValue.create((float) num.value());
		}
		return null;
	}
}
