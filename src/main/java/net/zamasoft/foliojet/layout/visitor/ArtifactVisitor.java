package net.zamasoft.foliojet.layout.visitor;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.draw.Drawer;

/**
 * Side-effect-free {@link Visitor} (added 2026-07-25, rescue splitting, increment 5;
 * design consultation §3).
 *
 * <p>
 * Passed instead of the real Visitor only while drawing <b>continuation fragments</b>
 * ({@code offset > 0}) of a rescue split. A continuation fragment "looks like content but
 * semantically belongs to the first fragment," so <b>document-level side effects</b>
 * such as links, form controls, page references, {@code string-set}, and bookmarks
 * must not be emitted twice.
 * </p>
 *
 * <p>
 * {@code ua.impl.NopVisitor} cannot be used: it extends {@code AbstractVisitor},
 * whose {@code visitBox()} processes exactly these side effects (recommendation §3).
 * This class truly does nothing.
 * </p>
 *
 * <p>
 * {@link #startPage()} and {@link #endPage()} are not called while drawing rescue fragments
 * (page start/end are per-page operations), but are no-ops by contract.
 * </p>
 */
public final class ArtifactVisitor implements Visitor {

	/** Can be shared because it has no state. */
	public static final ArtifactVisitor INSTANCE = new ArtifactVisitor();

	private ArtifactVisitor() {
		// singleton
	}

	public void startPage() {
		// Do nothing
	}

	public void visitBox(final AffineTransform transform, final IBox box, final Drawer drawer, final double x,
			final double y) {
		// Do nothing
	}

	public void endPage() {
		// Do nothing
	}
}
