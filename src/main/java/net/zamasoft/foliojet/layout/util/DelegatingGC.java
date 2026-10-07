package net.zamasoft.foliojet.layout.util;

import net.zamasoft.pdfg2d.gc.GC;

/**
 * Wrapper that delegates to another {@link GC} (2026-08-29).
 *
 * <p>
 * {@link FilterGC} (filter color transformation) and {@link ApproximationGC} (approximation reporting)
 * wrap the page's GC before passing it to drawables. PDF-specific processing (structure tags,
 * annotations) uses {@code instanceof} to check whether the destination is {@code PDFGC},
 * so {@link #unwrap} provides access to the underlying GC through wrappers.
 * </p>
 */
public interface DelegatingGC {
	/** Wrapped GC. */
	public GC delegate();

	/** Returns the underlying GC with all wrappers removed (unchanged if not wrapped). */
	public static GC unwrap(GC gc) {
		while (gc instanceof DelegatingGC d) {
			gc = d.delegate();
		}
		return gc;
	}
}
