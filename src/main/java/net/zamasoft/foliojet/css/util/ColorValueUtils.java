package net.zamasoft.foliojet.css.util;

import java.awt.SystemColor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.zamasoft.foliojet.css.value.BackgroundAttachmentValue;
import net.zamasoft.foliojet.css.value.BackgroundRepeatValue;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.PaintValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.css3.BackgroundClipValue;
import net.zamasoft.foliojet.css.value.css3.ConicGradientValue;
import net.zamasoft.foliojet.css.value.css3.GradientStops;
import net.zamasoft.foliojet.css.value.css3.LinearGradientValue;
import net.zamasoft.foliojet.css.value.css3.RadialGradientValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.QuantityValue;
import net.zamasoft.foliojet.layout.util.DoubleList;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.CMYKColor;
import net.zamasoft.pdfg2d.gc.paint.SpotColor;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Unit;

/**
 * @author MIYABE Tatsuhiko
 */
public final class ColorValueUtils {
	private ColorValueUtils() {
		// do nothing
	}

	private static final Map<String, ColorValue> COLORNAME_TO_CSS_COLOR;

	// HTML 16 colors.
	/**
	 * The 'aqua' RGB color.
	 */
	public static final ColorValue AQUA = fromRGBOctets(0, 255, 255);

	/**
	 * The 'black' RGB color.
	 */
	public static final ColorValue BLACK = fromGrayComponent(0);

	/**
	 * The 'blue' RGB color.
	 */
	public static final ColorValue BLUE = fromRGBOctets(0, 0, 255);

	/**
	 * The 'fuchsia' RGB color.
	 */
	public static final ColorValue FUCHSIA = fromRGBOctets(255, 0, 255);

	/**
	 * The 'green' RGB color.
	 */
	public static final ColorValue GREEN = fromRGBOctets(0, 128, 0);

	/**
	 * The 'gray' RGB color.
	 */
	public static final ColorValue GRAY = fromGrayComponent(0.5f);

	/**
	 * The 'lime' RGB color.
	 */
	public static final ColorValue LIME = fromRGBOctets(0, 255, 0);

	/**
	 * The 'maroon' RGB color.
	 */
	public static final ColorValue MAROON = fromRGBOctets(128, 0, 0);

	/**
	 * The 'navy' RGB color.
	 */
	public static final ColorValue NAVY = fromRGBOctets(0, 0, 128);

	/**
	 * The 'olive' RGB color.
	 */
	public static final ColorValue OLIVE = fromRGBOctets(128, 128, 0);

	/**
	 * The 'purple' RGB color.
	 */
	public static final ColorValue PURPLE = fromRGBOctets(128, 0, 128);

	/**
	 * The 'red' RGB color.
	 */
	public static final ColorValue RED = fromRGBOctets(255, 0, 0);

	/**
	 * The 'silver' RGB color.
	 */
	public static final ColorValue SILVER = fromGrayComponent(192f / 255f);

	/**
	 * The 'teal' RGB color.
	 */
	public static final ColorValue TEAL = fromRGBOctets(0, 128, 128);

	/**
	 * The 'white' RGB color.
	 */
	public static final ColorValue WHITE = fromGrayComponent(255f);

	/**
	 * The 'yellow' RGB color.
	 */
	public static final ColorValue YELLOW = fromRGBOctets(255, 255, 0);

	// Extension colors. (originates IE or NN)
	/**
	 * The 'aliceblue' RGB color.
	 */
	public static final ColorValue ALICEBLUE = fromRGBOctets(240, 248, 255);

	/**
	 * The 'antiquewhite' RGB color.
	 */
	public static final ColorValue ANTIQUEWHITE = fromRGBOctets(250, 235, 215);

	/**
	 * The 'aquamarine' RGB color.
	 */
	public static final ColorValue AQUAMARINE = fromRGBOctets(127, 255, 212);

	/**
	 * The 'azure' RGB color.
	 */
	public static final ColorValue AZURE = fromRGBOctets(240, 255, 255);

	/**
	 * The 'beige' RGB color.
	 */
	public static final ColorValue BEIGE = fromRGBOctets(245, 245, 220);

	/**
	 * The 'bisque' RGB color.
	 */
	public static final ColorValue BISQUE = fromRGBOctets(255, 228, 196);

	/**
	 * The 'blanchedalmond' RGB color.
	 */
	public static final ColorValue BLANCHEDALMOND = fromRGBOctets(255, 235, 205);

	/**
	 * The 'blueviolet' RGB color.
	 */
	public static final ColorValue BLUEVIOLET = fromRGBOctets(138, 43, 226);

	/**
	 * The 'brown' RGB color.
	 */
	public static final ColorValue BROWN = fromRGBOctets(165, 42, 42);

	/**
	 * The 'burlywood' RGB color.
	 */
	public static final ColorValue BURLYWOOD = fromRGBOctets(222, 184, 135);

	/**
	 * The 'cadetblue' RGB color.
	 */
	public static final ColorValue CADETBLUE = fromRGBOctets(95, 158, 160);

	/**
	 * The 'chartreuse' RGB color.
	 */
	public static final ColorValue CHARTREUSE = fromRGBOctets(127, 255, 0);

	/**
	 * The 'chocolate' RGB color.
	 */
	public static final ColorValue CHOCOLATE = fromRGBOctets(210, 105, 30);

	/**
	 * The 'coral' RGB color.
	 */
	public static final ColorValue CORAL = fromRGBOctets(255, 127, 80);

	/**
	 * The 'cornflowerblue' RGB color.
	 */
	public static final ColorValue CORNFLOWERBLUE = fromRGBOctets(100, 149, 237);

	/**
	 * The 'cornsilk' RGB color.
	 */
	public static final ColorValue CORNSILK = fromRGBOctets(255, 248, 220);

	/**
	 * The 'crimson' RGB color.
	 */
	public static final ColorValue CRIMSON = fromRGBOctets(220, 20, 60);

	/**
	 * The 'cyan' RGB color.
	 */
	public static final ColorValue CYAN = fromRGBOctets(0, 255, 255);

	/**
	 * The 'darkblue' RGB color.
	 */
	public static final ColorValue DARKBLUE = fromRGBOctets(0, 0, 139);

	/**
	 * The 'darkcyan' RGB color.
	 */
	public static final ColorValue DARKCYAN = fromRGBOctets(0, 139, 139);

	/**
	 * The 'darkgoldenrod' RGB color.
	 */
	public static final ColorValue DARKGOLDENROD = fromRGBOctets(184, 134, 11);

	/**
	 * The 'darkgray' RGB color.
	 */
	public static final ColorValue DARKGRAY = fromGrayComponent(169f / 255f);

	/**
	 * The 'darkgreen' RGB color.
	 */
	public static final ColorValue DARKGREEN = fromRGBOctets(0, 100, 0);

	/**
	 * The 'darkgrey' RGB color.
	 */
	public static final ColorValue DARKGREY = DARKGRAY;

	/**
	 * The 'darkkhaki' RGB color.
	 */
	public static final ColorValue DARKKHAKI = fromRGBOctets(189, 183, 107);

	/**
	 * The 'darkmagenta' RGB color.
	 */
	public static final ColorValue DARKMAGENTA = fromRGBOctets(139, 0, 139);

	/**
	 * The 'darkolivegreen' RGB color.
	 */
	public static final ColorValue DARKOLIVEGREEN = fromRGBOctets(85, 107, 47);

	/**
	 * The 'darkorange' RGB color.
	 */
	public static final ColorValue DARKORANGE = fromRGBOctets(255, 140, 0);

	/**
	 * The 'darkorchid' RGB color.
	 */
	public static final ColorValue DARKORCHID = fromRGBOctets(153, 50, 204);

	/**
	 * The 'darkred' RGB color.
	 */
	public static final ColorValue DARKRED = fromRGBOctets(139, 0, 0);

	/**
	 * The 'darksalmon' RGB color.
	 */
	public static final ColorValue DARKSALMON = fromRGBOctets(233, 150, 122);

	/**
	 * The 'darkseagreen' RGB color.
	 */
	public static final ColorValue DARKSEAGREEN = fromRGBOctets(143, 188, 143);

	/**
	 * The 'darkslateblue' RGB color.
	 */
	public static final ColorValue DARKSLATEBLUE = fromRGBOctets(72, 61, 139);

	/**
	 * The 'darkslategray' RGB color.
	 */
	public static final ColorValue DARKSLATEGRAY = fromRGBOctets(47, 79, 79);

	/**
	 * The 'darkslategrey' RGB color.
	 */
	public static final ColorValue DARKSLATEGREY = fromRGBOctets(47, 79, 79);

	/**
	 * The 'darkturquoise' RGB color.
	 */
	public static final ColorValue DARKTURQUOISE = fromRGBOctets(0, 206, 209);

	/**
	 * The 'darkviolet' RGB color.
	 */
	public static final ColorValue DARKVIOLET = fromRGBOctets(148, 0, 211);

	/**
	 * The 'deeppink' RGB color.
	 */
	public static final ColorValue DEEPPINK = fromRGBOctets(255, 20, 147);

	/**
	 * The 'deepskyblue' RGB color.
	 */
	public static final ColorValue DEEPSKYBLUE = fromRGBOctets(0, 191, 255);

	/**
	 * The 'dimgray' RGB color.
	 */
	public static final ColorValue DIMGRAY = fromGrayComponent(105f / 255f);

	/**
	 * The 'dimgrey' RGB color.
	 */
	public static final ColorValue DIMGREY = DIMGRAY;

	/**
	 * The 'dodgerblue' RGB color.
	 */
	public static final ColorValue DODGERBLUE = fromRGBOctets(30, 144, 255);

	/**
	 * The 'firebrick' RGB color.
	 */
	public static final ColorValue FIREBRICK = fromRGBOctets(178, 34, 34);

	/**
	 * The 'floralwhite' RGB color.
	 */
	public static final ColorValue FLORALWHITE = fromRGBOctets(255, 250, 240);

	/**
	 * The 'forestgreen' RGB color.
	 */
	public static final ColorValue FORESTGREEN = fromRGBOctets(34, 139, 34);

	/**
	 * The 'gainsboro' RGB color.
	 */
	public static final ColorValue GAINSBORO = fromRGBOctets(220, 200, 200);

	/**
	 * The 'ghostwhite' RGB color.
	 */
	public static final ColorValue GHOSTWHITE = fromRGBOctets(248, 248, 255);

	/**
	 * The 'gold' RGB color.
	 */
	public static final ColorValue GOLD = fromRGBOctets(255, 215, 0);

	/**
	 * The 'goldenrod' RGB color.
	 */
	public static final ColorValue GOLDENROD = fromRGBOctets(218, 165, 32);

	/**
	 * The 'grey' RGB color.
	 */
	public static final ColorValue GREY = GRAY;

	/**
	 * The 'greenyellow' RGB color.
	 */
	public static final ColorValue GREENYELLOW = fromRGBOctets(173, 255, 47);

	/**
	 * The 'honeydew' RGB color.
	 */
	public static final ColorValue HONEYDEW = fromRGBOctets(240, 255, 240);

	/**
	 * The 'hotpink' RGB color.
	 */
	public static final ColorValue HOTPINK = fromRGBOctets(255, 105, 180);

	/**
	 * The 'indianred' RGB color.
	 */
	public static final ColorValue INDIANRED = fromRGBOctets(205, 92, 92);

	/**
	 * The 'indigo' RGB color.
	 */
	public static final ColorValue INDIGO = fromRGBOctets(75, 0, 130);

	/**
	 * The 'ivory' RGB color.
	 */
	public static final ColorValue IVORY = fromRGBOctets(255, 255, 240);

