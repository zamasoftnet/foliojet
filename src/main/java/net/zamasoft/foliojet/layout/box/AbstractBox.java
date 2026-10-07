package net.zamasoft.foliojet.layout.box;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.params.Offset;

public abstract class AbstractBox implements IBox {
	/**
	 * The LayoutSource event ID that produced this content (SourceAnchor; P0: separates provenance
	 * from style params, as noted in external review). Assigned once during recording and immutable
	 * thereafter. Fragments and unrecorded content retain -1. Since recipe-built fragments have no
	 * anchor from the outset, the old params.sourceEventId protocol of invalidating by writing -1
	 * is unnecessary. The driver reattaches anchors to replay instances from event IDs
	 * (SourceReplayer.drive).
	 */
	private long sourceAnchor = -1;
	private long assignmentAnchor = -1;

	@Override
	public final long getAssignmentAnchor() {
		return this.assignmentAnchor >= 0 ? this.assignmentAnchor : this.sourceAnchor;
	}

	/** Inherits only the placement assignment source, leaving original source-replay eligibility untouched. */
	public final void setAssignmentAnchor(final long anchor) {
		this.assignmentAnchor = anchor;
	}

	/**
	 * Whether this box has already been split and passed some content to a continuation fragment
	 * (added on 2026-07-28). {@link #getSourceAnchor()} is the start event of the element that
	 * created this box and <b>remains on the preceding fragment even after a split</b>.
	 * Since recipe-built continuation fragments have no anchor, splitting invalidated
	 * <b>only the trailing half</b>. Replaying the preceding fragment from source rebuilds
	 * <b>the entire element</b>, including the remainder held by the continuation fragment,
	 * duplicating content when the continuation resumes.
	 */
	private boolean fragmented = false;

	/**
	 * Whether to prohibit reconstruction from the source range (2026-08-23). Independent of
	 * fragmented status, which also controls semantics such as repeated table footers and is not reused.
	 * Set when the entire table MOVEs: its lexical source range can contain content already finalized
	 * **outside** the table by HTML foster parenting (bare text directly under the table). Replaying
	 * that range after MOVE duplicates finalized content (found while minimizing v2 generator seed 30).
	 */
	private boolean sourceReplayInvalidated = false;

	public final long getSourceAnchor() {
		return this.sourceAnchor;
	}

	public final void setSourceAnchor(final long id) {
		assert this.sourceAnchor == -1 : "アンカーは付与後不変: " + this.sourceAnchor + " -> " + id;
		this.sourceAnchor = id;
	}

	public final boolean isSourceReplayable() {
		if (this instanceof net.zamasoft.foliojet.layout.box.impl.TableBox table && table.isIncomplete()) {
			// An unfinished fragment's anchor points to the whole table, so exclude it from whole-table source replay.
			return false;
		}
		return this.sourceAnchor >= 0 && !this.fragmented && !this.sourceReplayInvalidated;
	}

	/** Disables only source replay, so already-built content can be carried intact. */
	public final void invalidateSourceReplay() {
		this.sourceReplayInvalidated = true;
	}

	public final void markFragmented() {
		this.fragmented = true;
	}

	/**
	 * Returns whether this box has been split (the preceding fragment) (tagged-PDF defect ② fix,
	 * 2026-07-30). Used by the table-footer rule that repeats footers on split fragments.
	 */
	public final boolean isFragmented() {
		return this.fragmented;
	}

	/**
	 * The engine's internal horizontal compression ratio (e.g., fitting tate-chu-yoko into 1 em; default 1=none).
	 * Separate from the author's CSS {@code transform}; this applies on the inside.
	 */
	protected double internalScaleX() {
		return 1;
	}

	/**
	 * The {@code FlowContainer} holding this box as content (2026-08-29).
	 * Used to invalidate the {@code hasNonDecorationContent} memo only for ancestors of changed boxes.
	 * Reassigned whenever the holding container changes.
	 */
	private net.zamasoft.foliojet.layout.box.content.FlowContainer contentParent;

	public final net.zamasoft.foliojet.layout.box.content.FlowContainer getContentParent() {
		return this.contentParent;
	}

	public final void setContentParent(final net.zamasoft.foliojet.layout.box.content.FlowContainer parent) {
		this.contentParent = parent;
	}

	/** The physical X offset that centers content after internal compression (default 0). */
	protected double internalOffsetX() {
		return 0;
	}

