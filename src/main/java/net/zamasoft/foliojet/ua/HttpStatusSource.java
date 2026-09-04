package net.zamasoft.foliojet.ua;

/**
 * HTTP応答の状態を診断側へ渡すSourceの付加契約です。
 *
 * <p>
 * 本文や応答ヘッダは公開せず、取得失敗の段階表示に必要な状態コードだけを
 * 伝えます。まだ応答が無い、またはHTTP以外なら負の値を返します。
 * </p>
 */
public interface HttpStatusSource {
	public int httpStatus();
}