	/**
	 * The 'khaki' RGB color.
	 */
	public static final ColorValue KHAKI = fromRGBOctets(240, 230, 140);

	/**
	 * The 'lavender' RGB color.
	 */
	public static final ColorValue LAVENDER = fromRGBOctets(230, 230, 250);

	/**
	 * The 'lavenderblush' RGB color.
	 */
	public static final ColorValue LAVENDERBLUSH = fromRGBOctets(255, 240, 255);

	/**
	 * The 'lawngreen' RGB color.
	 */
	public static final ColorValue LAWNGREEN = fromRGBOctets(124, 252, 0);

	/**
	 * The 'lemonchiffon' RGB color.
	 */
	public static final ColorValue LEMONCHIFFON = fromRGBOctets(255, 250, 205);

	/**
	 * The 'lightblue' RGB color.
	 */
	public static final ColorValue LIGHTBLUE = fromRGBOctets(173, 216, 230);

	/**
	 * The 'lightcoral' RGB color.
	 */
	public static final ColorValue LIGHTCORAL = fromRGBOctets(240, 128, 128);

	/**
	 * The 'lightcyan' RGB color.
	 */
	public static final ColorValue LIGHTCYAN = fromRGBOctets(224, 255, 255);

	/**
	 * The 'lightgoldenrodyellow' RGB color.
	 */
	public static final ColorValue LIGHTGOLDENRODYELLOW = fromRGBOctets(250, 250, 210);

	/**
	 * The 'lightgray' RGB color.
	 */
	public static final ColorValue LIGHTGRAY = fromGrayComponent(211f / 255f);

	/**
	 * The 'lightgreen' RGB color.
	 */
	public static final ColorValue LIGHTGREEN = fromRGBOctets(144, 238, 144);

	/**
	 * The 'lightgrey' RGB color.
	 */
	public static final ColorValue LIGHTGREY = LIGHTGRAY;

	/**
	 * The 'lightpink' RGB color.
	 */
	public static final ColorValue LIGHTPINK = fromRGBOctets(255, 182, 193);

	/**
	 * The 'lightsalmon' RGB color.
	 */
	public static final ColorValue LIGHTSALMON = fromRGBOctets(255, 160, 122);

	/**
	 * The 'lightseagreen' RGB color.
	 */
	public static final ColorValue LIGHTSEAGREEN = fromRGBOctets(32, 178, 170);

	/**
	 * The 'lightskyblue' RGB color.
	 */
	public static final ColorValue LIGHTSKYBLUE = fromRGBOctets(135, 206, 250);

	/**
	 * The 'lightslategray' RGB color.
	 */
	public static final ColorValue LIGHTSLATEGRAY = fromRGBOctets(119, 136, 153);

	/**
	 * The 'lightslategrey' RGB color.
	 */
	public static final ColorValue LIGHTSLATEGREY = fromRGBOctets(119, 136, 153);

	/**
	 * The 'lightsteelblue' RGB color.
	 */
	public static final ColorValue LIGHTSTEELBLUE = fromRGBOctets(176, 196, 222);

	/**
	 * The 'lightyellow' RGB color.
	 */
	public static final ColorValue LIGHTYELLOW = fromRGBOctets(255, 255, 224);

	/**
	 * The 'limegreen' RGB color.
	 */
	public static final ColorValue LIMEGREEN = fromRGBOctets(50, 205, 50);

	/**
	 * The 'linen' RGB color.
	 */
	public static final ColorValue LINEN = fromRGBOctets(250, 240, 230);

	/**
	 * The 'magenta' RGB color.
	 */
	public static final ColorValue MAGENTA = fromRGBOctets(255, 0, 255);

	/**
	 * The 'mediumaquamarine' RGB color.
	 */
	public static final ColorValue MEDIUMAQUAMARINE = fromRGBOctets(102, 205, 170);

	/**
	 * The 'mediumblue' RGB color.
	 */
	public static final ColorValue MEDIUMBLUE = fromRGBOctets(0, 0, 205);

	/**
	 * The 'mediumorchid' RGB color.
	 */
	public static final ColorValue MEDIUMORCHID = fromRGBOctets(186, 85, 211);

	/**
	 * The 'mediumpurple' RGB color.
	 */
	public static final ColorValue MEDIUMPURPLE = fromRGBOctets(147, 112, 219);

	/**
	 * The 'mediumseagreen' RGB color.
	 */
	public static final ColorValue MEDIUMSEAGREEN = fromRGBOctets(60, 179, 113);

	/**
	 * The 'mediumslateblue' RGB color.
	 */
	public static final ColorValue MEDIUMSLATEBLUE = fromRGBOctets(123, 104, 238);

	/**
	 * The 'mediumspringgreen' RGB color.
	 */
	public static final ColorValue MEDIUMSPRINGGREEN = fromRGBOctets(0, 250, 154);

	/**
	 * The 'mediumturquoise' RGB color.
	 */
	public static final ColorValue MEDIUMTURQUOISE = fromRGBOctets(72, 209, 204);

	/**
	 * The 'mediumvioletred' RGB color.
	 */
	public static final ColorValue MEDIUMVIOLETRED = fromRGBOctets(199, 21, 133);

	/**
	 * The 'midnightblue' RGB color.
	 */
	public static final ColorValue MIDNIGHTBLUE = fromRGBOctets(25, 25, 112);

	/**
	 * The 'mintcream' RGB color.
	 */
	public static final ColorValue MINTCREAM = fromRGBOctets(245, 255, 250);

	/**
	 * The 'mistyrose' RGB color.
	 */
	public static final ColorValue MISTYROSE = fromRGBOctets(255, 228, 225);

	/**
	 * The 'moccasin' RGB color.
	 */
	public static final ColorValue MOCCASIN = fromRGBOctets(255, 228, 181);

	/**
	 * The 'navajowhite' RGB color.
	 */
	public static final ColorValue NAVAJOWHITE = fromRGBOctets(255, 222, 173);

	/**
	 * The 'oldlace' RGB color.
	 */
	public static final ColorValue OLDLACE = fromRGBOctets(253, 245, 230);

	/**
	 * The 'olivedrab' RGB color.
	 */
	public static final ColorValue OLIVEDRAB = fromRGBOctets(107, 142, 35);

	/**
	 * The 'orange' RGB color.
	 */
	public static final ColorValue ORANGE = fromRGBOctets(255, 165, 0);

	/**
	 * The 'orangered' RGB color.
	 */
	public static final ColorValue ORANGERED = fromRGBOctets(255, 69, 0);

	/**
	 * The 'orchid' RGB color.
	 */
	public static final ColorValue ORCHID = fromRGBOctets(218, 112, 214);

	/**
	 * The 'palegoldenrod' RGB color.
	 */
	public static final ColorValue PALEGOLDENROD = fromRGBOctets(238, 232, 170);

	/**
	 * The 'palegreen' RGB color.
	 */
	public static final ColorValue PALEGREEN = fromRGBOctets(152, 251, 152);

	/**
	 * The 'paleturquoise' RGB color.
	 */
	public static final ColorValue PALETURQUOISE = fromRGBOctets(175, 238, 238);

	/**
	 * The 'palevioletred' RGB color.
	 */
	public static final ColorValue PALEVIOLETRED = fromRGBOctets(219, 112, 147);

	/**
	 * The 'papayawhip' RGB color.
	 */
	public static final ColorValue PAPAYAWHIP = fromRGBOctets(255, 239, 213);

	/**
	 * The 'peachpuff' RGB color.
	 */
	public static final ColorValue PEACHPUFF = fromRGBOctets(255, 218, 185);

	/**
	 * The 'peru' RGB color.
	 */
	public static final ColorValue PERU = fromRGBOctets(205, 133, 63);

	/**
	 * The 'pink' RGB color.
	 */
	public static final ColorValue PINK = fromRGBOctets(255, 192, 203);

	/**
	 * The 'plum' RGB color.
	 */
	public static final ColorValue PLUM = fromRGBOctets(221, 160, 221);

	/**
	 * The 'powderblue' RGB color.
	 */
	public static final ColorValue POWDERBLUE = fromRGBOctets(176, 224, 230);

	/**
	 * {@code rebeccapurple}のRGB色です。
	 */
	public static final ColorValue REBECCAPURPLE = fromRGBOctets(102, 51, 153);

	/**
	 * The 'rosybrown' RGB color.
	 */
	public static final ColorValue ROSYBROWN = fromRGBOctets(188, 143, 143);

	/**
	 * The 'royalblue' RGB color.
	 */
	public static final ColorValue ROYALBLUE = fromRGBOctets(65, 105, 225);

	/**
	 * The 'saddlebrown' RGB color.
	 */
	public static final ColorValue SADDLEBROWN = fromRGBOctets(139, 69, 19);

	/**
	 * The 'salmon' RGB color.
	 */
	public static final ColorValue SALMON = fromRGBOctets(250, 69, 114);

	/**
	 * The 'sandybrown' RGB color.
	 */
	public static final ColorValue SANDYBROWN = fromRGBOctets(244, 164, 96);

	/**
	 * The 'seagreen' RGB color.
	 */
	public static final ColorValue SEAGREEN = fromRGBOctets(46, 139, 87);

	/**
	 * The 'seashell' RGB color.
	 */
	public static final ColorValue SEASHELL = fromRGBOctets(255, 245, 238);

	/**
	 * The 'sienna' RGB color.
	 */
	public static final ColorValue SIENNA = fromRGBOctets(160, 82, 45);

	/**
	 * The 'skyblue' RGB color.
	 */
	public static final ColorValue SKYBLUE = fromRGBOctets(135, 206, 235);

	/**
	 * The 'slateblue' RGB color.
	 */
	public static final ColorValue SLATEBLUE = fromRGBOctets(106, 90, 205);

	/**
	 * The 'slategray' RGB color.
	 */
	public static final ColorValue SLATEGRAY = fromRGBOctets(112, 128, 144);

	/**
	 * The 'slategrey' RGB color.
	 */
	public static final ColorValue SLATEGREY = fromRGBOctets(112, 128, 144);

	/**
	 * The 'snow' RGB color.
	 */
	public static final ColorValue SNOW = fromRGBOctets(255, 250, 250);

	/**
	 * The 'springgreen' RGB color.
	 */
	public static final ColorValue SPRINGGREEN = fromRGBOctets(0, 255, 127);

	/**
	 * The 'steelblue' RGB color.
	 */
	public static final ColorValue STEELBLUE = fromRGBOctets(70, 130, 180);

	/**
	 * The 'tan' RGB color.
	 */
	public static final ColorValue TAN = fromRGBOctets(210, 180, 140);

	/**
	 * The 'thistle' RGB color.
	 */
	public static final ColorValue THISTLE = fromRGBOctets(216, 91, 216);

	/**
	 * The 'tomato' RGB color.
	 */
	public static final ColorValue TOMATO = fromRGBOctets(255, 99, 71);

	/**
	 * The 'turquoise' RGB color.
	 */
	public static final ColorValue TURQUOISE = fromRGBOctets(64, 224, 208);

	/**
	 * The 'violet' RGB color.
	 */
	public static final ColorValue VIOLET = fromRGBOctets(238, 130, 238);

	/**
	 * The 'wheat' RGB color.
	 */
	public static final ColorValue WHEAT = fromRGBOctets(245, 222, 179);

	/**
	 * The 'whitesmoke' RGB color.
	 */
	public static final ColorValue WHITESMOKE = fromRGBOctets(245, 245, 245);

	/**
	 * The 'yellowgreen' RGB color.
	 */
	public static final ColorValue YELLOWGREEN = fromRGBOctets(154, 205, 50);

