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

	/**
	 * live boxからrecipeを組み立てます(E-6増分3b-3で
	 * {@code LayoutSourceEventConverter}の変換ロジックを移設——記録時
	 * ({@code StyleBuilder.addReplacedBox})と変換アダプタが同じ分類を
	 * 共有するため)。{@link ReplacedParamsTemplate#freeze}が不安全な
	 * {@code image}({@code ReplacedBoxImage}実装)を検出した場合、
	 * または未知の{@code AbstractReplacedBox}サブクラスの場合は
	 * fail closedで{@code Optional.empty()}を返す——呼び出し側は
	 * live box保持({@code LayoutSource.ReplacedLive}/
	 * {@link SegmentEvent.Barrier})へfall backすること。
	 */
	static java.util.Optional<ReplacedRecipe> freeze(final net.zamasoft.foliojet.layout.box.AbstractReplacedBox box) {
		final java.util.Optional<ReplacedParamsTemplate> params = ReplacedParamsTemplate
				.freeze(box.getReplacedParams());
		if (params.isEmpty()) {
			return java.util.Optional.empty();
		}
		// 4実装(InlineReplacedBox/FlowReplacedBox/FloatReplacedBox/
		// AbsoluteReplacedBox)がそれぞれInlinePos/FlowPos/FloatPos/
		// AbsolutePosを使う(クラスjavadoc参照)
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox inline) {
			return java.util.Optional
					.of(new Inline(params.get(), InlinePosTemplate.freeze(inline.getInlinePos())));
		}
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.FlowReplacedBox flow) {
			return java.util.Optional.of(new Flow(params.get(), FlowPosTemplate.freeze(flow.getFlowPos())));
		}
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.FloatReplacedBox floatBox) {
			return java.util.Optional
					.of(new Float(params.get(), FloatPosTemplate.freeze(floatBox.getFloatPos())));
		}
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.AbsoluteReplacedBox absolute) {
			return java.util.Optional
					.of(new Absolute(params.get(), AbsolutePosTemplate.freeze(absolute.getAbsolutePos())));
		}
		return java.util.Optional.empty();
	}

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
