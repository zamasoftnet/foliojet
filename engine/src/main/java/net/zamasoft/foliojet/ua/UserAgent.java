package net.zamasoft.foliojet.ua;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.value.FontFamilyValue;
import net.zamasoft.foliojet.css.value.LengthValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.value.ext.CSSJFontPolicyValue;
import net.zamasoft.foliojet.message.MessageHandler;
import net.zamasoft.foliojet.style.visitor.Visitor;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.SourceResolver;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * UAのプロファイルです。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: UserAgent.java 1566 2018-07-04 11:52:15Z miyabe $
 */
public interface UserAgent extends SourceResolver, MessageHandler {
	/**
	 * 片面。
	 */
	public static final byte BOUND_SIDE_SINGLE = 0;

	/**
	 * 左綴じ。
	 */
	public static final byte BOUND_SIDE_LEFT = 1;

	/**
	 * 右綴じ。
	 */
	public static final byte BOUND_SIDE_RIGHT = 2;

	/**
	 * 太さがthinの境界です。
	 */
	public static final byte BORDER_WIDTH_THIN = 1;

	/**
	 * 太さがmediumの境界です。
	 */
	public static final byte BORDER_WIDTH_MEDIUM = 2;

	/**
	 * 太さがthickの境界です。
	 */
	public static final byte BORDER_WIDTH_THICK = 3;

	public static final byte FONT_SIZE_XX_SMALL = 1;
	public static final byte FONT_SIZE_X_SMALL = 2;
	public static final byte FONT_SIZE_SMALL = 3;
	public static final byte FONT_SIZE_MEDIUM = 4;
	public static final byte FONT_SIZE_LARGE = 5;
	public static final byte FONT_SIZE_X_LARGE = 6;
	public static final byte FONT_SIZE_XX_LARGE = 7;

	public static final byte PREPARE_DOCUMENT = 0;
	public static final byte PREPARE_MIDDLE_PASS = 1;
	public static final byte PREPARE_LAST_PASS = 2;

	public void prepare(byte mode);

	public UAContext getUAContext();

	public PassContext getPassContext();

	public DocumentContext getDocumentContext();

	/**
	 * 結果文書を構築します。
	 * 
	 * @throws BrokenResultException
	 * @throws IOException
	 */
	public void finish() throws BrokenResultException, IOException;

	/**
	 * UAを破棄して、リソースを解放します。
	 */
	public void dispose();

	/**
	 * プロパティを返します。
	 * 
	 * @return
	 */
	public String getProperty(String name);

	/**
	 * プロパティを設定します。
	 * 
	 * @param name
	 * @param value
	 */
	public void setProperty(String name, String value);

	public void setProperties(Map<String, String> props);

	public void setSourceResolver(SourceResolver resolver);

	public SourceResolver getSourceResolver();

	public void setMessageHandler(jp.cssj.cti2.message.MessageHandler messageHander);

	/**
	 * 処理を中断します。
	 */
	public void abort(byte mode);

	/**
	 * このUAに対応するデフォルトのロケールを返します。
	 * 
	 * @return
	 */
	public Locale getDefaultLocale();

	/**
	 * このメディアが与えられたメディアタイプに含まれる場合はtrueを返します。
	 * 
	 * @param mediaTypes
	 *            スペース区切りのメディアタイプ
	 * @return
	 */
	public boolean is(String mediaTypes);

	/**
	 * デバイスの解像度(1インチ当たりのピクセル数)を返します。 dpi(1インチ当たりのドット数)でないことに注意してください。
	 * これはPixelからの換算に使われます。
	 * 
	 * @return
	 */
	public double getPixelsPerInch();

	/**
	 * line-height特性にnormalが指定された際のフォントとの比率(1.0-1.2)を返します。
	 * 
	 * @return
	 */
	public double getNormalLineHeight();

	/**
	 * デバイスのデフォルトの前景色を返します。
	 * 
	 * @return
	 */
	public ColorValue getDefaultColor();

	/**
	 * デバイスのマット色を返します。
	 * 
	 * @return
	 */
	public ColorValue getMatColor();

	/**
	 * デフォルトのマーカーオフセット（リスト項目の装飾とテキストの間の長さ）を返します。
	 * 
	 * @return
	 */
	public LengthValue getDefaultMarkerOffset();

	/**
	 * デフォルトのフォントを返します。
	 * 
	 * @return
	 */
	public FontFamilyValue getDefaultFontFamily();

	/**
	 * フォントの埋め込みポリシーを返します。
	 * 
	 * @return
	 */
	public CSSJFontPolicyValue getDefaultFontPolicy();

	/**
	 * 絶対フォントサイズに対応するフォントのサイズを返します。
	 * 
	 * @param absoluteFontSize
	 * @return
	 */
	public double getFontSize(byte absoluteFontSize);

	/**
	 * 文字サイズを調整して表示するための、フォントの倍率です。
	 * 
	 * @return
	 */
	public double getFontMagnification();

	/**
	 * 与えられたサイズより一回り大きなフォントのサイズを返します。
	 * 
	 * @param fontSize
	 * @return
	 */
	public double getLargerFontSize(double fontSize);

	/**
	 * 与えられたサイズより一回り小さなフォントのサイズを返します。
	 * 
	 * @param fontSize
	 * @return
	 */
	public double getSmallerFontSize(double fontSize);

	/**
	 * 境界線の太さを返します。border-widthプロパティ等で使われます。
	 * 
	 * @param type
	 * @return
	 */
	public AbsoluteLengthValue getBorderWidth(byte type);

	/**
	 * ボックスの大きさの最小値を返します。
	 * 
	 * @return
	 */
	public LengthValue getMinSize();

	/**
	 * ボックスの大きさの最大値を返します。
	 * 
	 * @return
	 */
	public Value getMaxSize();

	/**
	 * フォントマネージャを返します。
	 * 
	 * @return
	 */
	public FontManager getFontManager();

	/**
	 * 画像を取得します。
	 * 
	 * @param source
	 * @return
	 */
	public Image getImage(Source source) throws IOException;

	/**
	 * 文書のメタ情報を設定します。
	 * 
	 * @param name
	 * @param content
	 */
	public void meta(String name, String content);

	public void message(short code);

	public void message(short code, String arg0);

	public void message(short code, String arg0, String arg1);

	public void message(short code, String arg0, String arg1, String arg2);

	public void setBoundSide(byte boundSide);

	public byte getBoundSide();

	public boolean isMeasurePass();

	/**
	 * 次のページのグラフィックコンテキストを返します。
	 * 
	 * @param width
	 *            ポイント単位の幅。
	 * @param height
	 *            ポイント単位の高さ。
	 * @return
	 */
	public GC nextPage(double width, double height);

	/**
	 * ページへの描画を完了します。
	 * 
	 * @param gc
	 */
	public void closePage(GC gc) throws IOException;

	public Visitor getVisitor(GC gc);
}