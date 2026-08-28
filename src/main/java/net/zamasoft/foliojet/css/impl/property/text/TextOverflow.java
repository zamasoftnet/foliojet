package net.zamasoft.foliojet.css.impl.property.text;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * {@code text-overflow: clip | ellipsis}です(css-overflow-3 §4、
 * 2026-08-29新設)。
 *
 * <p>
 * 非継承・既定{@code clip}。{@code ellipsis}はブロックコンテナの
 * {@code overflow}が{@code visible}以外のとき、行の内容が行方向に
 * はみ出す行(典型は{@code white-space: nowrap}、または分割できない
 * 長い語)の末尾を切り詰めて省略記号"…"(U+2026、フォントに無ければ
 * "...")を置く。実装は{@code TextBuilder.applyTextOverflow}参照。
 * 2値形式({@code text-overflow: clip ellipsis})と文字列値は未対応
 * (宣言ごと無視)。
 * </p>
 */
public class TextOverflow extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new TextOverflow();

	/** {@code ellipsis}のキーワード値(KeywordValueにはないので専用)。 */
	public static final Value ELLIPSIS = new Value() {
		@Override
		public String toString() {
			return "ellipsis";
		}
	};

	public static byte get(final CSSStyle style) {
		return style.get(INFO) == ELLIPSIS ? BlockParams.TEXT_OVERFLOW_ELLIPSIS : BlockParams.TEXT_OVERFLOW_CLIP;
	}

	protected TextOverflow() {
		super("text-overflow");
	}

	public Value getDefault(final CSSStyle style) {
		return KeywordValue.CLIP;
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(final Value value, final CSSStyle style) {
		return value;
	}

	public Value parseValue(final TokenStream tokens, final UserAgent ua, final URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (lu instanceof CssToken.Ident ident && !tokens.hasNext()) {
			if (ident.is("clip")) {
				return KeywordValue.CLIP;
			}
			if (ident.is("ellipsis")) {
				return ELLIPSIS;
			}
		}
		throw new PropertyException();
	}
}
