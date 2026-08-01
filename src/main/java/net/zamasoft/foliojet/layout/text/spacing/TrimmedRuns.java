package net.zamasoft.foliojet.layout.text.spacing;

import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.RunCollector;
import net.zamasoft.pdfg2d.gc.text.TextImpl;

/**
 * 自己完結shape+run内約物詰めです(2026-08-01、テキスト系一本化)。
 *
 * <p>
 * ルビ単位・脚注ラベル・{@code leader()}パターンの3箇所に同型で複製
 * されていた匿名collectorを、pdfg2dの{@link RunCollector}(汎用の
 * shape→run収集)+このラッパー(foliojet固有のrun内約物詰め——
 * 和文詰めT1a/T1b)へ一本化した。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class TrimmedRuns {
	private TrimmedRuns() {
		// utility
	}

	/**
	 * 文字列を現在のスタイルで自己完結shapeし、run内約物詰めを適用して
	 * 返します。
	 *
	 * @param fontManager フォントマネージャ
	 * @param fontStyle   shapeに使うスタイル
	 * @param text        テキスト(空可)
	 * @param charOffset  先頭のソース文字オフセット(生成内容は{@code -1})
	 * @param trimOff     {@code text-spacing-trim: space-all}(詰め無効)か
	 * @return pack済みのrun列
	 */
	public static TextImpl[] shape(final FontManager fontManager, final FontStyle fontStyle, final String text,
			final int charOffset, final boolean trimOff) {
		final TextImpl[] runs = RunCollector.shape(fontManager, fontStyle, text, charOffset);
		if (!trimOff) {
			for (final TextImpl run : runs) {
				// 和文詰めT1a/T1b: font層から移管したrun内約物詰め
				JapaneseSpacingResolver.applyRunTrims(run);
			}
		}
		return runs;
	}
}