	/**
	 * Margins used to obtain the reference box (border box) for {@code transform-origin}
	 * and percentage {@code translate()}. Null for boxes without margins, where (x, y)
	 * and getWidth()/getHeight() directly define the reference box.
	 */
	protected net.zamasoft.foliojet.layout.part.AbsoluteInsets transformReferenceMargin() {
		return null;
	}

	protected final AffineTransform transform(AffineTransform transform, double x, double y) {
		AffineTransform ct = this.getParams().transform;
		final double txRatio = this.getParams().transformTxRatio;
		final double tyRatio = this.getParams().transformTyRatio;
		final double txRatioH = this.getParams().transformTxRatioH;
		final double tyRatioW = this.getParams().transformTyRatioW;
		final double isx = this.internalScaleX();
		final double iox = this.internalOffsetX();
		final double zoom = this.getParams().zoom;
		if (ct.isIdentity() && txRatio == 0 && tyRatio == 0 && txRatioH == 0 && tyRatioW == 0 && isx == 1
				&& iox == 0 && zoom == 1) {
			return transform;
		}
		transform = new AffineTransform(transform);
		// The reference box for transform-origin and translate() percentages is the border box
		// (css-transforms-1 §3: reference box is border-box). (x, y) is the margin-box
		// origin and getWidth()/getHeight() include margins, so remove margins
		// to obtain the reference box (2026-09-03; boxes with margins had their origin incorrectly
		// based on the margin box, found in filter-layer placement tests).
		final net.zamasoft.foliojet.layout.part.AbsoluteInsets margin = this.transformReferenceMargin();
		final double bx = margin == null ? x : x + margin.left;
		final double by = margin == null ? y : y + margin.top;
		final double bw = margin == null ? this.getWidth() : this.getWidth() - margin.getFrameWidth();
		final double bh = margin == null ? this.getHeight() : this.getHeight() - margin.getFrameHeight();
		if (zoom != 1) {
			// zoom (2026-08-29) scales around the border box's top-left corner, outside the author's
			// transform (Zoom's Javadoc: an approximation that does not affect layout).
			transform.translate(x, y);
			transform.scale(zoom, zoom);
			transform.translate(-x, -y);
		}
		double ax = bx;
		double ay = by;
		Offset offset = this.getParams().transformOrigin;
		switch (offset.getXType()) {
		case ABSOLUTE:
			ax += offset.getX();
			break;
		case RELATIVE:
			ax += bw * offset.getX();
			break;
		case MIXED:
			ax += offset.getX() + bw * offset.getXRatio();
			break;
		default:
			throw new IllegalStateException();
		}
		// Note: the switch below tests offset.getXType(), but its body calculates the Y component
		// (an existing code inconsistency outside the scope of adding MIXED;
		// retain the existing condition without changing behavior).
		switch (offset.getXType()) {
		case ABSOLUTE:
			ay += offset.getY();
			break;
		case RELATIVE:
			ay += bh * offset.getY();
			break;
		case MIXED:
			ay += offset.getY() + bh * offset.getYRatio();
			break;
		default:
			throw new IllegalStateException();
		}

		transform.translate(ax, ay);
		if (txRatio != 0 || tyRatio != 0 || txRatioH != 0 || tyRatioW != 0) {
			// **Resolve percentage translations here** (2026-08-03), relative to this box's own
			// dimensions. Their positions within the function sequence are linearly decomposed
			// into coefficients during parsing (TransformValue, 2026-08-29). The result is a single
			// translation added **outside** the composed matrix (before concatenate).
			final double w = bw;
			final double h = bh;
			transform.translate(w * txRatio + h * txRatioH, w * tyRatioW + h * tyRatio);
		}
		transform.concatenate(ct);
		transform.translate(-ax, -ay);
		if (isx != 1 || iox != 0) {
			// Apply internal compression relative to the box's left edge (x). Content is laid out
			// at its natural width, so this fits it exactly into [x, x+cell width].
			transform.translate(x + iox, 0);
			transform.scale(isx, 1);
			transform.translate(-x, 0);
		}
		return transform;
	}

	public String toString() {
		return super.toString() + "[width=" + this.getWidth() + ",height=" + this.getHeight() + ",params="
				+ this.getParams() + ",pos=" + this.getPos() + "]";
	}
}
