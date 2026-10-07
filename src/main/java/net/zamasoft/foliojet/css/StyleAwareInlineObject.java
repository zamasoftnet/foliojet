package net.zamasoft.foliojet.css;

/**
 * An inline object that can receive the host document's style context
 * (2026-08-07). When an inline object starts, CSSProcessor passes the resolved
 * {@link CSSStyle} of that element (the svg root for SVG).
 * Used by inline SVG to resolve var() in author CSS with custom properties
 * at the SVG's location ({@link SVGAuthorCss#toCssText}).
 */
public interface StyleAwareInlineObject extends InlineObject {
	public void setHostStyle(CSSStyle style);
}
