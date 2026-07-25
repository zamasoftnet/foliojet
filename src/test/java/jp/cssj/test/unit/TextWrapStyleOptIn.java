package jp.cssj.test.unit;

import java.io.File;

/**
 * Knuth-Plass行分割(CSS {@code text-wrap-style: pretty})を文書全体へ
 * オプトインさせるテスト用ヘルパです(2026-07-25、独自プロパティ
 * {@code text.line-breaker}廃止に伴い新設)。
 *
 * <p>
 * 「同じfixtureを貪欲法とK-Pの両方で組んで比較する」パリティ系テストは、
 * fixture自体にCSSを書けない(片方しか作れない)ため、著者スタイル
 * シートとして{@code input.default-stylesheet}から
 * {@code files/unittest/3200-line-breaker/text-wrap-pretty.css}を読ませる。
 * </p>
 */
public final class TextWrapStyleOptIn {
	/**
	 * {@code input.default-stylesheet}に渡す値です
	 * ({@code html { text-wrap-style: pretty }}のみを含むCSS)。
	 */
	public static final String PRETTY_STYLESHEET = new File(
			"files/unittest/3200-line-breaker/text-wrap-pretty.css").toURI().toString();

	private TextWrapStyleOptIn() {
		// インスタンス化しない
	}
}
