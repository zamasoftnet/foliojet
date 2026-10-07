package net.zamasoft.foliojet.layout.fragment;

import java.util.List;
import java.util.Optional;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.box.params.WritingModeVariant;

/**
 * A snapshot of {@code flowStack} at the break (the open ancestor chain, or the path relative to the
 * owner of a COLUMN continuation), observed independently of mutable box identity
 * (introduced 2026-07-21, M6b Phase B B2; generalized to {@link OpenLevelRole.Anchor} in B4 to represent
 * COLUMN owners as well as PAGE roots). Classification by {@link ContinuationCapability#classify}
 * occurs only once here (no reclassification after the break or resume);
 * {@link ContinuationValidator} and execution paths carry this result unchanged
 * (confirmed in the ChatGPT Pro consultation; design consultation).
 *
 * @param anchorFlow writing direction of index 0 (anchor): document root for PAGE, multi-column owner for
 * COLUMN
 * @param anchorWritingModeVariant glyph rotation variant of index 0 (anchor)
 * @param levels levels of the open path (index 0 = anchor)
 * @param firstBarrier first level found uncollectable (empty if all levels are collectable)
 */
public record OpenPathSnapshot(WritingMode anchorFlow, WritingModeVariant anchorWritingModeVariant,
		List<OpenLevelDescriptor> levels, Optional<CapabilityBarrier> firstBarrier) {

	public OpenPathSnapshot {
		levels = List.copyOf(levels);
	}

	/** The existing constructor for ordinary writing-mode. */
	public OpenPathSnapshot(final WritingMode anchorFlow, final List<OpenLevelDescriptor> levels,
			final Optional<CapabilityBarrier> firstBarrier) {
		this(anchorFlow, WritingModeVariant.NORMAL, levels, firstBarrier);
	}

	/** Open-path depth (total level count, including the anchor). */
	public int depth() {
		return this.levels.size();
	}

	/** The position and reason of the first level found uncollectable. */
	public record CapabilityBarrier(int openPathIndex, ContinuationCapability reason) {
	}

	/** The anchor kind (PAGE document root or COLUMN multi-column owner). */
	public enum AnchorKind {
		PAGE_ROOT, COLUMN_OWNER
	}

	/** Indicates whether a level is an anchor (root/owner) or a classified ancestor. */
	public sealed interface OpenLevelRole {
		record Anchor(AnchorKind kind) implements OpenLevelRole {
		}

		record Ancestor(ContinuationCapability capability) implements OpenLevelRole {
		}
	}

	/**
	 * Describes one flowStack level independently of the mutable box.
	 *
	 * @param index position on flowStack (0 = root)
	 * @param boxClass runtime class (used to identify subtypes such as multi-column layout)
	 * @param flow writing direction
	 * @param writingModeVariant glyph rotation variant
	 * @param columnCount number of columns
	 * @param sourceAnchor source anchor before split (diagnostic only; not the primary matching key,
	 * because the new fragment after resume does not inherit the old anchor)
	 * @param role root or classified ancestor
	 */
	public record OpenLevelDescriptor(int index, Class<? extends AbstractContainerBox> boxClass,
			WritingMode flow, WritingModeVariant writingModeVariant, int columnCount, long sourceAnchor,
			OpenLevelRole role) {

		/** The existing constructor for ordinary writing-mode. */
		public OpenLevelDescriptor(final int index, final Class<? extends AbstractContainerBox> boxClass,
				final WritingMode flow, final int columnCount, final long sourceAnchor, final OpenLevelRole role) {
			this(index, boxClass, flow, WritingModeVariant.NORMAL, columnCount, sourceAnchor, role);
		}

		/** Signature for matching the actual fragment on resume. */
		public FragmentSignature fragmentSignature() {
			return new FragmentSignature(this.boxClass, this.flow, this.writingModeVariant, this.columnCount);
		}
	}

	/**
	 * The matching key for fragment identity (class/flow/variant/columnCount; the runtime class also
	 * distinguishes subtypes such as multi-column layout). Excludes {@code sourceAnchor}
	 * because new fragments do not inherit the old anchor.
	 */
	public record FragmentSignature(Class<? extends AbstractContainerBox> boxClass,
			WritingMode flow, WritingModeVariant writingModeVariant, int columnCount) {

		/** The existing constructor for ordinary writing-mode. */
		public FragmentSignature(final Class<? extends AbstractContainerBox> boxClass, final WritingMode flow,
				final int columnCount) {
			this(boxClass, flow, WritingModeVariant.NORMAL, columnCount);
		}

		public static FragmentSignature from(final AbstractContainerBox box) {
			return new FragmentSignature(box.getClass(), box.getBlockParams().flow,
					box.getBlockParams().writingModeVariant, box.getColumnCount());
		}
	}
}
