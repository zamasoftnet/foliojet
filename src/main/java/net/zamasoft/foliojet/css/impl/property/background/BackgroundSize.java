package net.zamasoft.foliojet.css.impl.property.background;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractCompositePrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.LengthType;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * <a href=
 * "http://www.w3.org/TR/2002/WD-css3-background-20020802/#background-size">
 * backgropund-size 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 */
public class BackgroundSize extends AbstractCompositePrimitivePropertyInfo {
	public static final PrimitivePropertyInfo INFO_WIDTH = new BackgroundSize();

	public static final PrimitivePropertyInfo INFO_HEIGHT = new BackgroundSize();

	private static final PrimitivePropertyInfo[] PRIMITIVES = { INFO_WIDTH, INFO_HEIGHT };

	public static net.zamasoft.foliojet.layout.box.params.BackgroundFit getFit(CSSStyle style) {
		return getFit(style, INFO_WIDTH);
	}

	/** 対象primitiveを指定する版(mask-sizeが共有する、2026-08-29)。 */
	protected static net.zamasoft.foliojet.layout.box.params.BackgroundFit getFit(CSSStyle style,
			PrimitivePropertyInfo infoWidth) {
		Value widthValue = style.get(infoWidth);
		if (widthValue == KeywordValue.CONTAIN) {
			return net.zamasoft.foliojet.layout.box.params.BackgroundFit.CONTAIN;
		}
		if (widthValue == KeywordValue.COVER) {
			return net.zamasoft.foliojet.layout.box.params.BackgroundFit.COVER;
		}
		return net.zamasoft.foliojet.layout.box.params.BackgroundFit.NONE;
	}

	/**
	 * 画像の固有寸法を考慮したfitです(css-backgrounds-3 §background-size、
	 * 2026-08-27)。auto×autoで画像が縦横比しか持たない(viewBoxのみの
	 * SVG等)ときはcontain制約で配置領域へ収める。従来は代用値
	 * (viewBox寸法)を原寸扱いし、ロゴSVGが数百pxのまま箱からはみ出て
	 * いた(asahi.comフッターのRe:Ronロゴ)。
	 */
	public static net.zamasoft.foliojet.layout.box.params.BackgroundFit getFit(CSSStyle style, Image image) {
		return getFit(style, image, INFO_WIDTH, INFO_HEIGHT);
	}

	protected static net.zamasoft.foliojet.layout.box.params.BackgroundFit getFit(CSSStyle style, Image image,
			PrimitivePropertyInfo infoWidth, PrimitivePropertyInfo infoHeight) {
		final net.zamasoft.foliojet.layout.box.params.BackgroundFit declared = getFit(style, infoWidth);
		if (declared != net.zamasoft.foliojet.layout.box.params.BackgroundFit.NONE) {
			return declared;
		}
		if (style.get(infoWidth) == KeywordValue.AUTO && style.get(infoHeight) == KeywordValue.AUTO
				&& image.getIntrinsic() == Image.Intrinsic.RATIO) {
			return net.zamasoft.foliojet.layout.box.params.BackgroundFit.CONTAIN;
		}
		return declared;
	}

	public static Dimension get(CSSStyle style, Image image) {
		return get(style, image, INFO_WIDTH, INFO_HEIGHT);
	}

	protected static Dimension get(CSSStyle style, Image image, PrimitivePropertyInfo infoWidth,
			PrimitivePropertyInfo infoHeight) {
		Value widthValue = style.get(infoWidth);
		if (widthValue == KeywordValue.CONTAIN || widthValue == KeywordValue.COVER) {
			// 実寸はgetFit()を見た描画側が計算する
			return Dimension.AUTO_DIMENSION;
		}
		Value heightValue = style.get(infoHeight);
		LengthType widthType;
		double width;
		if (widthValue instanceof AbsoluteLengthValue length) {
			widthType = LengthType.ABSOLUTE;
			width = length.getLength();
		} else if (widthValue instanceof PercentageValue percentage) {
			widthType = LengthType.RELATIVE;
			width = percentage.getRatio();
		} else if (widthValue == KeywordValue.AUTO) {
			widthType = LengthType.AUTO;
			width = 0;
		} else {
			throw new IllegalStateException(String.valueOf(widthValue));
		}

		LengthType heightType;
		double height;
		if (heightValue instanceof AbsoluteLengthValue length) {
			heightType = LengthType.ABSOLUTE;
			height = length.getLength();
		} else if (heightValue instanceof PercentageValue percentage) {
			heightType = LengthType.RELATIVE;
			height = percentage.getRatio();
		} else if (heightValue == KeywordValue.AUTO) {
			heightType = LengthType.AUTO;
			height = 0;
		} else {
			throw new IllegalStateException(String.valueOf(heightValue));
		}

		if (widthType == LengthType.AUTO && heightType == LengthType.AUTO) {
			switch (image.getIntrinsic()) {
			case RATIO:
				// 縦横比のみ: contain制約(getFit(style, image)がCONTAINを
				// 返し、実寸は描画側が配置領域から計算する)
				return Dimension.AUTO_DIMENSION;
			case NONE:
				// 寸法情報なし: 既定サイズ規則により配置領域いっぱい
				widthType = heightType = LengthType.RELATIVE;
				width = height = 1;
				break;
			default:
				widthType = heightType = LengthType.ABSOLUTE;
				width = image.getWidth();
				height = image.getHeight();
				break;
			}
		}

		Dimension size = Dimension.create(width, height, widthType, heightType);
		return size;
	}

