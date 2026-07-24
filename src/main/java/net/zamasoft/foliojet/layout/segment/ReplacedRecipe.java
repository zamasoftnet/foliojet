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
 * {@link ReplacedParamsTemplate#freeze}はE-6増分3b-6で総関数化された
 * ({@code ReplacedBoxImage}は{@code duplicate()}の独立複製を凍結)。
 * {@link #freeze}が空を返すのは未知の{@code AbstractReplacedBox}
 * サブクラスのみ(現存4実装では構造的に到達不能・コーパス実測ゼロ)で、
 * その場合呼び出し側({@code StyleBuilder.addReplacedBox})はfail closed
 * でreplay不能マーカー({@code LayoutSource.Opaque})を記録すること。
 * </p>
 */
public sealed interface ReplacedRecipe {
	GenerationKind generationKind();

	/**
	 * live boxからrecipeを組み立てます(E-6増分3b-3で
	 * {@code LayoutSourceEventConverter}の変換ロジックを移設——記録時
	 * ({@code StyleBuilder.addReplacedBox})にfreezeする)。
	 * E-6増分3b-6: {@link ReplacedParamsTemplate#freeze}の総関数化
	 * ({@code ReplacedBoxImage}のduplicateベースfreeze)により、空を
	 * 返すのは未知の{@code AbstractReplacedBox}サブクラスのみ(現存
	 * 4実装では発生しない)。呼び出し側はfail closedでreplay不能
	 * マーカー({@code LayoutSource.Opaque})へfall backすること。
	 */
	static java.util.Optional<ReplacedRecipe> freeze(final net.zamasoft.foliojet.layout.box.AbstractReplacedBox box) {
		final ReplacedParamsTemplate params = ReplacedParamsTemplate.freeze(box.getReplacedParams());
		// 4実装(InlineReplacedBox/FlowReplacedBox/FloatReplacedBox/
		// AbsoluteReplacedBox)がそれぞれInlinePos/FlowPos/FloatPos/
		// AbsolutePosを使う(クラスjavadoc参照)
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox inline) {
			return java.util.Optional.of(new Inline(params, InlinePosTemplate.freeze(inline.getInlinePos())));
		}
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.FlowReplacedBox flow) {
			return java.util.Optional.of(new Flow(params, FlowPosTemplate.freeze(flow.getFlowPos())));
		}
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.FloatReplacedBox floatBox) {
			return java.util.Optional.of(new Float(params, FloatPosTemplate.freeze(floatBox.getFloatPos())));
		}
		if (box instanceof net.zamasoft.foliojet.layout.box.impl.AbsoluteReplacedBox absolute) {
			return java.util.Optional
					.of(new Absolute(params, AbsolutePosTemplate.freeze(absolute.getAbsolutePos())));
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
