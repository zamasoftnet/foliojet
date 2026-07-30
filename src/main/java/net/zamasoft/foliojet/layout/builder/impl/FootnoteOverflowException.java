package net.zamasoft.foliojet.layout.builder.impl;

/**
 * 脚注が空ページの最大脚注領域にも収まらないことを示す型付きレイアウト
 * 失敗です(脚注F3、2026-07-31——初期サブセットの合意事項:
 * consult-codex-2026-07-31-footnote.txt §2)。
 *
 * <p>
 * 収まらない脚注を次ページへ送り続けて無限にページを生むことを禁止する
 * ためのガード。ページ内に他の脚注が既にあって入らないだけの場合は、
 * F4(FIFO次ページ送り)の導入後は送りで解決され、この例外は
 * 「どのページにも入らない」脚注に対してだけ残る。
 * </p>
 */
public class FootnoteOverflowException extends RuntimeException {
	private static final long serialVersionUID = 0L;

	public FootnoteOverflowException(final String message) {
		super(message);
	}
}
