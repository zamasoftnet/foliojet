package net.zamasoft.foliojet.layout.draw;

/**
 * Marker for drawables with no visual appearance that affect page output (annotations, forms, etc.) (added
 * 2026-09-03, filter-element-group-design.md §0-3).
 * Even within a {@code filter} layer, {@link Drawer} draws elements bearing this marker through the page GC (they
 * would disappear inside the recording).
 */
public interface PageOutputDrawable extends Drawable {
}
