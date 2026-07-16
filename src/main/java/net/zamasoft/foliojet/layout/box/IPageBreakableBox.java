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
	 * ボックスをページ方向に分割します。
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
	 * @return 分割せず前のページに残す場合は null。全体を次のページに移動する場合は
	 *         このボックス自身(同一参照)。内部で切断した場合は次のページに送る
	 *         残余オブジェクト(このボックスは前ページ分のみを保持するよう変異済み)。
	 */
	public IPageBreakableBox splitPageAxis(double pageLimit, BreakMode mode, byte flags);

	/**
	 * splitPageAxis の型付きアダプタです(柱2c/M4-A1)。三義的返値
	 * (null/this/新オブジェクト)の解釈をこの一箇所に集約します。
	 * 実装の内部が SplitResult ネイティブになった段階(M4-A2以降)で
	 * こちらが正になり、splitPageAxis が廃止されます。
	 *
	 * @param pageLimit ボックスの外辺から分割位置までの長さ
	 * @param mode      分割モード
	 * @param flags     FLAGS_* のビット和
	 * @return 切断結果
	 */
	public default SplitResult split(double pageLimit, BreakMode mode, byte flags) {
		final IPageBreakableBox next = this.splitPageAxis(pageLimit, mode, flags);
		if (next == null) {
			return SplitResult.KEEP;
		}
		if (next == this) {
			return SplitResult.MOVE;
		}
		return new SplitResult.Split(next);
	}
}
