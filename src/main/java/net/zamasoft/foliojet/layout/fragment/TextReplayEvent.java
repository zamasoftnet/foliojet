package net.zamasoft.foliojet.layout.fragment;

import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.TextControl;

/**
 * 整形済みテキストの正規化イベントです(M3b Phase 1 / C3)。
 *
 * <p>
 * WordHyphenator の出口(BuilderGlyphHandler の入力)で確定した
 * glyph/control/run 境界を値として運ぶ。open 段落の handoff は shaper の
 * 内部状態ではなくこの列で行う(codex 相談 2026-07-17 の設計)。
 * ControlQuad はインライン quad・制御を Phase 1 では参照のまま運ぶ
 * (値 recipe 化は Phase 3 — インライン継続の box 依存除去と同時)。
 * </p>
 */
public sealed interface TextReplayEvent {
	/** テキストランの開始。 */
	record RunStart(int charOffset, FontStyle fontStyle, FontMetrics fontMetrics) implements TextReplayEvent {
	}

	/** グリフ(chars は専有コピー)。 */
	record Glyph(int charOffset, char[] chars, int gid) implements TextReplayEvent {
	}

	/** テキストランの終了。 */
	record RunEnd() implements TextReplayEvent {
	}

	/** 制御・インライン quad(Phase 1: 参照運搬)。 */
	record ControlQuad(TextControl quad) implements TextReplayEvent {
	}

	/** 行の flush。 */
	record Flush() implements TextReplayEvent {
	}
}
