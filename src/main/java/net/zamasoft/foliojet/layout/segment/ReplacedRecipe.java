package net.zamasoft.foliojet.layout.segment;

/**
 * A recipe describing how to create a replaced element (introduced on 2026-07-22 in M6d-A3a as a
 * skeleton defining only the type contract; the contents were designed that day for M6d-A replaced elements).
 *
 * <p>
 * Do not use a {@code Supplier} that captures the original live box (the {@code AbstractReplacedBox}
 * instance formerly held by {@code LayoutSource.Replaced}) directly. {@code AbstractReplacedBox} holds
 * geometry that changes on each replay: {@code width}/{@code height}/{@code offset}/{@code frame}.
 * Scratch measurement or MIN/MAX calls could therefore corrupt live placement (confirmed in the codex
 * design consultation). The box itself cannot be shared; only resources such as images confirmed to be
 * immutable and reentrant may be shared.
 * </p>
 *
 * <p>
 * As with {@link BoxRecipe}, this uses variants of a sealed interface instead of a single record because
 * each creation kind requires a different {@code Pos} template type. The four {@code AbstractReplacedBox}
 * implementations, {@code InlineReplacedBox}/{@code FlowReplacedBox}/
 * {@code FloatReplacedBox}/{@code AbsoluteReplacedBox}, use
 * {@code InlinePos}/{@code FlowPos}/{@code FloatPos}/{@code AbsolutePos}, respectively
 * (verified in the existing code). All variants share {@link ReplacedParamsTemplate} for {@code params}
 * ({@code ReplacedParams} has the same type regardless of the creation kind).
 * </p>
 *
 * <p>
 * {@link ReplacedParamsTemplate#freeze} became a total function in E-6 increment 3b-6
 * ({@code ReplacedBoxImage} freezes an independent copy made by {@code duplicate()}).
 * {@link #freeze} returns empty only for unknown {@code AbstractReplacedBox} subclasses
 * (structurally unreachable with the four existing implementations; zero occurrences measured in the corpus).
 * In that case, the caller ({@code StyleBuilder.addReplacedBox}) must fail closed and record a
 * non-replayable marker ({@code LayoutSource.Opaque}).
 * </p>
 */
public sealed interface ReplacedRecipe {
	GenerationKind generationKind();

	ReplacedParamsTemplate params();

	/**
	 * Builds a recipe from a live box (E-6 increment 3b-3 moved the conversion logic from
	 * {@code LayoutSourceEventConverter}; freezing occurs when recording in {@code StyleBuilder.addReplacedBox}).
	 * E-6 increment 3b-6: since {@link ReplacedParamsTemplate#freeze} became a total function
	 * (duplicate-based freezing of {@code ReplacedBoxImage}), only unknown {@code AbstractReplacedBox}
	 * subclasses return empty (this does not occur with the four existing implementations).
	 * The caller must fail closed and fall back to a non-replayable marker ({@code LayoutSource.Opaque}).
	 */
	static java.util.Optional<ReplacedRecipe> freeze(final net.zamasoft.foliojet.layout.box.AbstractReplacedBox box) {
		final ReplacedParamsTemplate params = ReplacedParamsTemplate.freeze(box.getReplacedParams());
		// The four implementations (InlineReplacedBox/FlowReplacedBox/FloatReplacedBox/
		// AbsoluteReplacedBox) use InlinePos/FlowPos/FloatPos/
		// AbsolutePos, respectively (see the class Javadoc).
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

	/** How the replaced element was created (four kinds listed in the codex design consultation). */
	enum GenerationKind {
		INLINE, FLOW, FLOAT, ABSOLUTE;
	}

	/** An inline replaced element ({@code InlineReplacedBox}); uses {@code InlinePos}. */
	record Inline(ReplacedParamsTemplate params, InlinePosTemplate pos) implements ReplacedRecipe {
		public GenerationKind generationKind() {
			return GenerationKind.INLINE;
		}
	}

	/** A normal-flow replaced element ({@code FlowReplacedBox}); uses {@code FlowPos}. */
	record Flow(ReplacedParamsTemplate params, FlowPosTemplate pos) implements ReplacedRecipe {
		public GenerationKind generationKind() {
			return GenerationKind.FLOW;
		}
	}

	/** A floating replaced element ({@code FloatReplacedBox}); uses {@code FloatPos}. */
	record Float(ReplacedParamsTemplate params, FloatPosTemplate pos) implements ReplacedRecipe {
		public GenerationKind generationKind() {
			return GenerationKind.FLOAT;
		}
	}

	/** An absolutely positioned replaced element ({@code AbsoluteReplacedBox}); uses {@code AbsolutePos}. */
	record Absolute(ReplacedParamsTemplate params, AbsolutePosTemplate pos) implements ReplacedRecipe {
		public GenerationKind generationKind() {
			return GenerationKind.ABSOLUTE;
		}
	}
}
