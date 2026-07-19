package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.params.Params;

/**
 * {@link TableBuilder}がDocumentBuilder側のインライン文脈操作を呼び出す
 * ための狭いコールバックです(C4-C深化、2026-07-19)。
 *
 * <p>
 * DocumentBuilderの非公開実装詳細(インラインボックスの一時退避スタック・
 * StyledTextUnitizerのコンテナ入れ子カウンタ)を丸ごと公開する代わりに、
 * 表の構築で実際に必要な3操作だけをこの狭いインターフェース経由で公開する。
 * 実装はDocumentBuilder自身が持ち、呼び出し元(TableBuilder実装)は
 * この3操作が具体的に何をしているかを知る必要がない——「表に入る/出る際に
 * 必要なら呼ぶ」という契約だけを知っていればよい(tell-don't-ask)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public interface TableBuilderHost {
	/**
	 * 開いているインラインボックスを閉じて後で復元できるよう退避します。
	 */
	void closeInlines(Params params);

	/**
	 * 現在のコンテナのテキスト整形文脈を終えます。
	 */
	void endContainer();

	/**
	 * 新しいコンテナのテキスト整形文脈を開始します。
	 */
	void startContainer();
}
