package jp.cssj.homare.impl.css.property.css3;

import java.net.URI;

import jp.cssj.homare.css.CSSStyle;
import jp.cssj.homare.css.property.AbstractCompositePrimitivePropertyInfo;
import jp.cssj.homare.css.property.CompositeProperty.Entry;
import jp.cssj.homare.css.property.PrimitivePropertyInfo;
import jp.cssj.homare.css.property.PropertyException;
import jp.cssj.homare.css.util.ValueUtils;
import jp.cssj.homare.css.value.AbsoluteLengthValue;
import jp.cssj.homare.css.value.AutoValue;
import jp.cssj.homare.css.value.InheritValue;
import jp.cssj.homare.css.value.LengthValue;
import jp.cssj.homare.css.value.PercentageValue;
import jp.cssj.homare.css.value.Value;
import jp.cssj.homare.style.box.params.Dimension;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

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

	protected Entry[] parseProperty(LexicalUnit lu, UserAgent ua, URI uri) throws PropertyException {
		if (lu.getLexicalUnitType() == LexicalUnit.SAC_INHERIT) {
			return new Entry[] { new Entry(BackgroundSize.INFO_WIDTH, InheritValue.INHERIT_VALUE),
					new Entry(BackgroundSize.INFO_HEIGHT, InheritValue.INHERIT_VALUE) };
		}
		Value w, h;

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

		lu = lu.getNextLexicalUnit();
		if (lu == null) {
			h = AutoValue.AUTO_VALUE;
			return new Entry[] { new Entry(BackgroundSize.INFO_WIDTH, w), new Entry(BackgroundSize.INFO_HEIGHT, h) };
		}

		if (ValueUtils.isAuto(lu)) {
			h = AutoValue.AUTO_VALUE;
		} else {
			h = ValueUtils.toPercentage(lu);
			if (h == null) {
				h = ValueUtils.toLength(ua, lu);
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