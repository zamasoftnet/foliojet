package net.zamasoft.foliojet.ua;

import java.util.Locale;

import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.FontFamilyValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJFontPolicyValue;
import net.zamasoft.pdfg2d.gc.font.FontManager;

/**
 * Read interface for the output device's default styles and metrics.
 * Provides the information needed to interpret CSS.
 */
public interface DeviceStyle {
	/**
	 * Returns the default locale for this UA.
	 */
	public Locale getDefaultLocale();

	/**
	 * Returns true if this medium is included in the given media types.
	 *
	 * @param mediaTypes space-separated media types
	 */
	public boolean is(String mediaTypes);

	/**
	 * Returns the device resolution in pixels per inch. This is not dpi (dots per inch).
	 * Used for conversion from pixels.
	 */
	public double getPixelsPerInch();

	/**
	 * Returns the ratio to the font size (1.0-1.2) when the line-height property is normal.
	 */
	public double getNormalLineHeight();

	/**
	 * Returns the device's default foreground color.
	 */
	public ColorValue getDefaultColor();

	/**
	 * Returns the device's matte color.
	 */
	public ColorValue getMatColor();

	/**
	 * Returns the default marker offset (the distance between a list item's marker and its text).
	 */
	public LengthValue getDefaultMarkerOffset();

	/**
	 * Returns the default font.
	 */
	public FontFamilyValue getDefaultFontFamily();

	/**
	 * Returns the font embedding policy.
	 */
	public CSSJFontPolicyValue getDefaultFontPolicy();

	/**
	 * Returns the font size corresponding to an absolute font-size keyword.
	 */
	public double getFontSize(AbsoluteFontSize absoluteFontSize);

	/**
	 * The font scaling factor for displaying adjusted text sizes.
	 */
	public double getFontMagnification();

	/**
	 * Returns the next larger font size for the given size.
	 */
	public double getLargerFontSize(double fontSize);

	/**
	 * Returns the next smaller font size for the given size.
	 */
	public double getSmallerFontSize(double fontSize);

	/**
	 * Returns the border thickness. Used for properties such as border-width.
	 */
	public AbsoluteLengthValue getBorderWidth(BorderWidthKeyword keyword);

	/**
	 * Returns the minimum box size.
	 */
	public LengthValue getMinSize();

	/**
	 * Returns the maximum box size.
	 */
	public Value getMaxSize();

	/**
	 * Returns the font manager.
	 */
	public FontManager getFontManager();
}
