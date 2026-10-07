package net.zamasoft.foliojet.layout.fragment;

import java.util.List;

import net.zamasoft.foliojet.layout.box.content.Container;

/**
 * Remainder directly under the owner of a COLUMN continuation (added 2026-07-21, M6b Phase B B4; used by {@link
 * ColumnContinuation} and {@link ContinuationValidator} as of 2026-07-25). Unlike the root fragment of a PAGE
 * continuation, the owner box itself is not reconstructed as a fragment ({@code
 * AbstractContainerBox.commitPreparedColumn()} merely adds a new empty Container to the same instance), so this
 * dedicated type represents it instead of a continuation frame (see the ChatGPT Pro consultation, design consultation).
 *
 * @param remainder   container holding replay ranges for closed subtrees directly under the owner
 * @param prefixItems replay ranges applied to remainder (replayed as closed)
 */
public record ColumnAnchor(Container remainder, List<Continuation.SourceRange> prefixItems) {

	public ColumnAnchor {
		prefixItems = List.copyOf(prefixItems);
	}
}
