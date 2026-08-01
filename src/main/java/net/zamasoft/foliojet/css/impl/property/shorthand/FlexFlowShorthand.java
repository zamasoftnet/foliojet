package net.zamasoft.foliojet.css.impl.property.shorthand;

import java.net.URI;

import net.zamasoft.foliojet.css.impl.property.flex.FlexDirectionProperty;
import net.zamasoft.foliojet.css.impl.property.flex.FlexWrapProperty;
import net.zamasoft.foliojet.css.property.AbstractShorthandPropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.property.ShorthandPropertyInfo;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.FlexDirectionValue;
import net.zamasoft.foliojet.css.value.FlexWrapValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code flex-flow}ショートハンドです(Flex F1a、2026-08-02)。
 * {@code <flex-direction> || <flex-wrap>}——省略側は初期値。
 *
 * @author MIYABE Tatsuhiko
 */
public class FlexFlowShorthand extends AbstractShorthandPropertyInfo {
	public static final ShorthandPropertyInfo INFO = new FlexFlowShorthand();

	private FlexFlowShorthand() {
		super("flex-flow");
	}

	public void parseValues(TokenStream tokens, UserAgent ua, URI uri, Primitives primitives)
			throws PropertyException {
		final KeywordValue global = tokens.globalKeyword();
		if (global != null) {
			primitives.set(FlexDirectionProperty.INFO, global);
			primitives.set(FlexWrapProperty.INFO, global);
			return;
		}
		FlexDirectionValue direction = null;
		FlexWrapValue wrap = null;
		while (tokens.hasNext()) {
			if (direction == null) {
				direction = FlexDirectionProperty.parseKeyword(tokens);
				if (direction != null) {
					continue;
				}
			}
			if (wrap == null) {
				wrap = FlexWrapProperty.parseKeyword(tokens);
				if (wrap != null) {
					continue;
				}
			}
			throw new PropertyException();
		}
		if (direction == null && wrap == null) {
			throw new PropertyException();
		}
		primitives.set(FlexDirectionProperty.INFO, direction != null ? direction : FlexDirectionValue.ROW);
		primitives.set(FlexWrapProperty.INFO, wrap != null ? wrap : FlexWrapValue.NOWRAP);
	}
}
