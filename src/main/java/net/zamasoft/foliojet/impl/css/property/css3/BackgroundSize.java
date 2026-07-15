package net.zamasoft.foliojet.impl.css.property.css3;

import java.net.URI;

import net.zamasoft.foliojet.css.CSSStyle;
import net.zamasoft.foliojet.css.property.AbstractCompositePrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.CompositeProperty.Entry;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.AutoValue;
import net.zamasoft.foliojet.css.value.InheritValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.style.box.params.Dimension;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;

/**
 * <a href=
 * "http://www.w3.org/TR/2002/WD-css3-background-20020802/#background-size">
 * backgropund-size 特性 </a>です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BackgroundSize.java 1633 2023-02-12 03:22:32Z miyabe $
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
		switch (widthValue.getValueType()) {
		case Value.TYPE_ABSOLUTE_LENGTH:
			widthType = Dimension.TYPE_ABSOLUTE;
			width = ((AbsoluteLengthValue) widthValue).getLength();
			break;
		case Value.TYPE_PERCENTAGE:
			widthType = Dimension.TYPE_RELATIVE;
			width = ((PercentageValue) widthValue).getRatio();
			break;
		case Value.TYPE_AUTO:
			widthType = Dimension.TYPE_AUTO;
			width = 0;
			break;
		default:
			throw new IllegalStateException();
		}
		
		byte heightType;
		double height;
		switch (heightValue.getValueType()) {
		case Value.TYPE_ABSOLUTE_LENGTH:
			heightType = Dimension.TYPE_ABSOLUTE;
			height = ((AbsoluteLengthValue) heightValue).getLength();
			break;
		case Value.TYPE_PERCENTAGE:
			heightType = Dimension.TYPE_RELATIVE;
			height = ((PercentageValue) heightValue).getRatio();
			break;
		case Value.TYPE_AUTO:
			heightType = Dimension.TYPE_AUTO;
			height = 0;
			break;
		default:
			throw new IllegalStateException();
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
		return AutoValue.AUTO_VALUE;
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
			return new Entry[] { new Entry(BackgroundSize.INFO_WIDTH, InheritValue.INHERIT_VALUE),
					new Entry(BackgroundSize.INFO_HEIGHT, InheritValue.INHERIT_VALUE) };
		}
		Value w, h;

		final CssToken lu = tokens.next();
		if (ValueUtils.isAuto(lu)) {
			w = AutoValue.AUTO_VALUE;
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
			h = AutoValue.AUTO_VALUE;
			return new Entry[] { new Entry(BackgroundSize.INFO_WIDTH, w), new Entry(BackgroundSize.INFO_HEIGHT, h) };
		}

		final CssToken hToken = tokens.next();
		if (ValueUtils.isAuto(hToken)) {
			h = AutoValue.AUTO_VALUE;
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
			h = AutoValue.AUTO_VALUE;
		}
		return new Entry[] { new Entry(BackgroundSize.INFO_WIDTH, w), new Entry(BackgroundSize.INFO_HEIGHT, h) };
	}

}