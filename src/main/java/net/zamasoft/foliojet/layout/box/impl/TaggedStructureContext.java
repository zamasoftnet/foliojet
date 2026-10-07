package net.zamasoft.foliojet.layout.box.impl;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.zamasoft.pdfg2d.pdf.StructureRef;

/**
 * Cross-page registry of tagged PDF structure elements (defect ② fix, 2026-07-30:
 * the result of a codex consultation on how to handle structure elements when splitting).
 *
 * <p>
 * <b>The defect</b>: StructElem deduplication was previously scoped to {@code PageBox}
 * (a new instance per page). When a continuation fragment of the same logical element was drawn
 * on the next page, it was considered undeclared, so {@code declareStructElement} ran again,
 * <b>splitting one element into multiple StructElems</b>. pdfg2d's {@code StructureTreeBuilder}
 * can retain and output MCIDs from multiple pages ({@code /Type /MCR /Pg}) in one Elem, so reusing
 * the {@link StructureRef} declared at first occurrence across pages gives one StructElem per element.
 * </p>
 *
 * <p>
 * <b>Lifetime</b>: {@code PageSequence} holds one registry per document (= PDF writer), passing it
 * to each page's {@code PageBox} via {@code setStructOutput}. At page end ({@link #endPage}), discards
 * all entries except those declared or reused on that page. Continuation fragments always appear
 * on the immediately following page, so a two-page window suffices; even for huge documents,
 * retention is bounded by the number of elements on a page.
 * </p>
 *
 * <p>
 * <b>Keys</b>: Only {@code StructureElement.elementKey() >= 0} (a document-order sequence number =
 * logical identity). Negative values represent anonymous/pseudo-elements whose contract requires
 * object-identity comparison, so they are excluded from the registry (managed within a page as before).
 * </p>
 */
public final class TaggedStructureContext {

	/**
	 * Bundle of declared references for one logical element. Only {@code LI} has two levels with
	 * {@code LBody}, so the reference sequence pushed onto the stack ({@code refs}) and the drawing
	 * target ({@code contentRef} = the last reference) are stored separately.
	 * Also retains role, scope, and parent to verify identity on a hit.
	 */
	record Binding(StructureRef[] refs, String role, String scope, StructureRef parent) {
		StructureRef contentRef() {
			return this.refs[this.refs.length - 1];
		}
	}

	private final Map<Long, Binding> byKey = new HashMap<>();

	/** Keys declared or reused on this page (for the endPage liveness check). */
	private final Set<Long> touched = new HashSet<>();

	/**
	 * Returns an existing declaration (null if absent).
	 * If returned, its key becomes eligible for retention on this page.
	 */
	Binding lookup(final long elementKey) {
		final Binding binding = this.byKey.get(elementKey);
		if (binding != null) {
			this.touched.add(elementKey);
		}
		return binding;
	}

	/** Registers a declaration at first occurrence. */
	void register(final long elementKey, final Binding binding) {
		this.byKey.put(elementKey, binding);
		this.touched.add(elementKey);
	}

	/**
	 * Performs page-boundary cleanup (at the end of {@code PageSequence.drawPage}).
	 * Discards entries untouched on this page (= elements with no continuation fragment on the next page)
	 * to keep retention bounded.
	 */
	public void endPage() {
		this.byKey.keySet().retainAll(this.touched);
		this.touched.clear();
	}
}
