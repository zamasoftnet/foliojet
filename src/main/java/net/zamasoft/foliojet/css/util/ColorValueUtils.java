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
import net.zamasoft.foliojet.css.value.css3.BackgroundClipValue;
import net.zamasoft.foliojet.css.value.css3.LinearGradientValue;
import net.zamasoft.foliojet.style.util.DoubleList;
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
 * @version $Id: ColorValueUtils.java 1629 2022-05-12 05:41:40Z miyabe $
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
			if (color.length() >= 6) {
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
			float alpha = toColorComponent(nextComponent(args));
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

	private static PaintValue toLinearGradient(UserAgent ua, TokenStream args) {
		try {
			double angle = 180 * Math.PI * 2 / 360;
			// 方向指定(<angle> | to <side> [<side>])
			CssToken first = args.peek();
			if (first instanceof CssToken.Dim dim && dim.unit() == Unit.DEG) {
				args.next();
				angle = dim.value() * Math.PI * 2 / 360;
				args.eatComma();
			} else if (args.eat("to")) {
				String a = args.ident();
				if (a == null) {
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
				args.eatComma();
			}

			List<Color> colors = new ArrayList<Color>();
			DoubleList fracs = new DoubleList();

			for (TokenStream group : args.splitComma()) {
				// 旧実装の寛容さを踏襲: 色の前のパーセント値は読み飛ばす
				while (group.peek() instanceof CssToken.Percent) {
					group.next();
				}
				CssToken colorToken = group.next();
				if (colorToken == null) {
					continue;
				}
				ColorValue cv = toColor(ua, colorToken);
				if (cv == null) {
					throw new IllegalArgumentException();
				}
				Color color = cv.getColor();
				if (!group.hasNext()) {
					colors.add(color);
					fracs.add(-1);
				} else {
					while (group.hasNext()) {
						CssToken stop = group.next();
						if (!(stop instanceof CssToken.Percent percent)) {
							throw new IllegalArgumentException();
						}
						colors.add(color);
						fracs.add(percent.value() / 100.0);
					}
				}
			}
			if (colors.isEmpty()) {
				throw new IllegalArgumentException();
			}
			double[] ds = fracs.toArray();
			if (ds[0] == -1) {
				ds[0] = 0;
			}
			ds[ds.length - 1] = 1;
			double a = ds[0];
			for (int i = 1; i < ds.length; ++i) {
				if (ds[i] == -1) {
					int j = i + 1;
					for (; ds[j] == -1; ++j) ;
					double b = ds[j];
					double step = (b - a) / (j - i);
					for(; i < j; ++i) {
						a += step;
						ds[i] = a;
					}
				}
				a = ds[i];
			}

			return new LinearGradientValue(angle, ds, colors.toArray(new Color[colors.size()]));
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

	/**
	 * transparent であればtrueを返します。
	 */
	public static boolean isTransparent(CssToken token) {
		return token instanceof CssToken.Ident ident && ident.is("transparent");
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
		}
		return null;
	}

	/**
	 * &lt;background-color&gt; を値に変換します。
	 */
	public static PaintValue toPaint(UserAgent ua, CssToken token) {
		PaintValue value = toColor(ua, token);
		if (value != null) {
			return value;
		}
		if (token instanceof CssToken.Func func && func.is("linear-gradient")) {
			return toLinearGradient(ua, func.argStream());
		}
		return null;
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
}
