package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.TextSpacingTrimValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code text-spacing-trim}です(和文詰めT1b、2026-07-31——
 * consult-codex-2026-07-31-text-spacing.txt)。継承プロパティ。
 * {@code normal}/{@code space-all}/{@code space-first}/{@code trim-start}/
 * {@code trim-both}/{@code auto}をCSS Text 4の意味で実装する。
 * {@code auto}はUAの高品質設定として{@code trim-both}相当。
 * {@code trim-all}は文字ごとの詰めを実装するまで宣言無効。
 *
 * @author MIYABE Tatsuhiko
 */
public class TextSpacingTrim extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextSpacingTrim();

	/** {@code space-all}(詰めなし)ならtrueです。 */
	public static boolean isSpaceAll(CSSStyle style) {
		return ((TextSpacingTrimValue) style.get(INFO)).isSpaceAll();
	}

	/** 行頭の全角始め括弧を天付きにする値ならtrueです。 */
	public static boolean trimsLineStart(CSSStyle style) {
		return ((TextSpacingTrimValue) style.get(INFO)).trimsLineStart();
	}

	/** 行末の全角終わり約物を常に半角化する値ならtrueです。 */
	public static boolean trimsLineEnd(CSSStyle style) {
		return ((TextSpacingTrimValue) style.get(INFO)).trimsLineEnd();
	}

	/** 初行・強制改行直後だけ行頭約物を全角のままにする値ならtrueです。 */
	public static boolean spacesFirstLine(CSSStyle style) {
		return ((TextSpacingTrimValue) style.get(INFO)).spacesFirstLine();
	}

	protected TextSpacingTrim() {
		super("text-spacing-trim");
	}

	public Value getDefault(CSSStyle style) {
		return TextSpacingTrimValue.NORMAL;
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
			return TextSpacingTrimValue.NORMAL;
		}
		if (tokens.eat("space-all")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return TextSpacingTrimValue.SPACE_ALL;
		}
		if (tokens.eat("space-first")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return TextSpacingTrimValue.SPACE_FIRST;
		}
		if (tokens.eat("trim-start")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return TextSpacingTrimValue.TRIM_START;
		}
		if (tokens.eat("trim-both")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return TextSpacingTrimValue.TRIM_BOTH;
		}
		if (tokens.eat("auto")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return TextSpacingTrimValue.AUTO;
		}
		throw new PropertyException();
	}
}
