package net.zamasoft.foliojet.ua;

import java.util.List;

/**
 * {@code string-set} entry awaiting finalized placement.
 * {@code parts} mixes {@link String} (fragments resolved at build time) and {@link #CONTENT}
 * (markers for positions to replace with the element's box text).
 * When placement is finalized, {@code AbstractVisitor.visitAssignment} materializes
 * {@link #CONTENT} using {@code box.getText()}, concatenates the parts,
 * and passes them to {@link PageAssignmentState#assign}.
 *
 * @author MIYABE Tatsuhiko
 */
public final class PendingStringSet {
	/** Marker for a {@code content()} position. */
	public static final Object CONTENT = new Object();

	public final String name;
	public final List<Object> parts;

	/** Stable document order of the assignment source. */
	public final long order;

	public PendingStringSet(final String name, final List<Object> parts, final long order) {
		this.name = name;
		this.parts = List.copyOf(parts);
		this.order = order;
	}
}
