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
 * 出力デバイスの既定スタイル・メトリクスの読み取り面です。
 * CSSの解釈に必要な情報を提供します。
 */
public interface DeviceStyle {
	/**
	 * このUAに対応するデフォルトのロケールを返します。
	 */
	public Locale getDefaultLocale();

	/**
	 * このメディアが与えられたメディアタイプに含まれる場合はtrueを返します。
	 *
	 * @param mediaTypes スペース区切りのメディアタイプ
	 */
	public boolean is(String mediaTypes);

	/**
	 * デバイスの解像度(1インチ当たりのピクセル数)を返します。dpi(1インチ当たりのドット数)で
	 * ないことに注意してください。これはPixelからの換算に使われます。
	 */
	public double getPixelsPerInch();

	/**
	 * line-height特性にnormalが指定された際のフォントとの比率(1.0-1.2)を返します。
	 */
	public double getNormalLineHeight();

	/**
	 * デバイスのデフォルトの前景色を返します。
	 */
	public ColorValue getDefaultColor();

	/**
	 * デバイスのマット色を返します。
	 */
	public ColorValue getMatColor();

	/**
	 * デフォルトのマーカーオフセット(リスト項目の装飾とテキストの間の長さ)を返します。
	 */
	public LengthValue getDefaultMarkerOffset();

	/**
	 * デフォルトのフォントを返します。
	 */
	public FontFamilyValue getDefaultFontFamily();

	/**
	 * フォントの埋め込みポリシーを返します。
	 */
	public CSSJFontPolicyValue getDefaultFontPolicy();

	/**
	 * 絶対フォントサイズキーワードに対応するフォントのサイズを返します。
	 */
	public double getFontSize(AbsoluteFontSize absoluteFontSize);

	/**
	 * 文字サイズを調整して表示するための、フォントの倍率です。
	 */
	public double getFontMagnification();

	/**
	 * 与えられたサイズより一回り大きなフォントのサイズを返します。
	 */
	public double getLargerFontSize(double fontSize);

	/**
	 * 与えられたサイズより一回り小さなフォントのサイズを返します。
	 */
	public double getSmallerFontSize(double fontSize);

	/**
	 * 境界線の太さを返します。border-widthプロパティ等で使われます。
	 */
	public AbsoluteLengthValue getBorderWidth(BorderWidthKeyword keyword);

	/**
	 * ボックスの大きさの最小値を返します。
	 */
	public LengthValue getMinSize();

	/**
	 * ボックスの大きさの最大値を返します。
	 */
	public Value getMaxSize();

	/**
	 * フォントマネージャを返します。
	 */
	public FontManager getFontManager();
}
