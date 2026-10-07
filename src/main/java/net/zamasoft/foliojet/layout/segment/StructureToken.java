package net.zamasoft.foliojet.layout.segment;

import org.xml.sax.Attributes;

import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.StructureElement;

/**
 * The frozen form of {@code Params.element} (introduced on 2026-07-24, E-6 increment 3b-4:
 * the StructureToken decision in the development record).
 *
 * <p>
 * Holding {@link CSSElement} directly in {@link ParamsFields} retains the entire sequence of earlier
 * elements through the {@code CSSElement.precedingElement} chain (a retention problem; sharing itself
 * is safe). Freezing at recording time therefore detaches it into this lightweight token, holding
 * only the four fields needed by readers after layout (see the Javadoc of {@link StructureElement}).
 * The {@code atts} reference is acceptable as lightweight metadata linear in event count
 * (codex decision: {@code CSSElement} already holds the same reference for reading during rendering,
 * so this adds no retention).
 * </p>
 *
 * <p>
 * <b>Identity contract</b>: tokens for the same logical element ({@code elementKey}) are interned
 * within a replay session ({@code SegmentExecutor}), so the same instance is passed.
 * This prevents duplicate structure-tag openings in Tagged PDF in the same way as the live path
 * (the identity set in {@code PageBox.beginStruct}; for example, the principal box and marker box
 * of a {@code <li>} share the same element).
 * </p>
 */
public record StructureToken(long elementKey, String lName, String id, Attributes atts) implements StructureElement {

	/**
	 * Freezes at recording time (called by {@link ParamsFields#freeze}).
	 *
	 * <ul>
	 * <li>Leaves {@code null} and already frozen tokens unchanged.</li>
	 * <li><b>Retains</b> {@link CSSElement} instances with {@code elementKey < 0} (pseudo-elements,
	 * anonymous elements, and at-page elements) directly. These are all static singleton constants
	 * of {@code CSSElement} (no {@code precedingElement}, so retention is harmless; dynamic creation
	 * occurs only through the {@code CSSProcessor} elementKey assignment path, inspected on 2026-07-24).
	 * Sharing the singleton exactly matches the identity of live behavior (e.g., sharing {@code ANON_TABLE}
	 * for anonymous tables prevents duplicate tag openings). Copying them into tokens would instead
	 * break identity (-1 collides across pseudo-elements, so they cannot be interned).</li>
	 * <li>Copies all other elements (real elements in the document) into tokens with only four fields.</li>
	 * </ul>
	 */
	public static StructureElement freeze(final StructureElement element) {
		if (element == null || element instanceof StructureToken) {
			return element;
		}
		if (element.elementKey() < 0) {
			return element;
		}
		return new StructureToken(element.elementKey(), element.lName(), element.id(), element.atts());
	}
}
