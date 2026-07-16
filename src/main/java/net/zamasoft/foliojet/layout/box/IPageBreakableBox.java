package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.fragment.SplitResult;

/**
 * ページ方向に分割可能なボックスです。
 *
 * <p>
 * <b>切断契約</b>: {@link #splitPageAxis(double, BreakMode, byte)} は
 * 「構築済みのボックスをその場で変異させ、次ページへ送る残余を返す」
 * プロトコルです。返値は三義的です(下記参照)。呼び出し側は返値の
 * 同一性(this)と null を判定して継続処理を行います。
 * テキストの継続情報は {@link net.zamasoft.foliojet.layout.box.content.BreakToken}
 * が担います(柱2で切断結果と統合予定)。
 * </p>
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
	 * ボックスをページ方向に分割します(M4-A3: SplitResult ネイティブ)。
	 *
	 * @param pageLimit ボックスの外辺(ページ方向始端)から分割位置までの長さです。
	 * @param mode      分割モード。自動改ページは AutoBreakMode、強制改ページは
	 *                  ForceBreakMode(TextBlockBox には渡されません)。
	 * @param flags     FLAGS_* のビット和。
	 *                  FLAGS_FIRST=このボックスはページ先頭にある(内部で切断するか、
	 *                  全体を前ページに残してよい)。
	 *                  FLAGS_LAST=ページ末尾にある(内部で切断するか、全体を次ページへ
	 *                  送ってよい。テーブルの行・行グループでは禁止)。
	 *                  FLAGS_SPLIT=必ず内部で切断する。
	 *                  FLAGS_FIRST_ROW=FLAGS_FIRST のテーブル行変種(ページ先頭行)。
	 *                  FLAGS_COLUMN=改ページではなく改段(マルチカラム)である。
	 * @return 切断結果。KEEP=分割せず前のページに残す。MOVE=全体を次のページに
	 *         移動する。Split(remainder)=内部で切断した(このボックスは前ページ分
	 *         のみを保持するよう変異済みで、remainder を次のページに送る)。
	 */
	public SplitResult split(double pageLimit, BreakMode mode, byte flags);
}
