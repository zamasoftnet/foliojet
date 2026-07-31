package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.TextAutospaceValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code text-autospace}です(和文詰めA1、2026-07-31——
 * consult-codex-2026-07-31-text-spacing.txt)。継承プロパティ。
 * computed initialは仕様どおり{@code normal}だが、UAスタイルシートが
 * {@code html { text-autospace: no-autospace }}を与えるため既存文書の
 * 見た目は変わらない(既定on化は将来UAの1行変更で行う——答申Q4)。
 *
 * @author MIYABE Tatsuhiko
 */
public class TextAutospace extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextAutospace();

	/** 実効フラグ({@code TextAutospaceValue.ALPHA}|{@code NUMERIC})。 */
	public static byte getFlags(CSSStyle style) {
		return ((TextAutospaceValue) style.get(INFO)).getFlags();
	}

	protected TextAutospace() {
		super("text-autospace");
	}

	public Value getDefault(CSSStyle style) {
		return TextAutospaceValue.NORMAL;
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
			return TextAutospaceValue.NORMAL;
		}
		if (tokens.eat("no-autospace")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return TextAutospaceValue.NO_AUTOSPACE;
		}
		// ideograph-alpha || ideograph-numeric(順不同・重複不可)
		byte flags = 0;
		while (tokens.hasNext()) {
			if (tokens.eat("ideograph-alpha")) {
				if ((flags & TextAutospaceValue.ALPHA) != 0) {
					throw new PropertyException();
				}
				flags |= TextAutospaceValue.ALPHA;
			} else if (tokens.eat("ideograph-numeric")) {
				if ((flags & TextAutospaceValue.NUMERIC) != 0) {
					throw new PropertyException();
				}
				flags |= TextAutospaceValue.NUMERIC;
			} else {
				// auto/punctuation/insert/replace等はサブセット外(宣言無効)
				throw new PropertyException();
			}
		}
		switch (flags) {
		case TextAutospaceValue.ALPHA:
			return TextAutospaceValue.IDEOGRAPH_ALPHA;
		case TextAutospaceValue.NUMERIC:
			return TextAutospaceValue.IDEOGRAPH_NUMERIC;
		case TextAutospaceValue.ALPHA | TextAutospaceValue.NUMERIC:
			return TextAutospaceValue.IDEOGRAPH_ALPHA_NUMERIC;
		default:
			throw new PropertyException();
		}
	}
}
