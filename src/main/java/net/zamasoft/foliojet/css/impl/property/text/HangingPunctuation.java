package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.HangingPunctuationValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code hanging-punctuation}です(和文詰めH1、2026-07-31——
 * consult-codex-2026-07-31-text-spacing.txt)。継承プロパティ。
 * {@code none | [ first || [ force-end | allow-end ] ]}を実装する。
 * {@code first}はJLREQの段落開始、行末2値はぶら下げ組を表す。
 * {@code last}は要素の真の最終整形行判定と併せて実装するまで宣言無効。
 *
 * @author MIYABE Tatsuhiko
 */
public class HangingPunctuation extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new HangingPunctuation();

	/** {@code allow-end}ならtrueです。 */
	public static boolean isAllowEnd(CSSStyle style) {
		return ((HangingPunctuationValue) style.get(INFO)).allowsEnd();
	}

	public static boolean isForceEnd(CSSStyle style) {
		return ((HangingPunctuationValue) style.get(INFO)).forcesEnd();
	}

	public static boolean hangsFirst(CSSStyle style) {
		return ((HangingPunctuationValue) style.get(INFO)).hangsFirst();
	}

	protected HangingPunctuation() {
		super("hanging-punctuation");
	}

	public Value getDefault(CSSStyle style) {
		return HangingPunctuationValue.NONE;
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
			return HangingPunctuationValue.NONE;
		}
		boolean first = false;
		byte end = 0; // 1=allow, 2=force
		while (tokens.hasNext()) {
			if (tokens.eat("first")) {
				if (first) {
					throw new PropertyException();
				}
				first = true;
			} else if (tokens.eat("allow-end")) {
				if (end != 0) {
					throw new PropertyException();
				}
				end = 1;
			} else if (tokens.eat("force-end")) {
				if (end != 0) {
					throw new PropertyException();
				}
				end = 2;
			} else {
				throw new PropertyException();
			}
		}
		if (first) {
			return end == 1 ? HangingPunctuationValue.FIRST_ALLOW_END
					: end == 2 ? HangingPunctuationValue.FIRST_FORCE_END : HangingPunctuationValue.FIRST;
		}
		if (end == 1) {
			return HangingPunctuationValue.ALLOW_END;
		}
		if (end == 2) {
			return HangingPunctuationValue.FORCE_END;
		}
		throw new PropertyException();
	}
}
