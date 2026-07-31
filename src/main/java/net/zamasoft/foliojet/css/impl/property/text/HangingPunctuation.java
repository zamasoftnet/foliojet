package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code hanging-punctuation}です(和文詰めH1、2026-07-31——
 * consult-codex-2026-07-31-text-spacing.txt)。継承プロパティ。
 * 初期サブセット: {@code none | allow-end}(行末句読点のぶら下げ——
 * JLREQのぶら下げ組。通常位置に収まるならぶら下げない)。
 * {@code first}/{@code last}/{@code force-end}はサブセット外(宣言無効)。
 *
 * @author MIYABE Tatsuhiko
 */
public class HangingPunctuation extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new HangingPunctuation();

	/** {@code allow-end}ならtrueです。 */
	public static boolean isAllowEnd(CSSStyle style) {
		return style.get(INFO) != KeywordValue.NONE;
	}

	protected HangingPunctuation() {
		super("hanging-punctuation");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NONE;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.eat("none")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return KeywordValue.NONE;
		}
		if (tokens.eat("allow-end")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			// allow-end。内部表現はKeywordValue.NORMALを流用
			return KeywordValue.NORMAL;
		}
		throw new PropertyException();
	}
}
