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
import net.zamasoft.foliojet.css.impl.property.internal.CSSJAutoWidth;
import net.zamasoft.foliojet.css.impl.property.internal.CSSJInternalImage;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * <a href="http://www.w3.org/TR/CSS21/visudet.html#propdef-width"> width 特性
 * </a>です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class Width extends AbstractPrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO = new Width();

	public static Value get(CSSStyle style) {
		boolean image = CSSJInternalImage.getImage(style) != null;
		if (style.isDeclared(INFO)) {
			return style.get(INFO);
		}
		// widthが明示指定されていなければ、標準の論理プロパティ
		// inline-size/block-sizeをフォールバックとして見る(画像には
		// 適用しない。2026-07-20、-cssj-direction-mode廃止によりinline-size/
		// block-sizeへ一本化)。
		if (!image) {
			PrimitivePropertyInfo logicalInfo = BlockFlow.get(style).isVertical() ? BlockSize.INFO : InlineSize.INFO;
			if (style.isDeclared(logicalInfo)) {
				return style.get(logicalInfo);
			}
		}
		return style.get(INFO);
	}

	public static Length getLength(CSSStyle style) {
		return BoxValueUtils.toLength(Width.get(style));
	}

	protected Width() {
		super("width");
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	/**
	 * <b>負の長さは無効値として初期値へ落とします</b>(2026-08-05)。
	 *
	 * <p>
	 * CSSでは width/height に負の値は書けず、{@link #parseValue} の
	 * {@code toPositiveLength} が弾いている。ところが {@code attr()} と
	 * {@code calc()} は<b>その要素の属性・文脈が要る</b>ので解決は計算値の
	 * 段階になり、構文解析時の検査を素通りする。
	 * </p>
	 *
	 * <p>
	 * 実害: 2026-08-03に表の表現属性をUA CSSへ移送して
	 * {@code table[width] { width: attr(width px) }} としたとき、
	 * {@code <table width="-500">} が幅16.5pt(最小内容幅)へ潰れ、
	 * セルの文字が1文字ずつ縦に折れるようになった。同じ意味を
	 * {@code style="width:-500px"} と書けば正しく無視される、という
	 * 経路依存の食い違いだった。<b>基準画像の差は0.35%で、
	 * imageTest の許容2%に隠れていた</b>(2026-08-05に目視で発見)。
	 * </p>
	 */
	public Value getComputedValue(Value value, CSSStyle style) {
		if (value == KeywordValue.AUTO) {
			value = CSSJAutoWidth.get(style);
		}
		final Value resolved = ValueUtils.emExToAbsoluteLength(value, style);
		if (resolved instanceof net.zamasoft.foliojet.css.value.QuantityValue q && q.isNegative()) {
			return ValueUtils.emExToAbsoluteLength(CSSJAutoWidth.get(style), style);
		}
		return resolved;
	}

	public Value parseValue(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		final CssToken lu = tokens.next();
		if (ValueUtils.isAuto(lu)) {
			return KeywordValue.AUTO;
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