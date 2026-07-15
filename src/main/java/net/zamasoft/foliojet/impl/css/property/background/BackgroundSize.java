package net.zamasoft.foliojet.impl.css.property.background;

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
import net.zamasoft.foliojet.style.box.params.Dimension;
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

	public static Dimension get(CSSStyle style, Image image) {
		Value widthValue = style.get(INFO_WIDTH);
		Value heightValue = style.get(INFO_HEIGHT);
		byte widthType;
		double width;
		if (widthValue instanceof AbsoluteLengthValue length) {
			widthType = Dimension.TYPE_ABSOLUTE;
			width = length.getLength();
		} else if (widthValue instanceof PercentageValue percentage) {
			widthType = Dimension.TYPE_RELATIVE;
			width = percentage.getRatio();
		} else if (widthValue == KeywordValue.AUTO) {
			widthType = Dimension.TYPE_AUTO;
			width = 0;
		} else {
			throw new IllegalStateException(String.valueOf(widthValue));
		}

		byte heightType;
		double height;
		if (heightValue instanceof AbsoluteLengthValue length) {
			heightType = Dimension.TYPE_ABSOLUTE;
			height = length.getLength();
		} else if (heightValue instanceof PercentageValue percentage) {
			heightType = Dimension.TYPE_RELATIVE;
			height = percentage.getRatio();
		} else if (heightValue == KeywordValue.AUTO) {
			heightType = Dimension.TYPE_AUTO;
			height = 0;
		} else {
			throw new IllegalStateException(String.valueOf(heightValue));
		}

		if (widthType == Dimension.TYPE_AUTO && heightType == Dimension.TYPE_AUTO) {
			widthType = heightType = Dimension.TYPE_ABSOLUTE;
			width = image.getWidth();
			height = image.getHeight();
		}
		
		Dimension size = Dimension.create(width, height, widthType, heightType);
		return size;
	}

	protected BackgroundSize() {
		super("-cssj-background-size");
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
			return new Entry[] { new Entry(BackgroundSize.INFO_WIDTH, KeywordValue.INHERIT),
					new Entry(BackgroundSize.INFO_HEIGHT, KeywordValue.INHERIT) };
		}
		Value w, h;

		final CssToken lu = tokens.next();
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
			return new Entry[] { new Entry(BackgroundSize.INFO_WIDTH, w), new Entry(BackgroundSize.INFO_HEIGHT, h) };
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
		return new Entry[] { new Entry(BackgroundSize.INFO_WIDTH, w), new Entry(BackgroundSize.INFO_HEIGHT, h) };
	}

}