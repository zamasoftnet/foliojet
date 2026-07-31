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
 * {@code text-spacing-trim}です(和文詰めT1b、2026-07-31——
 * consult-codex-2026-07-31-text-spacing.txt)。継承プロパティ。
 * 初期サブセット: {@code normal}(連続約物の詰め=T1aでfont層から
 * 移管した挙動)| {@code space-all}(詰めなし=全角のまま)。
 * {@code trim-start}/{@code trim-both}は次増分、{@code space-first}/
 * {@code trim-all}/{@code auto}はサブセット外(宣言無効)。
 *
 * @author MIYABE Tatsuhiko
 */
public class TextSpacingTrim extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextSpacingTrim();

	/** {@code space-all}(詰めなし)ならtrueです。 */
	public static boolean isSpaceAll(CSSStyle style) {
		return style.get(INFO) == KeywordValue.NONE;
	}

	protected TextSpacingTrim() {
		super("text-spacing-trim");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.NORMAL;
	}

	public boolean isInherited() {
		return true;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return value;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.eat("normal")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return KeywordValue.NORMAL;
		}
		if (tokens.eat("space-all")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			// space-all=詰め無効。内部表現はKeywordValue.NONEを流用
			return KeywordValue.NONE;
		}
		throw new PropertyException();
	}
}
