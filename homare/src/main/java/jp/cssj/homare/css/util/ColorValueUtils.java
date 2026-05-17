package jp.cssj.homare.css.util;

import java.awt.SystemColor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.primitives.ArrayDoubleList;

import jp.cssj.homare.css.value.BackgroundAttachmentValue;
import jp.cssj.homare.css.value.BackgroundRepeatValue;
import jp.cssj.homare.css.value.ColorValue;
import jp.cssj.homare.css.value.PaintValue;
import jp.cssj.homare.css.value.css3.BackgroundClipValue;
import jp.cssj.homare.css.value.css3.LinearGradientValue;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.paint.CMYKColor;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.paint.GrayColor;
import net.zamasoft.pdfg2d.gc.paint.RGBAColor;
import net.zamasoft.pdfg2d.gc.paint.RGBColor;
import net.zamasoft.pdfg2d.sac.css.LexicalUnit;

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
	 * rgbcolor をRGBColorValueに変換します。
	 * 
	 * @param value
	 * @return
	 */
	private static ColorValue toRGBColorValue(LexicalUnit value) {
		try {
			float red = toColorComponent(value);

			value = value.getNextLexicalUnit().getNextLexicalUnit();
			float green = toColorComponent(value);

			value = value.getNextLexicalUnit().getNextLexicalUnit();
			float blue = toColorComponent(value);

			return fromRGBComponents(red, green, blue);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * rgba をRGBAColorValueに変換します。
	 * 
	 * @param value
	 * @return
	 */
	private static ColorValue toRGBAColorValue(LexicalUnit value) {
		try {
			float red = toColorComponent(value);

			value = value.getNextLexicalUnit().getNextLexicalUnit();
			float green = toColorComponent(value);

			value = value.getNextLexicalUnit().getNextLexicalUnit();
			float blue = toColorComponent(value);

			value = value.getNextLexicalUnit().getNextLexicalUnit();
			float alpha = toColorComponent(value);

			return fromRGBAComponents(red, green, blue, alpha);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/**
	 * -cssj-cmyk をCMYKColorValueに変換します。
	 * 
	 * @param value
	 * @return
	 */
	private static ColorValue toCMYKColorValue(LexicalUnit value) {
		try {
			float cyan = toColorComponent(value);

			value = value.getNextLexicalUnit().getNextLexicalUnit();
			float magenta = toColorComponent(value);

			value = value.getNextLexicalUnit().getNextLexicalUnit();
			float yellow = toColorComponent(value);

			value = value.getNextLexicalUnit().getNextLexicalUnit();
			float black = toColorComponent(value);

			byte overprint = CMYKColor.OVERPRINT_NONE;
			value = value.getNextLexicalUnit();
			if (value != null) {
				value = value.getNextLexicalUnit();
				if (value.getLexicalUnitType() == LexicalUnit.SAC_IDENT) {
					String ident = value.getStringValue();
					if (ident.equalsIgnoreCase("standard")) {
						overprint = CMYKColor.OVERPRINT_STANDARD;
					} else if (ident.equalsIgnoreCase("illustrator")) {
						overprint = CMYKColor.OVERPRINT_ILLUSTRATOR;
					}
				}
			}

			return fromCMYKComponents(cyan, magenta, yellow, black, overprint);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static PaintValue toLinearGradient(UserAgent ua, LexicalUnit lu) {
		try {
			double angle = 180 * Math.PI * 2 / 360;
			switch (lu.getLexicalUnitType()) {
			case LexicalUnit.SAC_DEGREE:
				angle = lu.getFloatValue() * Math.PI * 2 / 360;
				lu = lu.getNextLexicalUnit().getNextLexicalUnit();
				break;
			case LexicalUnit.SAC_IDENT:
				if (!lu.getStringValue().equalsIgnoreCase("to")) {
					break;
				}
				lu = lu.getNextLexicalUnit();
				if (lu.getLexicalUnitType() != LexicalUnit.SAC_IDENT) {
					throw new IllegalArgumentException();
				}
				String ident = lu.getStringValue();
				if (ident.equalsIgnoreCase("top")) {
					lu = lu.getNextLexicalUnit();
					if (lu.getLexicalUnitType() != LexicalUnit.SAC_IDENT) {
						angle = 0 * Math.PI * 2 / 360;
						break;
					}
					ident = lu.getStringValue();
					lu = lu.getNextLexicalUnit();
					if (ident.equalsIgnoreCase("left")) {
						angle = 315 * Math.PI * 2 / 360;
					}
					else if (ident.equalsIgnoreCase("right")) {
						angle = 45 * Math.PI * 2 / 360;
					}
					break;
				}
				else if (ident.equalsIgnoreCase("bottom")) {
					lu = lu.getNextLexicalUnit();
					if (lu.getLexicalUnitType() != LexicalUnit.SAC_IDENT) {
						angle = 180 * Math.PI * 2 / 360;
						break;
					}
					ident = lu.getStringValue();
					lu = lu.getNextLexicalUnit();
					if (ident.equalsIgnoreCase("left")) {
						angle = 225 * Math.PI * 2 / 360;
					}
					else if (ident.equalsIgnoreCase("right")) {
						angle = 135 * Math.PI * 2 / 360;
					}
					break;
				}
				else if (ident.equalsIgnoreCase("left")) {
					lu = lu.getNextLexicalUnit();
					if (lu.getLexicalUnitType() != LexicalUnit.SAC_IDENT) {
						angle = 270 * Math.PI * 2 / 360;
						break;
					}
					ident = lu.getStringValue();
					lu = lu.getNextLexicalUnit();
					if (ident.equalsIgnoreCase("top")) {
						angle = 315 * Math.PI * 2 / 360;
					}
					else if (ident.equalsIgnoreCase("bottom")) {
						angle = 225 * Math.PI * 2 / 360;
					}
					break;
				}
				else if (ident.equalsIgnoreCase("right")) {
					lu = lu.getNextLexicalUnit();
					if (lu.getLexicalUnitType() != LexicalUnit.SAC_IDENT) {
						angle = 90 * Math.PI * 2 / 360;
						break;
					}
					ident = lu.getStringValue();
					lu = lu.getNextLexicalUnit();
					if (ident.equalsIgnoreCase("top")) {
						angle = 45 * Math.PI * 2 / 360;
					}
					else if (ident.equalsIgnoreCase("bottom")) {
						angle = 135 * Math.PI * 2 / 360;
					}
					break;
				}
			}

			List<Color> colors = new ArrayList<Color>();
			ArrayDoubleList fracs = new ArrayDoubleList();

			for (;;) {
				if (lu.getLexicalUnitType() == LexicalUnit.SAC_PERCENTAGE || lu.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_COMMA) {
					lu = lu.getNextLexicalUnit();
					continue;
				}
				ColorValue cv = toColor(ua, lu);
				if (cv == null) {
					throw new IllegalArgumentException();
				}
				Color color = cv.getColor();
				lu = lu.getNextLexicalUnit();
				if (lu == null || lu.getLexicalUnitType() == LexicalUnit.SAC_OPERATOR_COMMA) {
					colors.add(color);
					fracs.add(-1);
				} else {
					while (lu != null && lu.getLexicalUnitType() != LexicalUnit.SAC_OPERATOR_COMMA) {
						if (lu.getLexicalUnitType() != LexicalUnit.SAC_PERCENTAGE) {
							throw new IllegalArgumentException();
						}
						colors.add(color);
						fracs.add(lu.getFloatValue() / 100f);
						lu = lu.getNextLexicalUnit();
					}
				}
				if (lu == null) {
					break;
				}
				lu = lu.getNextLexicalUnit();
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

	private static ColorValue toGrayColorValue(LexicalUnit value) {
		try {
			return fromGrayComponent(toColorComponent(value));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static float toColorComponent(LexicalUnit value) throws IllegalArgumentException {
		if (value == null) {
			throw new IllegalArgumentException();
		}
		float a;
		switch (value.getLexicalUnitType()) {
		case LexicalUnit.SAC_PERCENTAGE:
			a = value.getFloatValue() / 100f;
			break;
		case LexicalUnit.SAC_REAL:
			a = value.getFloatValue();
			break;
		case LexicalUnit.SAC_INTEGER:
			a = (float) value.getIntegerValue() / 255f;
			break;
		default:
			throw new IllegalArgumentException();
		}
		return a;
	}

	/**
	 * transparent であればtrueを返します。
	 * 
	 * @param lu
	 * @return
	 */
	public static boolean isTransparent(LexicalUnit lu) {
		return (lu.getLexicalUnitType() == LexicalUnit.SAC_IDENT
				&& lu.getStringValue().equalsIgnoreCase("transparent"));
	}

	/**
	 * &lt;color&gt; を値に変換します。
	 * 
	 * @param lu
	 * @return
	 */
	public static ColorValue toColor(UserAgent ua, LexicalUnit lu) {
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_IDENT:
			String colorName = lu.getStringValue();
			ColorValue color = toColorValue(colorName);
			return color;

		case LexicalUnit.SAC_RGBCOLOR:
			LexicalUnit rgb = lu.getParameters();
			return toRGBColorValue(rgb);

		case LexicalUnit.SAC_FUNCTION:
			String func = lu.getFunctionName();
			if (func.equalsIgnoreCase("-cssj-cmyk")) {
				LexicalUnit cmyk = lu.getParameters();
				return toCMYKColorValue(cmyk);
			} else if (func.equalsIgnoreCase("-cssj-gray")) {
				LexicalUnit gray = lu.getParameters();
				return toGrayColorValue(gray);
			} else if (func.equalsIgnoreCase("rgba")) {
				LexicalUnit gray = lu.getParameters();
				return toRGBAColorValue(gray);
			}
			break;
		}
		return null;
	}

	/**
	 * &lt;background-color&gt; を値に変換します。
	 * 
	 * @param lu
	 * @return
	 */
	public static PaintValue toPaint(UserAgent ua, LexicalUnit lu) {
		PaintValue value = toColor(ua, lu);
		if (value != null) {
			return value;
		}
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_FUNCTION:
			String func = lu.getFunctionName();
			if (func.equalsIgnoreCase("linear-gradient")) {
				LexicalUnit lg = lu.getParameters();
				return toLinearGradient(ua, lg);
			}
			break;
		}
		return null;
	}

	/**
	 * &lt;background-repeat&gt; を値に変換します。
	 * 
	 * @param lu
	 * @return
	 */
	public static BackgroundRepeatValue toBackgroundRepeat(LexicalUnit lu) {
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("repeat")) {
				return BackgroundRepeatValue.REPEAT_VALUE;
			} else if (ident.equals("repeat-x")) {
				return BackgroundRepeatValue.REPEAT_X_VALUE;
			} else if (ident.equals("repeat-y")) {
				return BackgroundRepeatValue.REPEAT_Y_VALUE;
			} else if (ident.equals("no-repeat")) {
				return BackgroundRepeatValue.NO_REPEAT_VALUE;
			}
		}
		return null;
	}

	/**
	 * &lt;background-attachment を値に変換します。
	 * 
	 * @param lu
	 * @return
	 */
	public static BackgroundAttachmentValue toBackgroundAttachment(LexicalUnit lu) {
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("scroll")) {
				return BackgroundAttachmentValue.SCROLL_VALUE;
			} else if (ident.equals("fixed")) {
				return BackgroundAttachmentValue.FIXED_VALUE;
			}
		}
		return null;
	}

	/**
	 * &lt;background-clip&gt; を値に変換します。
	 * 
	 * @param lu
	 * @return
	 */
	public static BackgroundClipValue toBackgroundClip(LexicalUnit lu) {
		switch (lu.getLexicalUnitType()) {
		case LexicalUnit.SAC_IDENT:
			String ident = lu.getStringValue().toLowerCase();
			if (ident.equals("border-box")) {
				return BackgroundClipValue.BORDER_BOX_VALUE;
			} else if (ident.equals("padding-box")) {
				return BackgroundClipValue.PADDING_BOX_VALUE;
			} else if (ident.equals("content-box")) {
				return BackgroundClipValue.CONTENT_BOX_VALUE;
			} else if (ident.equals("text")) {
				return BackgroundClipValue.TEXT_VALUE;
			}
		}
		return null;
	}
}