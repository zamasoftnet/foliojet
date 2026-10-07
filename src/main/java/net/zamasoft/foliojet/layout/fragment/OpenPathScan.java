package net.zamasoft.foliojet.layout.fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;

/**
 * The result of scanning the collectable prefix of {@code flowStack}
 * (introduced 2026-07-21, M6b Phase B B2). {@link #capture} has no side effects:
 * callers are responsible for updating statistics counters, separating construction of
 * {@link OpenPathSnapshot} and execution of the scan from observation (statistics).
 *
 * @param snapshot classification results for all levels
 * @param approvedBoxes collectable ancestors from the start up to the first violating level
 * (equivalent to {@code chain} in {@link BreakPlan} ; excludes {@code root} )
 */
public record OpenPathScan(OpenPathSnapshot snapshot, List<AbstractContainerBox> approvedBoxes) {

	public OpenPathScan {
		approvedBoxes = List.copyOf(approvedBoxes);
	}

	/** Converts to the existing {@link BreakPlan} mechanism (the basis for unchanged behavior). */
	public BreakPlan toBreakPlan() {
		return new BreakPlan(this.approvedBoxes, this.snapshot.depth(), 0);
	}

	/**
	 * For PAGE continuations: classifies flowStack (including root, snapshotted at the break).
	 * Each level is classified once (a call to {@code ContinuationCapability
	 * .classify}), regardless of mode, but permission to collect it
	 * ({@code capability.supportsPageSplitThrough(mode)}) depends on mode
	 * (2026-07-21, M6b Phase B B3; forced page breaks do not collect {@code
	 * MULTICOL}; see {@link ContinuationCapability
	 * #supportsPageSplitThrough}).
	 *
	 * @param boxes boxes at each flowStack level (index 0 = root); must not be empty
	 * @param mode {@code BreakMode} for this break (automatic versus forced changes collection permission)
	 */
	public static OpenPathScan capture(final List<AbstractContainerBox> boxes,
			final net.zamasoft.foliojet.layout.box.content.BreakMode mode) {
		return scan(boxes, OpenPathSnapshot.AnchorKind.PAGE_ROOT, mode, ContinuationCapability::supportsPageSplitThrough);
	}

	/**
	 * For COLUMN continuations: classifies the open path relative to the multi-column owner
	 * (introduced 2026-07-21, M6b Phase B B4; used by {@code BreakableBuilder} as of 2026-07-25).
	 * Index 0 is the owner itself and does not call {@link ContinuationCapability#classify}
	 * (the owner itself is not classified as {@code MULTICOL} ; only further multi-column layouts inside it
	 * become {@code MULTICOL} ). Uses the owner's own writing direction for the anchor
	 * (ChatGPT Pro consultation; see the design consultation).
	 *
	 * @param boxes owner (index 0) plus its currently open descendants; must not be empty
	 * @param mode BreakMode for this break
	 */
	public static OpenPathScan captureColumn(final List<AbstractContainerBox> boxes,
			final net.zamasoft.foliojet.layout.box.content.BreakMode mode) {
		return scan(boxes, OpenPathSnapshot.AnchorKind.COLUMN_OWNER, mode,
				ContinuationCapability::supportsColumnSplitThrough);
	}

	private interface Admission {
		boolean supports(ContinuationCapability capability, net.zamasoft.foliojet.layout.box.content.BreakMode mode);
	}

	private static OpenPathScan scan(final List<AbstractContainerBox> boxes,
			final OpenPathSnapshot.AnchorKind anchorKind, final net.zamasoft.foliojet.layout.box.content.BreakMode mode,
			final Admission admission) {
		if (boxes.isEmpty()) {
			throw new IllegalArgumentException("open path is empty");
		}

		final WritingMode anchorFlow = boxes.get(0).getBlockParams().flow;
		final WritingModeVariant anchorWritingModeVariant = boxes.get(0).getBlockParams().writingModeVariant;
		final List<OpenPathSnapshot.OpenLevelDescriptor> descriptors = new ArrayList<>(boxes.size());
		final List<AbstractContainerBox> approved = new ArrayList<>();
		OpenPathSnapshot.CapabilityBarrier firstBarrier = null;

		for (int i = 0; i < boxes.size(); ++i) {
			final AbstractContainerBox box = boxes.get(i);

			final OpenPathSnapshot.OpenLevelRole role;
			if (i == 0) {
				role = new OpenPathSnapshot.OpenLevelRole.Anchor(anchorKind);
			} else {
				final ContinuationCapability capability = ContinuationCapability.classify(box, anchorFlow,
						anchorWritingModeVariant);
				role = new OpenPathSnapshot.OpenLevelRole.Ancestor(capability);
				if (firstBarrier == null) {
					if (admission.supports(capability, mode)) {
						approved.add(box);
					} else {
						firstBarrier = new OpenPathSnapshot.CapabilityBarrier(i, capability);
					}
				}
			}

			descriptors.add(new OpenPathSnapshot.OpenLevelDescriptor(i, box.getClass(),
					box.getBlockParams().flow, box.getBlockParams().writingModeVariant, box.getColumnCount(),
					box.getSourceAnchor(), role));
		}

		final OpenPathSnapshot snapshot = new OpenPathSnapshot(anchorFlow, anchorWritingModeVariant, descriptors,
				Optional.ofNullable(firstBarrier));
		return new OpenPathScan(snapshot, approved);
	}
}