	// System colors.
	public static final ColorValue ACTIVEBORDER = toColorValue(SystemColor.windowBorder);

	public static final ColorValue ACTIVECAPTION = toColorValue(SystemColor.activeCaption);

	public static final ColorValue APPWORKSPACE = toColorValue(SystemColor.desktop);

	public static final ColorValue BACKGROUND = toColorValue(SystemColor.desktop);

	public static final ColorValue BUTTONFACE = toColorValue(SystemColor.control);

	public static final ColorValue BUTTONHIGHLIGHT = toColorValue(SystemColor.controlLtHighlight);

	public static final ColorValue BUTTONSHADOW = toColorValue(SystemColor.controlDkShadow);

	public static final ColorValue BUTTONTEXT = toColorValue(SystemColor.controlText);

	public static final ColorValue CAPTIONTEXT = toColorValue(SystemColor.activeCaptionText);

	public static final ColorValue GRAYTEXT = toColorValue(SystemColor.textInactiveText);

	public static final ColorValue HIGHLIGHT = toColorValue(SystemColor.textHighlight);

	public static final ColorValue HIGHLIGHTTEXT = toColorValue(SystemColor.textHighlightText);

	public static final ColorValue INACTIVEBORDER = toColorValue(SystemColor.windowBorder);

	public static final ColorValue INACTIVECAPTION = toColorValue(SystemColor.inactiveCaption);

	public static final ColorValue INACTIVECAPTIONTEXT = toColorValue(SystemColor.inactiveCaptionText);

	public static final ColorValue INFOBACKGROUND = toColorValue(SystemColor.info);

	public static final ColorValue INFOTEXT = toColorValue(SystemColor.infoText);

	public static final ColorValue MENU = toColorValue(SystemColor.menu);

	public static final ColorValue MENUTEXT = toColorValue(SystemColor.menuText);

	public static final ColorValue SCROLLBAR = toColorValue(SystemColor.scrollbar);

	public static final ColorValue THREEDDARKSHADOW = toColorValue(SystemColor.controlDkShadow);

	public static final ColorValue THREEDFACE = toColorValue(SystemColor.control);

	public static final ColorValue THREEDHIGHLIGHT = toColorValue(SystemColor.controlHighlight);

	public static final ColorValue THREEDLIGHTSHADOW = toColorValue(SystemColor.controlLtHighlight);

	public static final ColorValue THREEDSHADOW = toColorValue(SystemColor.controlShadow);

	public static final ColorValue WINDOW = toColorValue(SystemColor.window);

	public static final ColorValue WINDOWFRAME = toColorValue(SystemColor.windowBorder);

	public static final ColorValue WINDOWTEXT = toColorValue(SystemColor.windowText);

	public static final ColorValue TRANSPARENT = new ColorValue(RGBAColor.create(0, 0, 0, 0));

	static {
		Map<String, ColorValue> map = new HashMap<String, ColorValue>();
		map.put("aqua", AQUA);
		map.put("black", BLACK);
		map.put("blue", BLUE);
		map.put("fuchsia", FUCHSIA);
		map.put("gray", GRAY);
		map.put("green", GREEN);
		map.put("lime", LIME);
		map.put("maroon", MAROON);
		map.put("navy", NAVY);
		map.put("olive", OLIVE);
		map.put("purple", PURPLE);
		map.put("red", RED);
		map.put("silver", SILVER);
		map.put("teal", TEAL);
		map.put("white", WHITE);
		map.put("yellow", YELLOW);

		map.put("aliceblue", ALICEBLUE);
		map.put("antiquewhite", ANTIQUEWHITE);
		map.put("aquamarine", AQUAMARINE);
		map.put("azure", AZURE);
		map.put("beige", BEIGE);
		map.put("bisque", BISQUE);
		map.put("blanchedalmond", BLANCHEDALMOND);
		map.put("blueviolet", BLUEVIOLET);
		map.put("brown", BROWN);
		map.put("burlywood", BURLYWOOD);
		map.put("cadetblue", CADETBLUE);
		map.put("chartreuse", CHARTREUSE);
		map.put("chocolate", CHOCOLATE);
		map.put("coral", CORAL);
		map.put("cornflowerblue", CORNFLOWERBLUE);
		map.put("cornsilk", CORNSILK);
		map.put("crimson", CRIMSON);
		map.put("cyan", CYAN);
		map.put("darkblue", DARKBLUE);
		map.put("darkcyan", DARKCYAN);
		map.put("darkgoldenrod", DARKGOLDENROD);
		map.put("darkgray", DARKGRAY);
		map.put("darkgreen", DARKGREEN);
		map.put("darkgrey", DARKGREY);
		map.put("darkkhaki", DARKKHAKI);
		map.put("darkmagenta", DARKMAGENTA);
		map.put("darkolivegreen", DARKOLIVEGREEN);
		map.put("darkorange", DARKORANGE);
		map.put("darkorchid", DARKORCHID);
		map.put("darkred", DARKRED);
		map.put("darksalmon", DARKSALMON);
		map.put("darkseagreen", DARKSEAGREEN);
		map.put("darkslateblue", DARKSLATEBLUE);
		map.put("darkslategray", DARKSLATEGRAY);
		map.put("darkslategrey", DARKSLATEGREY);
		map.put("darkturquoise", DARKTURQUOISE);
		map.put("darkviolet", DARKVIOLET);
		map.put("deeppink", DEEPPINK);
		map.put("deepskyblue", DEEPSKYBLUE);
		map.put("dimgray", DIMGRAY);
		map.put("dimgrey", DIMGREY);
		map.put("dodgerblue", DODGERBLUE);
		map.put("firebrick", FIREBRICK);
		map.put("floralwhite", FLORALWHITE);
		map.put("forestgreen", FORESTGREEN);
		map.put("gainsboro", GAINSBORO);
		map.put("ghostwhite", GHOSTWHITE);
		map.put("gold", GOLD);
		map.put("goldenrod", GOLDENROD);
		map.put("grey", GREY);
		map.put("greenyellow", GREENYELLOW);
		map.put("honeydew", HONEYDEW);
		map.put("hotpink", HOTPINK);
		map.put("indianred", INDIANRED);
		map.put("indigo", INDIGO);
		map.put("ivory", IVORY);
		map.put("khaki", KHAKI);
		map.put("lavender", LAVENDER);
		map.put("lavenderblush", LAVENDERBLUSH);
		map.put("lawngreen", LAWNGREEN);
		map.put("lemonchiffon", LEMONCHIFFON);
		map.put("lightblue", LIGHTBLUE);
		map.put("lightcoral", LIGHTCORAL);
		map.put("lightcyan", LIGHTCYAN);
		map.put("lightgoldenrodyellow", LIGHTGOLDENRODYELLOW);
		map.put("lightgray", LIGHTGRAY);
		map.put("lightgreen", LIGHTGREEN);
		map.put("lightgrey", LIGHTGREY);
		map.put("lightpink", LIGHTPINK);
		map.put("lightsalmon", LIGHTSALMON);
		map.put("lightseagreen", LIGHTSEAGREEN);
		map.put("lightskyblue", LIGHTSKYBLUE);
		map.put("lightslategray", LIGHTSLATEGRAY);
		map.put("lightslategrey", LIGHTSLATEGREY);
		map.put("lightsteelblue", LIGHTSTEELBLUE);
		map.put("lightyellow", LIGHTYELLOW);
		map.put("limegreen", LIMEGREEN);
		map.put("linen", LINEN);
		map.put("magenta", MAGENTA);
		map.put("mediumaquamarine", MEDIUMAQUAMARINE);
		map.put("mediumblue", MEDIUMBLUE);
		map.put("mediumorchid", MEDIUMORCHID);
		map.put("mediumpurple", MEDIUMPURPLE);
		map.put("mediumseagreen", MEDIUMSEAGREEN);
		map.put("mediumslateblue", MEDIUMSLATEBLUE);
		map.put("mediumspringgreen", MEDIUMSPRINGGREEN);
		map.put("mediumturquoise", MEDIUMTURQUOISE);
		map.put("mediumvioletred", MEDIUMVIOLETRED);
		map.put("midnightblue", MIDNIGHTBLUE);
		map.put("mintcream", MINTCREAM);
		map.put("mistyrose", MISTYROSE);
		map.put("moccasin", MOCCASIN);
		map.put("navajowhite", NAVAJOWHITE);
		map.put("oldlace", OLDLACE);
		map.put("olivedrab", OLIVEDRAB);
		map.put("orange", ORANGE);
		map.put("orangered", ORANGERED);
		map.put("orchid", ORCHID);
		map.put("palegoldenrod", PALEGOLDENROD);
		map.put("palegreen", PALEGREEN);
		map.put("paleturquoise", PALETURQUOISE);
		map.put("palevioletred", PALEVIOLETRED);
		map.put("papayawhip", PAPAYAWHIP);
		map.put("peachpuff", PEACHPUFF);
		map.put("peru", PERU);
		map.put("pink", PINK);
		map.put("plum", PLUM);
		map.put("powderblue", POWDERBLUE);
		map.put("rebeccapurple", REBECCAPURPLE);
		map.put("rosybrown", ROSYBROWN);
		map.put("royalblue", ROYALBLUE);
		map.put("saddlebrown", SADDLEBROWN);
		map.put("salmon", SALMON);
		map.put("sandybrown", SANDYBROWN);
		map.put("seagreen", SEAGREEN);
		map.put("seashell", SEASHELL);
		map.put("sienna", SIENNA);
		map.put("skyblue", SKYBLUE);
		map.put("slateblue", SLATEBLUE);
		map.put("slategray", SLATEGRAY);
		map.put("slategrey", SLATEGREY);
		map.put("snow", SNOW);
		map.put("springgreen", SPRINGGREEN);
		map.put("steelblue", STEELBLUE);
		map.put("tan", TAN);
		map.put("thistle", THISTLE);
		map.put("tomato", TOMATO);
		map.put("turquoise", TURQUOISE);
		map.put("violet", VIOLET);
		map.put("wheat", WHEAT);
		map.put("whitesmoke", WHITESMOKE);
		map.put("yellowgreen", YELLOWGREEN);

		map.put("activeborder", ACTIVEBORDER);
		map.put("activecaption", ACTIVECAPTION);
		map.put("appworkspace", APPWORKSPACE);
		map.put("background", BACKGROUND);
		map.put("buttonface", BUTTONFACE);
		map.put("buttonheighlight", BUTTONHIGHLIGHT);
		map.put("buttonshadow", BUTTONSHADOW);
		map.put("buttontext", BUTTONTEXT);
		map.put("captiontext", CAPTIONTEXT);
		map.put("graytext", GRAYTEXT);
		map.put("highlight", HIGHLIGHT);
		map.put("highlighttext", HIGHLIGHTTEXT);
		map.put("inactiveborder", INACTIVEBORDER);
		map.put("inactivecaption", INACTIVECAPTION);
		map.put("inactivecaptiontext", INACTIVECAPTIONTEXT);
		map.put("infobackground", INFOBACKGROUND);
		map.put("infotext", INFOTEXT);
		map.put("menu", MENU);
		map.put("menutext", MENUTEXT);
		map.put("scrollbar", SCROLLBAR);
		map.put("threeddarkshadow", THREEDDARKSHADOW);
		map.put("threedface", THREEDFACE);
		map.put("threedhighlight", THREEDHIGHLIGHT);
		map.put("threedlightshadow", THREEDLIGHTSHADOW);
		map.put("threedshadow", THREEDSHADOW);
		map.put("window", WINDOW);
		map.put("windowframe", WINDOWFRAME);
		map.put("windowtext", WINDOWTEXT);

		map.put("transparent", TRANSPARENT);

		COLORNAME_TO_CSS_COLOR = Collections.unmodifiableMap(map);
	}

