package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.flex.FlexBasisProperty;
import net.zamasoft.foliojet.css.impl.property.flex.FlexFactor;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.FlexBasisValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.RealValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code flex}ショートハンドです(Flex F1a、2026-08-02)。
 * {@code none | [ <flex-grow> <flex-shrink>? || <flex-basis> ]}(§7.1)。
 * 省略時の値は各プロパティの初期値ではなくショートハンド既定
 * (grow=1・shrink=1・basis=0)——{@code flex: auto}=1 1 auto、
 * {@code flex: 2}=2 1 0。{@code none}=0 0 auto。
 * 2因子の後の単位なし0はbasis 0(§7.1の構文注記)。
 *
 * @author MIYABE Tatsuhiko
 */
public class FlexShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new FlexShorthand();

	private FlexShorthand() {
		super("flex");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException {
		final KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(FlexFactor.GROW, global);
			primitives.set(FlexFactor.SHRINK, global);
			primitives.set(FlexBasisProperty.INFO, global);
			return;
		}
		if (tokens.eat("none")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			primitives.set(FlexFactor.GROW, RealValue.ZERO);
			primitives.set(FlexFactor.SHRINK, RealValue.ZERO);
			primitives.set(FlexBasisProperty.INFO, FlexBasisValue.AUTO_VALUE);
			return;
		}
		RealValue grow = null, shrink = null;
		FlexBasisValue basis = null;
		while (tokens.hasNext()) {
			if (tokens.peek() instanceof CssToken.Num num) {
				if (grow == null) {
					grow = FlexFactor.parseFactor(tokens);
					if (grow == null) {
						throw new PropertyException();
					}
					continue;
				}
				if (shrink == null) {
					shrink = FlexFactor.parseFactor(tokens);
					if (shrink == null) {
						throw new PropertyException();
					}
					continue;
				}
				// 2因子の後の単位なし0はbasis 0
				if (basis == null && num.value() == 0) {
					tokens.next();
					basis = FlexBasisValue.size(AbsoluteLengthValue.ZERO);
					continue;
				}
				throw new PropertyException();
			}
			if (basis == null) {
				basis = FlexBasisProperty.parseBasis(tokens, ua);
				if (basis != null) {
					continue;
				}
			}
			throw new PropertyException();
		}
		if (grow == null && shrink == null && basis == null) {
			throw new PropertyException();
		}
		// shrinkだけの指定は構文上ない(数値1つ目はgrow)
		primitives.set(FlexFactor.GROW, grow != null ? grow : RealValue.ONE);
		primitives.set(FlexFactor.SHRINK, shrink != null ? shrink : RealValue.ONE);
		primitives.set(FlexBasisProperty.INFO,
				basis != null ? basis : FlexBasisValue.size(AbsoluteLengthValue.ZERO));
	}
}
