package jp.cssj.homare.style.box;

import jp.cssj.homare.style.box.content.BreakMode;

/**
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: IPageBreakableBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface IPageBreakableBox extends IBox {
	/**
	 * 内部で切断するか、前ページに残します。
	 */
	public static final byte FLAGS_FIRST = 1;

	/**
	 * 内部で切断するか、次ページに送ります。
	 */
	public static final byte FLAGS_LAST = 2;

	/**
	 * 必ず内部で切断します。
	 */
	public static final byte FLAGS_SPLIT = 4;

	/**
	 * 内部で切断するか、前ページに残します(テーブル行)。
	 */
	public static final byte FLAGS_FIRST_ROW = 8;

	/**
	 * マルチカラムでの改ページです。
	 */
	public static final byte FLAGS_COLUMN = 16;

	/**
	 * ボックスをページ方向に分割します。
	 * 
	 * @param pageLimit
	 *            ボックスの外辺から分割位置までの長さです。
	 * @param mode
	 * @param flags
	 *            TODO
	 * @return 分割せず前のページに残す場合はnullを返します。 全ての次のページに移動する場合はこのブロック自身を返します。
	 *         分割する場合は次のページに送るブロックボックスオブジェクトを返します。
	 */
	public IPageBreakableBox splitPageAxis(double pageLimit, BreakMode mode, byte flags);
}
