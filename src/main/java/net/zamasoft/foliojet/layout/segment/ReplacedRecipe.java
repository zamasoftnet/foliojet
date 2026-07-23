package net.zamasoft.foliojet.layout.segment;

/**
 * 置換要素の生成方法を表すrecipeです(2026-07-22新設・M6d-A3aで型契約の
 * みの骨格として導入、同日中にM6d-A Replaced要素対応で内容を実設計)。
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
 * {@link BoxRecipe}と同じ理由(生成種別ごとに必要な{@code Pos}
 * テンプレートの型が異なる——{@code AbstractReplacedBox}の4実装
 * {@code InlineReplacedBox}/{@code FlowReplacedBox}/
 * {@code FloatReplacedBox}/{@code AbsoluteReplacedBox}がそれぞれ
 * {@code InlinePos}/{@code FlowPos}/{@code FloatPos}/{@code AbsolutePos}
 * を使う、既存コード確認済み)で、単一recordではなくsealed interfaceの
 * variantとして表現する。{@code params}は全variant共通で
 * {@link ReplacedParamsTemplate}({@code ReplacedParams}は生成種別に
 * よらず共通の型)。
 * </p>
 *
 * <p>
 * {@link ReplacedParamsTemplate#freeze}は不安全な{@code image}
 * ({@link net.zamasoft.foliojet.layout.box.content.ReplacedBoxImage}
 * 実装)を検出すると{@code Optional.empty()}を返す——その場合この
 * recipeは構築できず、呼び出し側(変換アダプタ配線時)は
 * {@link SegmentEvent.Barrier}にfall backすること。
 * </p>
 */
public sealed interface ReplacedRecipe {
	GenerationKind generationKind();

	/** 置換要素がどう生成されたか(codex設計相談で列挙された4種)。 */
	enum GenerationKind {
		INLINE, FLOW, FLOAT, ABSOLUTE;
	}

	/** インラインの置換要素({@code InlineReplacedBox})——{@code InlinePos}を使う。 */
	record Inline(ReplacedParamsTemplate params, InlinePosTemplate pos) implements ReplacedRecipe {
		public GenerationKind generationKind() {
			return GenerationKind.INLINE;
		}
	}

	/** 通常フローの置換要素({@code FlowReplacedBox})——{@code FlowPos}を使う。 */
	record Flow(ReplacedParamsTemplate params, FlowPosTemplate pos) implements ReplacedRecipe {
		public GenerationKind generationKind() {
			return GenerationKind.FLOW;
		}
	}

	/** 浮動の置換要素({@code FloatReplacedBox})——{@code FloatPos}を使う。 */
	record Float(ReplacedParamsTemplate params, FloatPosTemplate pos) implements ReplacedRecipe {
		public GenerationKind generationKind() {
			return GenerationKind.FLOAT;
		}
	}

	/** 絶対配置の置換要素({@code AbsoluteReplacedBox})——{@code AbsolutePos}を使う。 */
	record Absolute(ReplacedParamsTemplate params, AbsolutePosTemplate pos) implements ReplacedRecipe {
		public GenerationKind generationKind() {
			return GenerationKind.ABSOLUTE;
		}
	}
}
