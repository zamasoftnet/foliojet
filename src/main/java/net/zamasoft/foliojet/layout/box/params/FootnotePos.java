package net.zamasoft.foliojet.layout.box.params;

/**
 * Positioning for {@code float: footnote} (footnote F2, 2026-07-31;
 * design in consult-codex-2026-07-31-footnote.txt §3).
 *
 * <p>
 * Extends {@link FloatPos} and flows through as {@code PosType.FLOAT}, reusing
 * {@code DocumentBuilder.startBox}'s lifecycle for layout in a builder separate from the body text
 * (container-builder push/pop and range sealing). Differs from left/right floats only at completion
 * (the FLOAT branch of {@code endBox}), when it is passed to the page footnote registry
 * ({@code RootBuilder}) instead of {@code addBound} on the parent.
 * Does not participate in wrapping geometry (ExclusionSpace).
 * </p>
 */
public final class FootnotePos extends FloatPos {
}
