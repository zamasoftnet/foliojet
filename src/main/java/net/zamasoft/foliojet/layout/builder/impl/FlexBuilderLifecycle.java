package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.impl.FlexBox;
import net.zamasoft.foliojet.layout.box.params.FlexDirection;
import net.zamasoft.foliojet.layout.box.params.FlexParams;
import net.zamasoft.foliojet.layout.box.params.FlexWrap;
import net.zamasoft.foliojet.layout.builder.Builder;

/**
 * Entry point for the Flex construction lifecycle (Flex F1d, the same thin structure as
 * {@code GridBuilderLifecycle}). Ineligible cases fall back to single-column flow (F0).
 */
public final class FlexBuilderLifecycle {
	private FlexBuilderLifecycle() {
		// Static utility
	}

	/**
	 * Checks whether Flex row placement applies (consult-codex-2026-08-02-
	 * flexbox.txt, "phased fallback rules", F1: TB+row+nowrap only).
	 * Eligible hosts include BlockBuilder and TwoPass (F1f: record the execution plan as
	 * a FlexEvent and bind after width resolution; column = F4, reverse/wrap = F2/F5,
	 * vertical writing = F6).
	 */
	public static boolean eligible(final FlexBox flexBox, final Builder builder) {
		final FlexParams params = flexBox.getFlexParams();
		if (!params.flexDirection.isRow()) {
			// F4b/F4d: column takes a definite main axis (absolute length). Since 2026-10-08 a column whose main size
			// is indefinite also comes here when only the whole container can place it (stage 2 of
			// docs/design/column-flex-indefinite-main-design.md); the rest stays in the streamed flow (F0), where an
			// app shell's body flex keeps its contents unretained. wrap additionally requires an absolute cross
			// (line-axis) length (F4c recommendation: eligibility requires sizing the column width on the cross axis
			// in advance).
			if (!flexBox.hasDefinitePageSize() && !retainsIndefiniteColumn(params)) {
				return false;
			}
			if (params.flexWrap != FlexWrap.NOWRAP
					&& params.size.getLineType(params.flow) != net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE
					&& !(builder instanceof BlockBuilder block && params.intrinsicLine == null
							&& lineKnownBeforeContent(block, params.flow))) {
				return false;
			}
			return builder instanceof BlockBuilder || builder instanceof TwoPassBlockBuilder;
		}
		final net.zamasoft.foliojet.layout.box.params.LengthType crossType = params.flow.isVertical()
				? params.size.getWidthType()
				: params.size.getHeightType();
		if (params.flexWrap.isWrap()
				&& crossType != net.zamasoft.foliojet.layout.box.params.LengthType.AUTO
				&& crossType != net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
			// F3d: a definite cross size for wrap is allowed only for absolute lengths (% cross sizes
			// remain outside the subset).
			return false;
		}
		return builder instanceof BlockBuilder || builder instanceof TwoPassBlockBuilder;
	}

	/**
	 * Whether a column flex with an indefinite main size is retained and placed as a whole (2026-10-08), judged
	 * from the container's own declarations at its start: {@code column-reverse} (the last item comes first) or an
	 * absolute max main size (items shrink or wrap at it). Their contents are retained up to
	 * {@code processing.retained-text-limit}.
	 *
	 * <p>
	 * {@code align-items} other than stretch no longer retains the container (2026-10-09): every page of a retained
	 * container relaid all of its remaining content, so a centered column holding a whole body took time and retained
	 * text growing with pages times content (2000 paragraphs exceeded the 16 MiB limit). Streamed columns align items
	 * with a width and images instead ({@code DocumentBuilder.alignInStreamedColumn},
	 * {@code BlockBuilder.streamedColumnAlign}); items whose width is auto fill the column, since taking their
	 * fit-content width would measure each one as a single box that does not break across pages. {@code order}
	 * stays ignored there.
	 * </p>
	 */
	static boolean retainsIndefiniteColumn(final FlexParams params) {
		if (params.flexDirection.isReverse()) {
			return true;
		}
		return params.maxSize.getPageType(params.flow) == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE;
	}

	/**
	 * Whether the line size of a container just started in a block builder is known before its content (2026-10-11): the
	 * boxes it is in, up to one with an absolute line size or the builder's root, are blocks of the same writing mode
	 * that do not size themselves by their content. A flex or grid item, a table cell, a box with an intrinsic size
	 * keyword (width: max-content, min-width: min-content, ...), or an inline-block, float or absolute box of auto
	 * line size sizes it by its measured content, a wrapping column measured as one column (the content of such a box
	 * is replayed into a block builder, too).
	 */
	private static boolean lineKnownBeforeContent(final BlockBuilder builder,
			final net.zamasoft.foliojet.layout.box.params.WritingMode flow) {
		for (int i = builder.getFlowCount() - 2; i >= 0; --i) {
			final net.zamasoft.foliojet.layout.box.AbstractContainerBox box = builder.getFlow(i).box;
			if (box instanceof net.zamasoft.foliojet.layout.box.impl.FlexItemBox
					|| box instanceof net.zamasoft.foliojet.layout.box.impl.GridItemBox
					|| box instanceof net.zamasoft.foliojet.layout.box.impl.TableCellBox) {
				return false;
			}
			final net.zamasoft.foliojet.layout.box.params.BlockParams p = box.getBlockParams();
			if (p.flow.isVertical() != flow.isVertical() || p.intrinsicLine != null || p.intrinsicMinLine != null
					|| p.intrinsicMaxLine != null) {
				return false;
			}
			final net.zamasoft.foliojet.layout.box.params.LengthType type = p.size.getLineType(flow);
			if (type == net.zamasoft.foliojet.layout.box.params.LengthType.ABSOLUTE) {
				return true;
			}
			if (type == net.zamasoft.foliojet.layout.box.params.LengthType.AUTO
					&& (box instanceof net.zamasoft.foliojet.layout.box.impl.InlineBlockBox
							|| box instanceof net.zamasoft.foliojet.layout.box.impl.FloatBlockBox
							|| box instanceof net.zamasoft.foliojet.layout.box.impl.AbsoluteBlockBox)) {
				return false;
			}
		}
		return true;
	}

	/** Starts a FlexBuilder (eligibility must already be checked). */
	public static FlexBuilder start(final Builder builder, final FlexBox flexBox) {
		return new FlexBuilder(builder, flexBox);
	}
}
