package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineReplacedBox;

/**
 * Marker output information.
 *
 * @author MIYABE Tatsuhiko
 */
class Marker {
	InlineBlockBox box = null;
	char[] text = null;
	InlineReplacedBox imageBox = null;
	/**
	 * Whether the list item owning an outside marker is in vertical writing (2026-10-08). The marker is settled before
	 * a child whose writing mode is orthogonal to it ({@code StyleEventMachine.settleMarkerBeforeTable}).
	 */
	boolean ownerVertical = false;
}