	protected BackgroundSize() {
		this("-cssj-background-size");
	}

	/** mask-size等、同じ文法を使う特性のための派生用(2026-08-29)。 */
	protected BackgroundSize(String name) {
		super(name);
	}

	/** ショートハンドから値列を渡すための公開入口(2026-08-29)。 */
	public Entry[] parseSizeValues(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		return this.parseValues(tokens, ua, uri);
	}

	public Value getDefault(CSSStyle style) {
		return KeywordValue.AUTO;
	}

	public boolean isInherited() {
		return false;
	}

	protected PrimitivePropertyInfo[] getPrimitives() {
		return PRIMITIVES;
	}

	/**
	 * 計算値はAbsoluteLengthValue, PercentageValue, AutoValueのいずれかです。
	 */
	public Value getComputedValue(Value value, CSSStyle style) {
		return ValueUtils.emExToAbsoluteLength(value, style);
	}

	protected Entry[] parseValues(TokenStream tokens, UserAgent ua, URI uri) throws PropertyException {
		if (tokens.isInherit()) {
			return new Entry[] { new Entry(this.getPrimitives()[0], KeywordValue.INHERIT),
					new Entry(this.getPrimitives()[1], KeywordValue.INHERIT) };
		}
		Value w, h;

		final CssToken lu = tokens.next();
		// **contain/coverキーワード形式**(2026-08-06)。単独値のみ許され、
		// 幅高さの2値構文とは併用不可(仕様通り、後続トークンがあれば無効)。
		// これまで未対応で、`ValueUtils.toLength`が失敗して例外になり
		// 既定のauto/autoへ丸ごと落ちていた——auto/autoは画像の原寸表示を
		// 意味するため、実寸より大きい画像(スプライト等)では箱の中に
		// ごく一部だけが表示される欠陥になっていた
		// (yahoo.co.jpのサイドバーアイコンで発覚)
		if (ValueUtils.isKeyword(lu, "contain")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return new Entry[] { new Entry(this.getPrimitives()[0], KeywordValue.CONTAIN),
					new Entry(this.getPrimitives()[1], KeywordValue.CONTAIN) };
		}
		if (ValueUtils.isKeyword(lu, "cover")) {
			if (tokens.hasNext()) {
				throw new PropertyException();
			}
			return new Entry[] { new Entry(this.getPrimitives()[0], KeywordValue.COVER),
					new Entry(this.getPrimitives()[1], KeywordValue.COVER) };
		}
		if (ValueUtils.isAuto(lu)) {
			w = KeywordValue.AUTO;
		} else {
			w = ValueUtils.toPercentage(lu);
			if (w == null) {
				w = ValueUtils.toLength(ua, lu);
				if (w == null || ((LengthValue) w).isNegative()) {
					throw new PropertyException();
				}
			} else if (((PercentageValue) w).isNegative()) {
				throw new PropertyException();
			}
		}

		if (!tokens.hasNext()) {
			h = KeywordValue.AUTO;
			return new Entry[] { new Entry(this.getPrimitives()[0], w), new Entry(this.getPrimitives()[1], h) };
		}

		final CssToken hToken = tokens.next();
		if (ValueUtils.isAuto(hToken)) {
			h = KeywordValue.AUTO;
		} else {
			h = ValueUtils.toPercentage(hToken);
			if (h == null) {
				h = ValueUtils.toLength(ua, hToken);
				if (h != null && ((LengthValue) h).isNegative()) {
					throw new PropertyException();
				}
			} else if (((PercentageValue) h).isNegative()) {
				throw new PropertyException();
			}
		}
		if (h == null) {
			h = KeywordValue.AUTO;
		}
		return new Entry[] { new Entry(this.getPrimitives()[0], w), new Entry(this.getPrimitives()[1], h) };
	}

}