	/* a */private static ColorValue toColorValue(java.awt.Color color) {
		/* a */return fromRGBOctets(color.getRed(), color.getGreen(), color/* a */.getBlue());
		/* a */}

	public static ColorValue toColorValue(String colorName) {
		return (ColorValue) COLORNAME_TO_CSS_COLOR.get(colorName.toLowerCase());
	}

	private static ColorValue fromRGBOctets(int red, int green, int blue) {
		return new ColorValue(RGBColor.create((float) red / 255f, (float) green / 255f, (float) blue / 255f));
	}

	private static ColorValue fromRGBComponents(float red, float green, float blue) {
		if (red == 0 && green == 0 && blue == 0) {
			return BLACK;
		}
		if (red == 1f && green == 1f && blue == 1f) {
			return WHITE;
		}
		return new ColorValue(RGBColor.create(red, green, blue));
	}

	private static ColorValue fromRGBAComponents(float red, float green, float blue, float alpha) {
		return new ColorValue(RGBAColor.create(red, green, blue, alpha));
	}

	private static ColorValue fromCMYKComponents(float cyan, float magenta, float yellow, float black, byte overprint) {
		return new ColorValue(CMYKColor.create(cyan, magenta, yellow, black, overprint));
	}

	private static ColorValue fromGrayComponent(float g) {
		return new ColorValue(GrayColor.create(g));
	}

	public static ColorValue parseRGBHexColor(String color) {
		int r, g, b;
		try {
			if (color.length() == 8) {
				// #RRGGBBAA(CSS Color 4、2026-08-29)
				r = Integer.parseInt(color.substring(0, 2), 16);
				g = Integer.parseInt(color.substring(2, 4), 16);
				b = Integer.parseInt(color.substring(4, 6), 16);
				final int a = Integer.parseInt(color.substring(6, 8), 16);
				return fromRGBAComponents(r / 255f, g / 255f, b / 255f, a / 255f);
			} else if (color.length() == 4) {
				r = Integer.parseInt(color.substring(0, 1), 16) * 17;
				g = Integer.parseInt(color.substring(1, 2), 16) * 17;
				b = Integer.parseInt(color.substring(2, 3), 16) * 17;
				final int a = Integer.parseInt(color.substring(3, 4), 16) * 17;
				return fromRGBAComponents(r / 255f, g / 255f, b / 255f, a / 255f);
			} else if (color.length() >= 6) {
				r = Integer.parseInt(color.substring(0, 2), 16);
				g = Integer.parseInt(color.substring(2, 4), 16);
				b = Integer.parseInt(color.substring(4, 6), 16);
			} else if (color.length() >= 3) {
				r = Integer.parseInt(color.substring(0, 1), 16);
				g = Integer.parseInt(color.substring(1, 2), 16);
				b = Integer.parseInt(color.substring(2, 3), 16);
			} else if (color.equals("0")) {
				return BLACK;
			} else {
				return null;
			}
		} catch (NumberFormatException e) {
			return null;
		}
		return ColorValueUtils.fromRGBOctets(r, g, b);
	}

