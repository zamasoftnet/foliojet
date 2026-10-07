package net.zamasoft.foliojet.css;

import org.xml.sax.Attributes;

/**
 * The read contract for source elements retained by layout results
 * ({@code Params.element}; added on 2026-07-24, E-6 increment 3b-4).
 *
 * <p>
 * {@link CSSElement} implements this contract during live construction,
 * and {@code StructureToken} (layout.segment) implements it during source replay
 * (BoxRecipe materialize). Style application (selector matching) finishes before recording,
 * so consumers after layout need only the following four items
 * (audit of all consumers on 2026-07-24, E-6 increment 3b-4):
 * </p>
 *
 * <ul>
 * <li>{@link #elementKey()} — pending resolution key for string-set (GCPM)
 * ({@code AbstractVisitor.visitBox})</li>
 * <li>{@link #lName()} — Tagged PDF structure role
 * ({@code TaggedPdf.blockRole}) and form component type</li>
 * <li>{@link #id()} — element identification in the geometry test harness
 * ({@code TestPDFVisitor})</li>
 * <li>{@link #atts()} — hyperlinks (xlink:href), image maps,
 * fragments (id), bookmarks (PDF outline) (header), form attributes, and
 * {@code <th scope>} ({@code TaggedPdf.headerScope}). {@code null} denotes
 * pseudo-elements or anonymous elements (annotation consumers return early)</li>
 * </ul>
 *
 * <p>
 * The contract also includes <b>logical identity</b>: boxes for the same logical
 * element must be identifiable as referring to the same element (prevents opening
 * Tagged PDF structure tags twice; {@code PageBox.beginStruct}). Actual elements
 * ({@code elementKey >= 0}) are identified by {@code elementKey} (sequence number in document order).
 * E-6 increment 4b (2026-07-24) introduced cases where a live ancestor ({@code CSSElement})
 * and a descendant replayed from a range ({@code StructureToken}) refer to the same logical element
 * (e.g., the principal box and marker box of {@code <li>}), so reference identity
 * could no longer express sharing across replay boundaries.
 * Pseudo-elements and anonymous elements ({@code elementKey < 0}) continue to use
 * the reference identity of static singletons. Interning within a replay session
 * ({@code SegmentExecutor}) continues to limit the number of instances.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public interface StructureElement {

	/** Sequence number in document order (-1 for pseudo-elements and anonymous elements). */
	long elementKey();

	/** Local name of the XML/HTML element. */
	String lName();

	/** ID corresponding to a CSS ID selector ({@code null} if absent). */
	String id();

	/** XML/HTML attributes ({@code null} for pseudo-elements and anonymous elements). */
	Attributes atts();
}
