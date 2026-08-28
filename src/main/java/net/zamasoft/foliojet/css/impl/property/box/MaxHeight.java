package net.zamasoft.foliojet.css.impl.property.box;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.text.BlockFlow;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * @author MIYABE Tatsuhiko
 */
public class MaxHeight extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new MaxHeight();

	public static Value get(CSSStyle style) {
		boolean image = CSSJInternalImage.getImage(style) != null;
		if (style.isDeclared(INFO)) {
			return style.get(INFO);
		}
		// 2026-07-20、-cssj-direction-mode廃止によりmax-inline-size/
		// max-block-sizeへ一本化。
		if (!image) {
			PrimitivePropertyInfo logicalInfo = BlockFlow.get(style).isVertical() ? MaxInlineSize.INFO : MaxBlockSize.INFO;
			if (style.isDeclared(logicalInfo)) {
				return style.get(logicalInfo);
			}
		}
		return style.get(INFO);
	}

	public static Length getLength(CSSStyle style) {
		return BoxValueUtils.toLength(MaxHeight.get(style));
	}

	private MaxHeight() {
		super("max-height");
	}

	private Value getDefault(UserAgent ua) {
		// **初期値は none**(2026-08-17)。以前は UA の
		// {@code getMaxSize()}(=14400pt。PDFの<b>用紙</b>寸法の限界)を
		// 返していたが、これは箱の高さの上限ではない。14400ptより高い
		// ブロック(長い表など)は{@code AbstractBlockBox}でこの値へ
		// 切り詰められ、改ページが永久に進まなくなる——w3c-jlreqの用語表が
		// 32回の無進捗改ページでライブロックし、変換が失敗していた。
		return KeywordValue.NONE;
	}

	public Value getDefault(CSSStyle style) {
		return this.getDefault(style.getUserAgent());
	}

	public boolean isInherited() {
		return false;
	}

	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isNone(lu)) {
			return this.getDefault(ua);
		}
		// 固有寸法キーワード max-content/min-content/fit-content(L)(2026-08-29)
		final Value intrinsic = BoxValueUtils.toIntrinsicSize(ua, lu);
		if (intrinsic != null) {
			return intrinsic;
		}
		Value value = BoxValueUtils.toPositiveLength(ua, lu);
		if (value == null) {
			throw new PropertyException();
		}
		return value;
	}

}