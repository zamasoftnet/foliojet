package net.zamasoft.foliojet.layout.segment;

/**
 * 置換要素の生成方法を表すrecipeです(2026-07-22新設、M6d-A3a、
 * 型契約のみ・未配線)。
 *
 * <p>
 * 元のlive box(旧{@code LayoutSource.Replaced}が保持していた
 * {@code AbstractReplacedBox}インスタンス)をそのまま捕捉する
 * {@code Supplier}にしてはならない——{@code AbstractReplacedBox}は
 * {@code width}/{@code height}/{@code offset}/{@code frame}という
 * 再生ごとの可変幾何を持つため、scratch測定やMIN/MAX呼び出しが
 * live配置を汚染しうる(codex設計相談で確認)。box自体の共有は不可、
 * 共有できるのは不変・再入可能と確認できた画像等のresourceだけ。
 * </p>
 *
 * <p>
 * この段階では{@link GenerationKind}だけを保持する骨格——frozen
 * {@code ReplacedParams}/{@code Pos}・共有可能resource handleの設計は
 * M6d-A3bで行う。
 * </p>
 */
public record ReplacedRecipe(GenerationKind generationKind) {
	/** 置換要素がどう生成されたか(codex設計相談で列挙された4種)。 */
	public enum GenerationKind {
		INLINE, FLOW, FLOAT, ABSOLUTE;
	}
}