	/**
	 * rgb の引数をRGBColorValueに変換します。
	 */
	private static ColorValue toRGBColorValue(TokenStream args) {
		try {
			float red = toColorComponent(nextComponent(args));
			float green = toColorComponent(nextComponent(args));
			float blue = toColorComponent(nextComponent(args));
			// CSS Color 4の空白区切り構文 rgb(0 0 0 / 50%)(2026-08-29)。
			// 従来は第4成分を読み捨てていたため不透明になっていた
			if (args.eatSlash()) {
				return fromMaybeAlpha(red, green, blue, toUnitNumber(nextComponent(args)));
			}
			return fromRGBComponents(red, green, blue);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * rgba の引数をRGBAColorValueに変換します。
	 */
	private static ColorValue toRGBAColorValue(TokenStream args) {
		try {
			float red = toColorComponent(nextComponent(args));
			float green = toColorComponent(nextComponent(args));
			float blue = toColorComponent(nextComponent(args));
			// rgba(0 0 0 / .5) の別名構文も受ける(2026-08-29)
			args.eatSlash();
			float alpha = toUnitNumber(nextComponent(args));
			return fromRGBAComponents(red, green, blue, alpha);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * -cssj-cmyk の引数をCMYKColorValueに変換します。
	 */
	private static ColorValue toCMYKColorValue(TokenStream args) {
		try {
			float cyan = toColorComponent(nextComponent(args));
			float magenta = toColorComponent(nextComponent(args));
			float yellow = toColorComponent(nextComponent(args));
			float black = toColorComponent(nextComponent(args));

			byte overprint = CMYKColor.OVERPRINT_NONE;
			CssToken token = nextComponent(args);
			if (token instanceof CssToken.Ident ident) {
				if (ident.is("standard")) {
					overprint = CMYKColor.OVERPRINT_STANDARD;
				} else if (ident.is("illustrator")) {
					overprint = CMYKColor.OVERPRINT_ILLUSTRATOR;
				}
			}

			return fromCMYKComponents(cyan, magenta, yellow, black, overprint);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * -cssj-spot(版名, 代替色 [, 網点率%] [, standard|illustrator]) を
	 * スポットカラーに変換します。-cssj-spot(registration) は
	 * レジストレーションカラー("All" 版)です。
	 */
	private static ColorValue toSpotColorValue(UserAgent ua, TokenStream args) {
		try {
			CssToken first = nextComponent(args);
			// レジストレーションカラー
			if (first instanceof CssToken.Ident ident && ident.is("registration")) {
				return new ColorValue(SpotColor.REGISTRATION);
			}
			if (!(first instanceof CssToken.Str str)) {
				return null;
			}
			String name = str.value();

			CssToken alternate = nextComponent(args);
			ColorValue alternateValue = alternate == null ? null : toColor(ua, alternate);
			if (alternateValue == null) {
				return null;
			}
			Color alternateColor = alternateValue.getColor();

			float tint = 1;
			byte overprint = CMYKColor.OVERPRINT_NONE;
			for (CssToken token = nextComponent(args); token != null; token = nextComponent(args)) {
				if (token instanceof CssToken.Percent percent) {
					tint = (float) (percent.value() / 100.0);
				} else if (token instanceof CssToken.Num) {
					tint = toColorComponent(token);
				} else if (token instanceof CssToken.Ident ident) {
					if (ident.is("standard")) {
						overprint = CMYKColor.OVERPRINT_STANDARD;
					} else if (ident.is("illustrator")) {
						overprint = CMYKColor.OVERPRINT_ILLUSTRATOR;
					}
				}
			}
			return new ColorValue(new SpotColor(name, alternateColor, tint, overprint));
		} catch (RuntimeException e) {
			return null;
		}
	}

	/**
	 * @param legacy 接頭辞つき旧構文か(2026-08-29)。旧構文では向きの
	 *               キーワードが{@code to}無しで<b>開始辺</b>を表し
	 *               ({@code top}=現行の{@code to bottom})、角度は東を0とする
	 *               反時計回り(現行=90deg−旧)
	 */
	private static PaintValue toLinearGradient(UserAgent ua, TokenStream args, boolean legacy) {
		return toLinearGradient(ua, args, legacy, false);
	}

	private static PaintValue toLinearGradient(UserAgent ua, TokenStream args, boolean legacy, boolean repeating) {
		try {
			double angle = 180 * Math.PI * 2 / 360;
			boolean prelude = false;
			boolean direction = false;
			boolean interpolation = false;
			while (true) {
				if (!interpolation && consumeGradientInterpolation(args)) {
					interpolation = true;
					prelude = true;
					continue;
				}
				// 方向指定(<angle> | to <side> [<side>])。補間指定とは順不同。
				final CssToken first = args.peek();
				final Double radians = direction ? null : toAngleRadians(first);
				if (radians != null) {
					args.next();
					angle = legacy ? Math.PI / 2 - radians : radians;
					direction = true;
					prelude = true;
					continue;
				}
				if (!direction && (args.eat("to") || (legacy && first instanceof CssToken.Ident side
						&& isGradientSide(side.name())))) {
					final boolean startSide = first instanceof CssToken.Ident firstIdent && !firstIdent.is("to");
					String a = args.ident();
					if (a == null || !isGradientSide(a)) {
						throw new IllegalArgumentException();
					}
					String b = null;
					int mark = args.position();
					String cand = args.ident();
					if (cand != null) {
						if (isGradientSide(cand)) {
							b = cand;
						} else {
							args.rewind(mark);
						}
					}
					angle = gradientAngle(a, b);
					if (startSide) {
						// 旧構文の「開始辺」→現行の「終了辺」は正反対
						angle += Math.PI;
					}
					direction = true;
					prelude = true;
					continue;
				}
				break;
			}
			if (prelude && !args.eatComma()) {
				throw new IllegalArgumentException();
			}

			return new LinearGradientValue(angle, parseStops(ua, args.splitComma(), false), repeating);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static boolean isGradientSide(String ident) {
		switch (ident.toLowerCase()) {
		case "top":
		case "bottom":
		case "left":
		case "right":
			return true;
		default:
			return false;
		}
	}

	/**
	 * linear-gradient の to 方向を角度(ラジアン)に変換します。
	 */
	private static double gradientAngle(String a, String b) {
		final int deg;
		switch (a.toLowerCase()) {
		case "top":
			deg = b == null ? 0 : (b.equalsIgnoreCase("left") ? 315 : b.equalsIgnoreCase("right") ? 45 : 0);
			break;
		case "bottom":
			deg = b == null ? 180 : (b.equalsIgnoreCase("left") ? 225 : b.equalsIgnoreCase("right") ? 135 : 180);
			break;
		case "left":
			deg = b == null ? 270 : (b.equalsIgnoreCase("top") ? 315 : b.equalsIgnoreCase("bottom") ? 225 : 270);
			break;
		case "right":
			deg = b == null ? 90 : (b.equalsIgnoreCase("top") ? 45 : b.equalsIgnoreCase("bottom") ? 135 : 90);
			break;
		default:
			deg = 180;
			break;
		}
		return deg * Math.PI * 2 / 360;
	}

	private static ColorValue toGrayColorValue(TokenStream args) {
		try {
			return fromGrayComponent(toColorComponent(nextComponent(args)));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** 引数列からコンマを読み飛ばして次のトークンを返します。 */
	private static CssToken nextComponent(TokenStream args) {
		while (args.eatComma()) {
			// skip
		}
		return args.next();
	}

	private static float toColorComponent(CssToken token) throws IllegalArgumentException {
		if (token instanceof CssToken.Percent percent) {
			return (float) (percent.value() / 100.0);
		}
		if (token instanceof CssToken.Num num) {
			// 整数表記は0〜255、実数表記は0〜1として扱う(旧実装と同じ)
			return num.integer() ? (float) (num.value() / 255.0) : (float) num.value();
		}
		throw new IllegalArgumentException();
	}

	/** 色相トークン(数値または角度)を度で返します。 */
	private static double toHueDegrees(final CssToken token) throws IllegalArgumentException {
		if (token instanceof CssToken.Num num) {
			return num.value();
		}
		if (token instanceof CssToken.Dim dim) {
			return switch (dim.unit()) {
			case DEG -> dim.value();
			case GRAD -> dim.value() * 0.9;
			case RAD -> Math.toDegrees(dim.value());
			default -> {
				if (dim.unitText().equalsIgnoreCase("turn")) {
					yield dim.value() * 360;
				}
				throw new IllegalArgumentException();
			}
			};
		}
		throw new IllegalArgumentException();
	}

	/**
	 * 0..1の単位数値です(%は/100、数値はそのまま——Color 4系関数用。
	 * 旧toColorComponentの「整数は/255」ヒューリスティックはrgbレガシー
	 * 専用のためここでは使わない)。
	 */
	private static float toUnitNumber(final CssToken token) throws IllegalArgumentException {
		if (token instanceof CssToken.Percent percent) {
			return (float) (percent.value() / 100.0);
		}
		if (token instanceof CssToken.Num num) {
			return (float) num.value();
		}
		throw new IllegalArgumentException();
	}

	/** 省略可能な「/ アルファ」を読みます(なければ1)。 */
	private static float toOptionalAlpha(final TokenStream args) throws IllegalArgumentException {
		if (args.eatSlash()) {
			return toUnitNumber(nextComponent(args));
		}
		return 1;
	}

	/**
	 * hsl/hslaです(CSS Color 3——Web由来CSSの入力互換。旧カンマ構文と
	 * 現代のスペース+スラッシュ構文の両方を受ける)。
	 */
	private static ColorValue toHSLColorValue(final TokenStream args) {
		try {
			final double h = ((toHueDegrees(nextComponent(args)) % 360) + 360) % 360;
			final float sat = toUnitNumber(nextComponent(args));
			final float light = toUnitNumber(nextComponent(args));
			float alpha = 1;
			if (args.eatSlash()) {
				alpha = toUnitNumber(nextComponent(args));
			} else if (args.hasNext()) {
				alpha = toUnitNumber(nextComponent(args));
			}
			final double[] rgb = hslToSRGB(h, sat, light);
			return fromMaybeAlpha((float) rgb[0], (float) rgb[1], (float) rgb[2], alpha);
		} catch (final IllegalArgumentException e) {
			return null;
		}
	}

	/** HSLをsRGB成分へ変換します。hueは0以上360未満の度数です。 */
	private static double[] hslToSRGB(final double hue, final double saturation, final double lightness) {
		final double c = (1 - Math.abs(2 * lightness - 1)) * saturation;
		final double x = c * (1 - Math.abs((hue / 60) % 2 - 1));
		final double m = lightness - c / 2;
		final double[] rgb = switch ((int) (hue / 60)) {
		case 0 -> new double[] { c, x, 0 };
		case 1 -> new double[] { x, c, 0 };
		case 2 -> new double[] { 0, c, x };
		case 3 -> new double[] { 0, x, c };
		case 4 -> new double[] { x, 0, c };
		default -> new double[] { c, 0, x };
		};
		return new double[] { rgb[0] + m, rgb[1] + m, rgb[2] + m };
	}

	/**
	 * hwbです(CSS Color 4)。HSLの純色をwhiteness/blacknessでsRGBへ写します。
	 */
	private static ColorValue toHWBColorValue(final TokenStream args) {
		try {
			final double hue = ((toHueDegrees(nextComponent(args)) % 360) + 360) % 360;
			final CssToken whitenessToken = nextComponent(args);
			final CssToken blacknessToken = nextComponent(args);
			if (!(whitenessToken instanceof CssToken.Percent whitenessPercent)
					|| !(blacknessToken instanceof CssToken.Percent blacknessPercent)) {
				return null;
			}
			final double whiteness = Math.min(1, Math.max(0, whitenessPercent.value() / 100.0));
			final double blackness = Math.min(1, Math.max(0, blacknessPercent.value() / 100.0));
			final float alpha = toOptionalAlpha(args);
			if (whiteness + blackness >= 1) {
				final float gray = (float) (whiteness / (whiteness + blackness));
				return fromMaybeAlpha(gray, gray, gray, alpha);
			}
			final double[] rgb = hslToSRGB(hue, 1, 0.5);
			final double factor = 1 - whiteness - blackness;
			return fromMaybeAlpha((float) (rgb[0] * factor + whiteness),
					(float) (rgb[1] * factor + whiteness), (float) (rgb[2] * factor + whiteness), alpha);
		} catch (final IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * oklch/oklabです(CSS Color 4)。L=数値0..1または%、C=数値または
	 * %(基準0.4)、H=角度。sRGBへ変換して保持する(印刷パイプラインは
	 * RGB/CMYK——広色域はsRGBへクリップ)。
	 */
	private static ColorValue toOKColorValue(final TokenStream args, final boolean lch) {
		try {
			final float lightness = toUnitNumber(nextComponent(args));
			final double a1;
			final double b1;
			if (lch) {
				final CssToken chromaToken = nextComponent(args);
				final double chroma;
				if (chromaToken instanceof CssToken.Percent percent) {
					chroma = percent.value() / 100.0 * 0.4;
				} else if (chromaToken instanceof CssToken.Num num) {
					chroma = num.value();
				} else {
					return null;
				}
				final double hue = Math.toRadians(toHueDegrees(nextComponent(args)));
				a1 = chroma * Math.cos(hue);
				b1 = chroma * Math.sin(hue);
			} else {
				a1 = toOKLabAxis(nextComponent(args));
				b1 = toOKLabAxis(nextComponent(args));
			}
			final float alpha = toOptionalAlpha(args);
			final double[] rgb = oklabToSRGB(lightness, a1, b1);
			return fromMaybeAlpha((float) rgb[0], (float) rgb[1], (float) rgb[2], alpha);
		} catch (final IllegalArgumentException e) {
			return null;
		}
	}

	/** oklabのa/b軸(数値または%——基準±0.4)。 */
	private static double toOKLabAxis(final CssToken token) throws IllegalArgumentException {
		if (token instanceof CssToken.Percent percent) {
			return percent.value() / 100.0 * 0.4;
		}
		if (token instanceof CssToken.Num num) {
			return num.value();
		}
		throw new IllegalArgumentException();
	}

	/**
	 * lab/lchです(CSS Color 4)。D50のCIE LabをBradford変換でD65へ順応し、
	 * sRGBへ変換して保持します。Labのa/bは100%=125、LCHのCは100%=150です。
	 */
	private static ColorValue toLabColorValue(final TokenStream args, final boolean lch) {
		try {
			final double lightness = toLabLightness(nextComponent(args));
			final double a;
			final double b;
			if (lch) {
				final double chroma = Math.max(0, toLabComponent(nextComponent(args), 150));
				final double hue = Math.toRadians(toHueDegrees(nextComponent(args)));
				a = chroma * Math.cos(hue);
				b = chroma * Math.sin(hue);
			} else {
				a = toLabComponent(nextComponent(args), 125);
				b = toLabComponent(nextComponent(args), 125);
			}
			final float alpha = toOptionalAlpha(args);
			final double[] rgb = labToSRGB(lightness, a, b);
			return fromMaybeAlpha((float) rgb[0], (float) rgb[1], (float) rgb[2], alpha);
		} catch (final IllegalArgumentException e) {
			return null;
		}
	}

	/** lab/lchのL軸(数値は0..100、100%=100)。範囲外は仕様どおりクリップ。 */
	private static double toLabLightness(final CssToken token) throws IllegalArgumentException {
		final double lightness;
		if (token instanceof CssToken.Percent percent) {
			lightness = percent.value();
		} else if (token instanceof CssToken.Num num) {
			lightness = num.value();
		} else {
			throw new IllegalArgumentException();
		}
		return Math.min(100, Math.max(0, lightness));
	}

	/** Lab/LCH成分。percentageScaleは100%に対応する値です。 */
	private static double toLabComponent(final CssToken token, final double percentageScale)
			throws IllegalArgumentException {
		if (token instanceof CssToken.Percent percent) {
			return percent.value() / 100.0 * percentageScale;
		}
		if (token instanceof CssToken.Num num) {
			return num.value();
		}
		throw new IllegalArgumentException();
	}

	/** CIE Lab(D50)→XYZ(D50)→XYZ(D65)→sRGB(ガンマ符号化+0..1クリップ)。 */
	private static double[] labToSRGB(final double lightness, final double a, final double b) {
		final double epsilon = 216.0 / 24389.0;
		final double kappa = 24389.0 / 27.0;
		final double fy = (lightness + 16) / 116;
		final double fx = fy + a / 500;
		final double fz = fy - b / 200;
		final double x50 = labToXYZComponent(fx, epsilon, kappa) * (0.3457 / 0.3585);
		final double y50 = (lightness > kappa * epsilon ? fy * fy * fy : lightness / kappa);
		final double z50 = labToXYZComponent(fz, epsilon, kappa)
				* ((1 - 0.3457 - 0.3585) / 0.3585);
		final double[] xyz65 = d50ToD65(x50, y50, z50);
		return xyzD65ToSRGB(xyz65[0], xyz65[1], xyz65[2]);
	}

	private static double labToXYZComponent(final double value, final double epsilon, final double kappa) {
		final double cube = value * value * value;
		return cube > epsilon ? cube : (116 * value - 16) / kappa;
	}

	/**
	 * CSS Color 4の{@code color()}です。定義済みRGB色空間とXYZ(D50/D65)を
	 * sRGBへ変換して保持します。出力色域外の成分は単純にsRGBの0..1へ
	 * クランプします。
	 */
	private static ColorValue toColorFunction(final TokenStream args) {
		try {
			final String colorSpace = args.ident();
			if (colorSpace == null) {
				return null;
			}
			final double c1 = toColorFunctionComponent(args.next());
			final double c2 = toColorFunctionComponent(args.next());
			final double c3 = toColorFunctionComponent(args.next());
			final float alpha = toOptionalAlpha(args);
			if (args.hasNext()) {
				return null;
			}

			final double[] rgb;
			switch (colorSpace.toLowerCase(java.util.Locale.ROOT)) {
			case "srgb":
				rgb = new double[] { c1, c2, c3 };
				break;
			case "srgb-linear":
				rgb = linearSRGBToSRGB(c1, c2, c3);
				break;
			case "display-p3": {
				final double r = gammaDecodeExtended(c1);
				final double g = gammaDecodeExtended(c2);
				final double b = gammaDecodeExtended(c3);
				rgb = xyzD65ToSRGB(0.4865709486482162 * r + 0.26566769316909306 * g
						+ 0.1982172852343625 * b,
						0.2289745640697488 * r + 0.6917385218365064 * g + 0.079286914093745 * b,
						0.04511338185890264 * g + 1.043944368900976 * b);
				break;
			}
			case "a98-rgb": {
				final double r = a98Decode(c1);
				final double g = a98Decode(c2);
				final double b = a98Decode(c3);
				rgb = xyzD65ToSRGB(0.5766690429101305 * r + 0.1855582379065463 * g
						+ 0.1882286462349947 * b,
						0.29734497525053605 * r + 0.6273635662554661 * g + 0.07529145849399788 * b,
						0.02703136138641234 * r + 0.07068885253582723 * g + 0.9913375368376388 * b);
				break;
			}
			case "prophoto-rgb": {
				final double r = proPhotoDecode(c1);
				final double g = proPhotoDecode(c2);
				final double b = proPhotoDecode(c3);
				final double[] xyz65 = d50ToD65(0.7977666449006423 * r + 0.13518129740053308 * g
						+ 0.0313477341283922 * b,
						0.2880748288194013 * r + 0.711835234241873 * g + 0.00008993693872564 * b,
						0.8251046025104602 * b);
				rgb = xyzD65ToSRGB(xyz65[0], xyz65[1], xyz65[2]);
				break;
			}
			case "rec2020": {
				final double r = rec2020Decode(c1);
				final double g = rec2020Decode(c2);
				final double b = rec2020Decode(c3);
				rgb = xyzD65ToSRGB(63426534.0 / 99577255.0 * r + 20160776.0 / 139408157.0 * g
						+ 47086771.0 / 278816314.0 * b,
						26158966.0 / 99577255.0 * r + 472592308.0 / 697040785.0 * g
								+ 8267143.0 / 139408157.0 * b,
						19567812.0 / 697040785.0 * g + 295819943.0 / 278816314.0 * b);
				break;
			}
			case "xyz":
			case "xyz-d65":
				rgb = xyzD65ToSRGB(c1, c2, c3);
				break;
			case "xyz-d50": {
				final double[] xyz65 = d50ToD65(c1, c2, c3);
				rgb = xyzD65ToSRGB(xyz65[0], xyz65[1], xyz65[2]);
				break;
			}
			default:
				return null;
			}
			return fromMaybeAlpha((float) rgb[0], (float) rgb[1], (float) rgb[2], alpha);
		} catch (final IllegalArgumentException e) {
			return null;
		}
	}

	/** {@code color()}の色成分です。数値はそのまま、%は0..1へ正規化します。 */
	private static double toColorFunctionComponent(final CssToken token) throws IllegalArgumentException {
		if (token instanceof CssToken.Percent percent) {
			return percent.value() / 100.0;
		}
		if (token instanceof CssToken.Num num) {
			return num.value();
		}
		throw new IllegalArgumentException();
	}

	private static double[] linearSRGBToSRGB(final double red, final double green, final double blue) {
		return new double[] { gammaEncode(red), gammaEncode(green), gammaEncode(blue) };
	}

	private static double a98Decode(final double encoded) {
		return Math.copySign(Math.pow(Math.abs(encoded), 563.0 / 256.0), encoded);
	}

	private static double proPhotoDecode(final double encoded) {
		final double absolute = Math.abs(encoded);
		return absolute <= 16.0 / 512.0 ? encoded / 16.0
				: Math.copySign(Math.pow(absolute, 1.8), encoded);
	}

	private static double rec2020Decode(final double encoded) {
		final double alpha = 1.09929682680944;
		final double beta = 0.018053968510807;
		final double absolute = Math.abs(encoded);
		return absolute < beta * 4.5 ? encoded / 4.5
				: Math.copySign(Math.pow((absolute + alpha - 1) / alpha, 1 / 0.45), encoded);
	}

	/** Bradford色順応(D50→D65)。 */
	private static double[] d50ToD65(final double x50, final double y50, final double z50) {
		return new double[] { 0.955473421488075 * x50 - 0.02309845494876471 * y50
				+ 0.06325924320057072 * z50,
				-0.0283697093338637 * x50 + 1.0099953980813041 * y50 + 0.021041441191917323 * z50,
				0.012314014864481998 * x50 - 0.020507649298898964 * y50 + 1.330365926242124 * z50 };
	}

	/** XYZ(D65)→sRGB。色域外は各成分を0..1へクランプします。 */
	private static double[] xyzD65ToSRGB(final double x65, final double y65, final double z65) {
		return linearSRGBToSRGB(3.2409699419045226 * x65 - 1.537383177570094 * y65
				- 0.4986107602930034 * z65,
				-0.9692436362808796 * x65 + 1.8759675015077202 * y65 + 0.04155505740717559 * z65,
				0.05563007969699366 * x65 - 0.20397695888897652 * y65 + 1.0569715142428786 * z65);
	}

	/** OKLab→sRGB(標準行列。ガンマ符号化+0..1クリップ)。 */
	private static double[] oklabToSRGB(final double lightness, final double a, final double b) {
		final double l_ = lightness + 0.3963377774 * a + 0.2158037573 * b;
		final double m_ = lightness - 0.1055613458 * a - 0.0638541728 * b;
		final double s_ = lightness - 0.0894841775 * a - 1.2914855480 * b;
		final double l = l_ * l_ * l_;
		final double m = m_ * m_ * m_;
		final double sv = s_ * s_ * s_;
		final double lr = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * sv;
		final double lg = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * sv;
		final double lb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * sv;
		return new double[] { gammaEncode(lr), gammaEncode(lg), gammaEncode(lb) };
	}

	/** sRGB→OKLab(color-mixの補間空間用)。 */
	private static double[] srgbToOKLab(final double red, final double green, final double blue) {
		final double lr = gammaDecode(red);
		final double lg = gammaDecode(green);
		final double lb = gammaDecode(blue);
		final double l = Math.cbrt(0.4122214708 * lr + 0.5363325363 * lg + 0.0514459929 * lb);
		final double m = Math.cbrt(0.2119034982 * lr + 0.6806995451 * lg + 0.1073969566 * lb);
		final double sv = Math.cbrt(0.0883024619 * lr + 0.2817188376 * lg + 0.6299787005 * lb);
		return new double[] { 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * sv,
				1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * sv,
				0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * sv };
	}

	private static double gammaEncode(final double linear) {
		final double clipped = Math.min(1, Math.max(0, linear));
		return clipped <= 0.0031308 ? clipped * 12.92 : 1.055 * Math.pow(clipped, 1 / 2.4) - 0.055;
	}

	private static double gammaDecode(final double encoded) {
		return encoded <= 0.04045 ? encoded / 12.92 : Math.pow((encoded + 0.055) / 1.055, 2.4);
	}

	/** sRGBの拡張伝達関数です({@code color()}の範囲外成分を変換途中まで保持)。 */
	private static double gammaDecodeExtended(final double encoded) {
		final double absolute = Math.abs(encoded);
		return absolute <= 0.04045 ? encoded / 12.92
				: Math.copySign(Math.pow((absolute + 0.055) / 1.055, 2.4), encoded);
	}

	private static ColorValue fromMaybeAlpha(final float red, final float green, final float blue,
			final float alpha) {
		final float r = Math.min(1, Math.max(0, red));
		final float g = Math.min(1, Math.max(0, green));
		final float b = Math.min(1, Math.max(0, blue));
		return alpha >= 1 ? fromRGBComponents(r, g, b) : fromRGBAComponents(r, g, b, Math.max(0, alpha));
	}

	/**
	 * color-mixです(CSS Color 5のうちin srgb/oklab/oklch——Tailwind v4の
	 * 透明度ユーティリティが多用する。補間は仕様どおり
	 * アルファpremultiplied)。
	 */
	private static ColorValue toColorMix(final UserAgent ua, final TokenStream args) {
		try {
			if (!args.eat("in")) {
				return null;
			}
			final String space = args.ident();
			if (space == null) {
				return null;
			}
			final ColorValue color1 = toMixArgColor(ua, nextComponent(args));
			Float p1 = eatPercent(args);
			final ColorValue color2 = toMixArgColor(ua, nextComponent(args));
			Float p2 = eatPercent(args);
			if (color1 == null || color2 == null) {
				return null;
			}
			if (p1 == null && p2 == null) {
				p1 = 0.5f;
				p2 = 0.5f;
			} else if (p1 == null) {
				p1 = 1 - p2;
			} else if (p2 == null) {
				p2 = 1 - p1;
			}
			final float sum = p1 + p2;
			if (sum <= 0) {
				return null;
			}
			final float w1 = p1 / sum;
			final float w2 = p2 / sum;
			final float a1 = color1.getAlpha();
			final float a2 = color2.getAlpha();
			final float alpha = a1 * w1 + a2 * w2;
			if (alpha <= 0) {
				return fromRGBAComponents(0, 0, 0, 0);
			}
			final double[] c1;
			final double[] c2;
			switch (space.toLowerCase()) {
			case "srgb":
				c1 = new double[] { color1.getRed(), color1.getGreen(), color1.getBlue() };
				c2 = new double[] { color2.getRed(), color2.getGreen(), color2.getBlue() };
				break;
			case "oklab":
			case "oklch":
				// oklchの色相最短弧は実装簡略化のためoklab直線補間で代替
				// (無彩色・近色相では同一。記録済みの近似)
				c1 = srgbToOKLab(color1.getRed(), color1.getGreen(), color1.getBlue());
				c2 = srgbToOKLab(color2.getRed(), color2.getGreen(), color2.getBlue());
				break;
			default:
				return null;
			}
			final double[] mixed = new double[3];
			for (int i = 0; i < 3; ++i) {
				// アルファpremultiplied補間(CSS Color 4 §interpolation)
				mixed[i] = (c1[i] * a1 * w1 + c2[i] * a2 * w2) / alpha;
			}
			final double[] rgb = space.equalsIgnoreCase("srgb") ? mixed
					: oklabToSRGB(mixed[0], mixed[1], mixed[2]);
			return fromMaybeAlpha((float) rgb[0], (float) rgb[1], (float) rgb[2], alpha);
		} catch (final IllegalArgumentException e) {
			return null;
		}
	}

	/** 直後の%トークンがあれば0..1で返します(なければnull)。 */
	private static Float eatPercent(final TokenStream args) {
		while (args.eatComma()) {
			// skip
		}
		if (args.hasNext() && args.peek() instanceof CssToken.Percent percent) {
			args.next();
			return (float) (percent.value() / 100.0);
		}
		return null;
	}

	/**
	 * color-mix/light-darkの引数の色です(transparentをrgba(0,0,0,0)として
	 * 受ける——Tailwind v4の透明度ユーティリティが多用する。単独の色指定
	 * としてのtransparentは従来どおり各プロパティ側のisTransparentが扱い、
	 * toColor本体の挙動は変えない)。
	 */
	private static ColorValue toMixArgColor(final UserAgent ua, final CssToken token) {
		if (isTransparent(token)) {
			return fromRGBAComponents(0, 0, 0, 0);
		}
		return toColor(ua, token);
	}

	/**
	 * transparent であればtrueを返します。
	 */
	public static boolean isTransparent(CssToken token) {
		return token instanceof CssToken.Ident ident && ident.is("transparent");
	}

	/**
	 * currentcolor であればtrueを返します(2026-08-29)。
	 */
	public static boolean isCurrentColor(CssToken token) {
		return token instanceof CssToken.Ident ident && ident.is("currentcolor");
	}

	/**
	 * &lt;color&gt; を値に変換します。{@code currentcolor}は
	 * {@link net.zamasoft.foliojet.css.value.KeywordValue#DEFAULT}を返す
	 * (2026-08-29)——border-color等が既定値「その要素のcolor」を表すのに
	 * 使っている番兵で、各プロパティの{@code getComputedValue}が
	 * {@code color}の計算値へ解決する。
	 */
	public static Value toColorOrCurrent(UserAgent ua, CssToken token) {
		if (isCurrentColor(token)) {
			return net.zamasoft.foliojet.css.value.KeywordValue.DEFAULT;
		}
		return toColor(ua, token);
	}

	/**
	 * &lt;color&gt; を値に変換します。
	 */
	public static ColorValue toColor(UserAgent ua, CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			return toColorValue(ident.name());
		}
		if (token instanceof CssToken.Func func) {
			if (func.is("rgb")) {
				return toRGBColorValue(func.argStream());
			}
			if (func.is("rgba")) {
				return toRGBAColorValue(func.argStream());
			}
			if (func.is("-cssj-cmyk")) {
				return toCMYKColorValue(func.argStream());
			}
			if (func.is("-cssj-spot")) {
				return toSpotColorValue(ua, func.argStream());
			}
			if (func.is("-cssj-gray")) {
				return toGrayColorValue(func.argStream());
			}
			// CSS Color 3/4(2026-08-02、PLAN §2の3位——Tailwind v4が
			// oklchを既定採用し、未対応だと色宣言が全滅する入力互換対応)
			if (func.is("hsl") || func.is("hsla")) {
				return toHSLColorValue(func.argStream());
			}
			if (func.is("hwb")) {
				return toHWBColorValue(func.argStream());
			}
			if (func.is("oklch")) {
				return toOKColorValue(func.argStream(), true);
			}
			if (func.is("oklab")) {
				return toOKColorValue(func.argStream(), false);
			}
			if (func.is("lch")) {
				return toLabColorValue(func.argStream(), true);
			}
			if (func.is("lab")) {
				return toLabColorValue(func.argStream(), false);
			}
			if (func.is("color")) {
				return toColorFunction(func.argStream());
			}
			if (func.is("color-mix")) {
				return toColorMix(ua, func.argStream());
			}
			if (func.is("light-dark")) {
				// 印刷は常にlight(ページメディアにダークモードはない——
				// 第2引数は読み捨て)
				return toMixArgColor(ua, nextComponent(func.argStream()));
			}
		}
		return null;
	}

	/**
	 * &lt;background-color&gt; を値に変換します。
	 */
	public static PaintValue toPaint(UserAgent ua, CssToken token) {
		// 型付き attr()(2026-08-03)。属性から色を取る(bgcolor/text/link等の
		// 移送に要る)。解決は計算値の段階で、長さと同じ窓口が行う
		Value attr = AttrValueUtils.toTypedAttr(ua, token,
				net.zamasoft.foliojet.css.value.TypedAttrValue.Kind.COLOR);
		if (attr instanceof PaintValue paint) {
			return paint;
		}
		PaintValue value = toColor(ua, token);
		if (value != null) {
			return value;
		}
		return toGradient(ua, token);
	}

	/**
	 * グラデーション関数を塗りに変換します(2026-08-29に対応範囲を拡張——
	 * 実サイト50件中31件・約1100回が不受理だった。同日夜に放射・円錐・
	 * 繰り返しを本実装にした)。対応外のトークンは null。
	 *
	 * <ul>
	 * <li>{@code linear-gradient()}: 現行構文</li>
	 * <li>{@code -webkit-}/{@code -moz-}/{@code -o-linear-gradient()}:
	 * 旧構文({@code to}無しの向きは<b>開始辺</b>、角度は東から反時計回り)</li>
	 * <li>{@code -webkit-gradient(linear|radial, ...)}: 2008年版のSafari構文。
	 * 2点から角度を出して線形へ、2円から中心と半径を出して放射へ写す</li>
	 * <li>{@code radial-gradient()}: 形状(circle/ellipse)・寸法(4つの
	 * キーワード・長さ・長さ2つ)・{@code at <position>}。接頭辞つき旧構文
	 * ({@code -webkit-radial-gradient(center, ellipse cover, ...)})も受ける</li>
	 * <li>{@code conic-gradient()}: {@code from <angle>}・{@code at <position>}。
	 * 角度の色停止(deg/turn/%)</li>
	 * <li>{@code repeating-*}: 周期を箱を覆うまで展開({@link GradientStops})</li>
	 * <li>{@code in <color-space>}と任意のhue補間指定は構文として受理する。
	 * 現在の描画値は既存形式を保つため、指定にかかわらずsRGB補間へフォールバックする</li>
	 * </ul>
	 */
	public static PaintValue toGradient(UserAgent ua, CssToken token) {
		if (!(token instanceof CssToken.Func func)) {
			return null;
		}
		String name = func.name().toLowerCase();
		boolean legacy = false;
		for (final String prefix : new String[] { "-webkit-", "-moz-", "-o-", "-ms-" }) {
			if (name.startsWith(prefix)) {
				name = name.substring(prefix.length());
				legacy = true;
				break;
			}
		}
		if (legacy && name.equals("gradient")) {
			return toWebkitGradient(ua, func.argStream());
		}
		boolean repeating = false;
		if (name.startsWith("repeating-")) {
			name = name.substring("repeating-".length());
			repeating = true;
		}
		switch (name) {
		case "linear-gradient":
			return toLinearGradient(ua, func.argStream(), legacy, repeating);
		case "radial-gradient":
			return toRadialGradient(ua, func.argStream(), legacy, repeating);
		case "conic-gradient":
			return toConicGradient(ua, func.argStream(), repeating);
		default:
			return null;
		}
	}

	/**
	 * 色停止列を読みます(css-images-3 §3.4.1 / css-images-4の色ヒントは
	 * 読み飛ばす)。各項は{@code <color> [<pos> [<pos>]]?}。旧実装の寛容さで
	 * 位置が色の前に書かれた形も受ける。
	 *
	 * @param angular 円錐(位置は角度か%)
	 */
	private static GradientStops parseStops(final UserAgent ua, final List<TokenStream> groups,
			final boolean angular) {
		final List<Color> colors = new ArrayList<Color>();
		final DoubleList ratios = new DoubleList();
		final DoubleList abs = new DoubleList();
		final List<Boolean> autos = new ArrayList<Boolean>();
		for (final TokenStream group : groups) {
			double[] leading = null;
			while (group.peek() != null && !(group.peek() instanceof CssToken.Ident)
					&& toColor(ua, group.peek()) == null) {
				final double[] pos = toStopPosition(ua, group.peek(), angular);
				if (pos == null) {
					break;
				}
				leading = pos;
				group.next();
			}
			final CssToken colorToken = group.next();
			if (colorToken == null) {
				// 色ヒント(`red, 30%, blue`)の項。補間の中点は未対応——読み飛ばす
				continue;
			}
			final ColorValue cv = toColor(ua, colorToken);
			if (cv == null) {
				throw new IllegalArgumentException();
			}
			final Color color = cv.getColor();
			if (!group.hasNext()) {
				colors.add(color);
				if (leading != null) {
					ratios.add(leading[0]);
					abs.add(leading[1]);
					autos.add(leading[2] != 0);
				} else {
					ratios.add(0);
					abs.add(0);
					autos.add(Boolean.TRUE);
				}
				continue;
			}
			int count = 0;
			while (group.hasNext() && count < 2) {
				final double[] pos = toStopPosition(ua, group.next(), angular);
				if (pos == null) {
					throw new IllegalArgumentException();
				}
				colors.add(color);
				ratios.add(pos[0]);
				abs.add(pos[1]);
				autos.add(pos[2] != 0);
				++count;
			}
			if (group.hasNext()) {
				throw new IllegalArgumentException();
			}
		}
		if (colors.isEmpty()) {
			throw new IllegalArgumentException();
		}
		final boolean[] auto = new boolean[autos.size()];
		for (int i = 0; i < auto.length; ++i) {
			auto[i] = autos.get(i);
		}
		return new GradientStops(colors.toArray(new Color[colors.size()]), ratios.toArray(), abs.toArray(), auto);
	}

	/**
	 * CSS Images 4の{@code in <color-space> [<hue-interpolation-method> hue]?}を
	 * 消費します。指定は構文互換のため受理し、実際の色停止補間は既存のsRGBへ
	 * フォールバックします。
	 */
	private static boolean consumeGradientInterpolation(final TokenStream tokens) {
		if (!tokens.eat("in")) {
			return false;
		}
		final String colorSpace = tokens.ident();
		if (colorSpace == null || !isGradientColorSpace(colorSpace)) {
			throw new IllegalArgumentException();
		}
		final int mark = tokens.position();
		final String hueMethod = tokens.ident();
		if (hueMethod != null) {
			switch (hueMethod.toLowerCase(java.util.Locale.ROOT)) {
			case "shorter":
			case "longer":
			case "increasing":
			case "decreasing":
				if (!tokens.eat("hue")) {
					throw new IllegalArgumentException();
				}
				break;
			default:
				tokens.rewind(mark);
				break;
			}
		}
		return true;
	}

	private static boolean isGradientColorSpace(final String colorSpace) {
		return switch (colorSpace.toLowerCase(java.util.Locale.ROOT)) {
		case "srgb", "srgb-linear", "display-p3", "a98-rgb", "prophoto-rgb", "rec2020", "lab", "lch",
				"oklab", "oklch", "xyz", "xyz-d50", "xyz-d65", "hsl", "hwb" -> true;
		default -> false;
		};
	}

	/**
	 * {@code radial-gradient([ <ending-shape> || <size> ]? [ at <position> ]?, <color-stop-list>)}
	 * および旧構文{@code -webkit-radial-gradient([<position>,]? [<shape> || <size>,]? <stops>)}
	 * (寸法キーワード{@code contain}/{@code cover}を含む)。
	 */
	private static PaintValue toRadialGradient(final UserAgent ua, final TokenStream args, final boolean legacy,
			final boolean repeating) {
		try {
			final List<TokenStream> groups = args.splitComma();
			if (groups.isEmpty()) {
				return null;
			}
			boolean circle = false;
			boolean shapeGiven = false;
			RadialGradientValue.Size size = RadialGradientValue.Size.FARTHEST_CORNER;
			QuantityValue sizeX = null, sizeY = null;
			QuantityValue[] position = null;
			int first = 0;
			if (legacy && isPositionGroup(groups.get(0))) {
				position = BasicShapes.parsePosition(groups.get(0), ua);
				first = 1;
			}
			final TokenStream prelude = first < groups.size() ? groups.get(first) : null;
			if (prelude != null && prelude.peek() != null && toColor(ua, prelude.peek()) == null) {
				final List<QuantityValue> lengths = new ArrayList<QuantityValue>();
				boolean any = false;
				while (prelude.hasNext()) {
					if (consumeGradientInterpolation(prelude)) {
						any = true;
						continue;
					}
					final CssToken t = prelude.peek();
					if (t instanceof CssToken.Ident ident) {
						switch (ident.lower()) {
						case "circle":
							circle = true;
							shapeGiven = true;
							break;
						case "ellipse":
							circle = false;
							shapeGiven = true;
							break;
						case "closest-side":
						case "contain":
							size = RadialGradientValue.Size.CLOSEST_SIDE;
							break;
						case "farthest-side":
							size = RadialGradientValue.Size.FARTHEST_SIDE;
							break;
						case "closest-corner":
							size = RadialGradientValue.Size.CLOSEST_CORNER;
							break;
						case "farthest-corner":
						case "cover":
							size = RadialGradientValue.Size.FARTHEST_CORNER;
							break;
						case "at":
							prelude.next();
							position = BasicShapes.parsePosition(prelude, ua);
							any = true;
							continue;
						default:
							throw new IllegalArgumentException();
						}
						prelude.next();
						any = true;
					} else {
						lengths.add(BasicShapes.lengthOrPercentage(ua, t));
						prelude.next();
						any = true;
					}
				}
				if (!lengths.isEmpty()) {
					size = RadialGradientValue.Size.EXPLICIT;
					sizeX = lengths.get(0);
					if (lengths.size() >= 2) {
						sizeY = lengths.get(1);
						if (!shapeGiven) {
							circle = false;
						}
					} else if (!shapeGiven) {
						// 長さ1つは円の半径
						circle = true;
					}
					if (circle && sizeX instanceof PercentageValue) {
						// 円の半径に%は不可(仕様)
						throw new IllegalArgumentException();
					}
					if (!circle && sizeY == null) {
						sizeY = sizeX;
					}
				}
				if (any) {
					++first;
				}
			}
			if (position == null) {
				position = new QuantityValue[] { PercentageValue.HALF, PercentageValue.HALF };
			}
			final GradientStops stops = parseStops(ua, groups.subList(first, groups.size()), false);
			return new RadialGradientValue(circle, size, sizeX, sizeY, position[0], position[1], stops, repeating);
		} catch (IllegalArgumentException | PropertyException e) {
			return null;
		}
	}

	/** 旧構文の先頭項が位置(center/left/...・長さ・%だけ)か。 */
	private static boolean isPositionGroup(final TokenStream group) {
		final int mark = group.position();
		try {
			boolean any = false;
			while (group.hasNext()) {
				final CssToken t = group.next();
				if (t instanceof CssToken.Ident ident) {
					switch (ident.lower()) {
					case "center":
					case "left":
					case "right":
					case "top":
					case "bottom":
						break;
					default:
						return false;
					}
				} else if (!(t instanceof CssToken.Percent) && !(t instanceof CssToken.Dim)
						&& !(t instanceof CssToken.Num num && num.value() == 0)) {
					return false;
				}
				any = true;
			}
			return any;
		} finally {
			group.rewind(mark);
		}
	}

	/**
	 * {@code conic-gradient([ from <angle> ]? [ at <position> ]?, <angular-color-stop-list>)}。
	 */
	private static PaintValue toConicGradient(final UserAgent ua, final TokenStream args, final boolean repeating) {
		try {
			final List<TokenStream> groups = args.splitComma();
			if (groups.isEmpty()) {
				return null;
			}
			double from = 0;
			QuantityValue[] position = null;
			int first = 0;
			final TokenStream prelude = groups.get(0);
			boolean any = false;
			while (prelude.hasNext()) {
				if (consumeGradientInterpolation(prelude)) {
					any = true;
				} else if (prelude.eat("from")) {
					final Double angle = toAngleRadians(prelude.next());
					if (angle == null) {
						throw new IllegalArgumentException();
					}
					from = angle;
					any = true;
				} else if (prelude.eat("at")) {
					position = BasicShapes.parsePosition(prelude, ua);
					any = true;
				} else {
					break;
				}
			}
			if (any) {
				if (prelude.hasNext()) {
					throw new IllegalArgumentException();
				}
				first = 1;
			}
			if (position == null) {
				position = new QuantityValue[] { PercentageValue.HALF, PercentageValue.HALF };
			}
			final GradientStops stops = parseStops(ua, groups.subList(first, groups.size()), true);
			return new ConicGradientValue(from, position[0], position[1], stops, repeating);
		} catch (IllegalArgumentException | PropertyException e) {
			return null;
		}
	}

	/**
	 * {@code -webkit-gradient(linear, x0 y0, x1 y1, from(c), color-stop(p, c), to(c))}
	 * (2008年版WebKit構文)を線形グラデーションへ写します。
	 */
	private static PaintValue toWebkitGradient(UserAgent ua, TokenStream args) {
		final List<TokenStream> groups = args.splitComma();
		if (groups.size() < 3) {
			return null;
		}
		final String type = groups.get(0).ident();
		if (type == null) {
			return null;
		}
		if (type.equalsIgnoreCase("radial")) {
			return toWebkitRadialGradient(ua, groups);
		}
		if (!type.equalsIgnoreCase("linear")) {
			return null;
		}
		final double[] p0 = toWebkitPoint(groups.get(1));
		final double[] p1 = toWebkitPoint(groups.get(2));
		if (p0 == null || p1 == null) {
			return null;
		}
		// CSSの角度: 0deg=上向き、時計回り(画面座標はy下向き)
		final double dx = p1[0] - p0[0];
		final double dy = p1[1] - p0[1];
		final double angle = (dx == 0 && dy == 0) ? Math.PI : Math.atan2(dx, -dy);
		final GradientStops stops = toWebkitStops(ua, groups.subList(3, groups.size()));
		if (stops == null) {
			return null;
		}
		return new LinearGradientValue(angle, stops, false);
	}

	/** -webkit-gradientの点(left/center/right・top/center/bottom・%・数)を0..1へ。 */
	private static double[] toWebkitPoint(final TokenStream group) {
		final double[] point = new double[2];
		for (int axis = 0; axis < 2; ++axis) {
			final CssToken token = group.next();
			if (token instanceof CssToken.Ident ident) {
				switch (ident.lower()) {
				case "left":
				case "top":
					point[axis] = 0;
					break;
				case "center":
					point[axis] = 0.5;
					break;
				case "right":
				case "bottom":
					point[axis] = 1;
					break;
				default:
					return null;
				}
			} else if (token instanceof CssToken.Percent percent) {
				point[axis] = percent.value() / 100;
			} else if (token instanceof CssToken.Num num) {
				point[axis] = num.value();
			} else {
				return null;
			}
		}
		return point;
	}

	/** 角度トークンをラジアンで返します(deg/rad/grad/turn)。角度でなければnull。 */
	public static Double toAngleRadians(final CssToken token) {
		if (token instanceof CssToken.Dim dim) {
			switch (dim.unit()) {
			case DEG:
				return dim.value() * Math.PI / 180;
			case RAD:
				return dim.value();
			case GRAD:
				return dim.value() * Math.PI / 200;
			default:
				return dim.unitText().equalsIgnoreCase("turn") ? dim.value() * Math.PI * 2 : null;
			}
		}
		Value calc = CalcValueUtils.toCalc(null, token);
		return calc instanceof net.zamasoft.foliojet.css.value.AngleValue angle ? angle.getRadians() : null;
	}

	/**
	 * 色停止の位置を{@code [割合, 絶対長pt, 自動なら1]}で返します(2026-08-29に
	 * 絶対長を保持するよう変更——それまでは長さの位置を等間隔補間に落として
	 * いたので、{@code repeating-linear-gradient(#fff, #000 10px)}の周期が
	 * 消えていた)。em等のフォント相対長は計算値の段階で解決されないため
	 * 自動として近似する。calc()は割合と絶対の両成分を使う。
	 * 位置として読めなければnull。
	 *
	 * @param angular 円錐の停止(角度を1周=1の割合へ写す)
	 */
	private static double[] toStopPosition(final UserAgent ua, final CssToken token, final boolean angular) {
		if (token instanceof CssToken.Percent percent) {
			return new double[] { percent.value() / 100.0, 0, 0 };
		}
		if (angular) {
			final Double angle = toAngleRadians(token);
			if (angle != null) {
				return new double[] { angle / (Math.PI * 2), 0, 0 };
			}
			if (token instanceof CssToken.Num num && num.value() == 0) {
				return new double[] { 0, 0, 0 };
			}
			return null;
		}
		if (token instanceof CssToken.Dim || (token instanceof CssToken.Num num && num.value() == 0)) {
			final Value length = ValueUtils.toLength(ua, token);
			if (length instanceof net.zamasoft.foliojet.css.value.AbsoluteLengthValue abs) {
				return new double[] { 0, abs.getLength(), 0 };
			}
			return length != null ? new double[] { 0, 0, 1 } : null;
		}
		final Value calc = CalcValueUtils.toCalc(ua, token);
		if (calc instanceof net.zamasoft.foliojet.css.value.CalcLengthValue mixed) {
			return new double[] { mixed.getRatio(), mixed.getAbsolute(), 0 };
		}
		if (calc instanceof net.zamasoft.foliojet.css.value.PercentageValue p) {
			return new double[] { p.getRatio(), 0, 0 };
		}
		if (calc instanceof net.zamasoft.foliojet.css.value.AbsoluteLengthValue abs) {
			return new double[] { 0, abs.getLength(), 0 };
		}
		if (calc instanceof net.zamasoft.foliojet.css.value.CalcFontRelativeValue) {
			return new double[] { 0, 0, 1 };
		}
		return null;
	}

	/**
	 * {@code -webkit-gradient(radial, x0 y0, r0, x1 y1, r1, stops)}。外側の円
	 * (中心x1 y1・半径r1)を終了形状にし、内側の円は無視する(焦点は
	 * PDFのType 3で表せるが、旧構文の実例はほぼ同心なので使わない)。
	 * 半径の数はCSS px。
	 */
	private static PaintValue toWebkitRadialGradient(final UserAgent ua, final List<TokenStream> groups) {
		if (groups.size() < 6) {
			return null;
		}
		final double[] center = toWebkitPoint(groups.get(3));
		final CssToken r1 = groups.get(4).next();
		if (center == null || !(r1 instanceof CssToken.Num radius)) {
			return null;
		}
		final GradientStops stops = toWebkitStops(ua, groups.subList(5, groups.size()));
		if (stops == null) {
			return null;
		}
		final QuantityValue r = ValueUtils.toAbsoluteLength(ua, new CssToken.Dim(radius.value(), Unit.PX, "px"));
		return new RadialGradientValue(true, RadialGradientValue.Size.EXPLICIT, r, r,
				PercentageValue.create(center[0] * 100), PercentageValue.create(center[1] * 100), stops, false);
	}

	/** {@code from()/to()/color-stop()}の列を停止列にします。読めなければnull。 */
	private static GradientStops toWebkitStops(final UserAgent ua, final List<TokenStream> groups) {
		final List<Color> colors = new ArrayList<Color>();
		final DoubleList fracs = new DoubleList();
		for (final TokenStream group : groups) {
			final CssToken token = group.next();
			if (!(token instanceof CssToken.Func stop)) {
				return null;
			}
			final TokenStream inner = stop.argStream();
			final double position;
			if (stop.is("from")) {
				position = 0;
			} else if (stop.is("to")) {
				position = 1;
			} else if (stop.is("color-stop")) {
				final CssToken pos = nextComponent(inner);
				if (pos instanceof CssToken.Percent percent) {
					position = percent.value() / 100;
				} else if (pos instanceof CssToken.Num num) {
					position = num.value();
				} else {
					return null;
				}
			} else {
				return null;
			}
			final ColorValue color = toColor(ua, nextComponent(inner));
			if (color == null) {
				return null;
			}
			colors.add(color.getColor());
			fracs.add(position);
		}
		if (colors.isEmpty()) {
			return null;
		}
		return GradientStops.ofFractions(fracs.toArray(), colors.toArray(new Color[colors.size()]));
	}

	/**
	 * &lt;background-repeat&gt; を値に変換します。
	 */
	public static BackgroundRepeatValue toBackgroundRepeat(CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "repeat":
				return BackgroundRepeatValue.REPEAT_VALUE;
			case "repeat-x":
				return BackgroundRepeatValue.REPEAT_X_VALUE;
			case "repeat-y":
				return BackgroundRepeatValue.REPEAT_Y_VALUE;
			case "no-repeat":
				return BackgroundRepeatValue.NO_REPEAT_VALUE;
			}
		}
		return null;
	}

	/**
	 * &lt;background-attachment&gt; を値に変換します。
	 */
	public static BackgroundAttachmentValue toBackgroundAttachment(CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "scroll":
				return BackgroundAttachmentValue.SCROLL_VALUE;
			case "fixed":
				return BackgroundAttachmentValue.FIXED_VALUE;
			}
		}
		return null;
	}

	/**
	 * &lt;background-clip&gt; を値に変換します。
	 */
	public static BackgroundClipValue toBackgroundClip(CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "border-box":
				return BackgroundClipValue.BORDER_BOX_VALUE;
			case "padding-box":
				return BackgroundClipValue.PADDING_BOX_VALUE;
			case "content-box":
				return BackgroundClipValue.CONTENT_BOX_VALUE;
			case "text":
				return BackgroundClipValue.TEXT_VALUE;
			}
		}
		return null;
	}

	/**
	 * &lt;background-origin&gt; を値に変換します。
	 */
	public static net.zamasoft.foliojet.css.value.css3.BackgroundOriginValue toBackgroundOrigin(CssToken token) {
		if (token instanceof CssToken.Ident ident) {
			switch (ident.lower()) {
			case "border-box":
				return net.zamasoft.foliojet.css.value.css3.BackgroundOriginValue.BORDER_BOX_VALUE;
			case "padding-box":
				return net.zamasoft.foliojet.css.value.css3.BackgroundOriginValue.PADDING_BOX_VALUE;
			case "content-box":
				return net.zamasoft.foliojet.css.value.css3.BackgroundOriginValue.CONTENT_BOX_VALUE;
			}
		}
		return null;
	}
}
