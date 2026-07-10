package net.zamasoft.foliojet.style.box;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.GeneralPath;

import net.zamasoft.foliojet.style.box.impl.PageBox;
import net.zamasoft.foliojet.style.box.params.Params;
import net.zamasoft.foliojet.style.box.params.Pos;
import net.zamasoft.foliojet.style.draw.Drawer;
import net.zamasoft.foliojet.style.visitor.Visitor;

public interface IBox {
	public static final byte TYPE_PAGE = 1;
	public static final byte TYPE_TEXT_BLOCK = 2;
	public static final byte TYPE_LINE = 3;
	public static final byte TYPE_INLINE = 4;
	public static final byte TYPE_BLOCK = 5;
	public static final byte TYPE_REPLACED = 6;
	public static final byte TYPE_TABLE = 7;
	public static final byte TYPE_TABLE_COLUMN_GROUP = 8;
	public static final byte TYPE_TABLE_COLUMN = 9;
	public static final byte TYPE_TABLE_ROW_GROUP = 10;
	public static final byte TYPE_TABLE_ROW = 11;
	public static final byte TYPE_TABLE_CELL = 12;

	public static final byte SUBTYPE_RUBY = 1;
	public static final byte SUBTYPE_RUBY_BODY = 2;

	/**
	 * ボックスのタイプを返します。
	 * 
	 * @return
	 */
	public byte getType();

	public byte getSubtype();

	/**
	 * 内容のパラメータを返します。
	 * 
	 * @return
	 */
	public Params getParams();

	/**
	 * 位置のパラメータを返します。
	 * 
	 * @return
	 */
	public Pos getPos();

	/**
	 * ボックスの現在の幅を返します。
	 * 
	 * @return
	 */
	public double getWidth();

	/**
	 * ボックスの現在の高さを返します。
	 * 
	 * @return
	 */
	public double getHeight();

	/**
	 * ボックスの現在の内部幅を返します。
	 * 
	 * @return
	 */
	public double getInnerWidth();

	/**
	 * ボックスの現在の内部高さを返します。
	 * 
	 * @return
	 */
	public double getInnerHeight();

	/**
	 * ページ方向の幅を確定します。
	 * 
	 * @param containerBox
	 */
	public void finishLayout(IFramedBox containerBox);

	/**
	 * 描画可能なコンテンツを追加します。
	 * 
	 * <p>
	 * 与えられる座標系はページの左上を基点とします。
	 * </p>
	 * 
	 * @param pageBox
	 *            TODO
	 * @param drawer
	 * @param clip
	 * @param transform
	 *            TODO
	 * @param contextX
	 *            TODO
	 * @param contextY
	 *            TODO
	 */
	public void draw(PageBox pageBox, Drawer drawer, Visitor visitor, Shape clip, AffineTransform transform,
			double contextX, double contextY, double x, double y);

	/**
	 * 内部のテキストを返します。
	 */
	public void getText(StringBuilder textBuff);

	public void textShape(PageBox pageBox, GeneralPath path, AffineTransform transform, double x, double d);
}