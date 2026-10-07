package net.zamasoft.foliojet.css.scan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.ext.DefaultHandler2;

import net.zamasoft.foliojet.ua.SelectorFacts;
import net.zamasoft.foliojet.xml.XMLHandler;

/**
 * Terminal handler for the {@code STRUCTURE_SCAN} pass (a lightweight preliminary
 * scan without actual layout). The pseudo-classes {@code :last-child}/{@code :only-child}/
 * {@code :empty}/{@code :nth-last-child()}/{@code :nth-last-of-type()} depend only on
 * DOM structure (parent-child and sibling relationships, and element names) and require
 * no CSS cascade. Resolve them with this lightweight dedicated walker, bypassing
 * {@link net.zamasoft.foliojet.css.CSSProcessor} (style resolution and box construction;
 * see finalized design v3 in the development plan, "2パス制御モード").
 * <p>
 * Includes {@code display:none} subtrees (selectors match elements, not boxes).
 * Buffers only the sequence of one parent's direct children (ElementKey and element
 * name only), finalizes their positions from the end when the parent closes, then
 * discards it. Retains no content (text, attribute values, etc.), so retained data is
 * proportional only to one parent's child count, not to subtree size.
 * </p>
 * <p>
 * Assigns ElementKey values with the same convention as {@code CSSProcessor}
 * (zero-based, incremented without gaps in document order, excluding pseudo-elements).
 * Values agree as long as both scan the same input in the same order
 * ({@code CSSProcessor} was also fixed to consume the key space for display:none
 * and elements inside inline objects).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class StructureScanHandler extends DefaultHandler2 implements XMLHandler {
	private final SelectorFacts facts;

	private long nextElementKey = 0;

	private Frame current = null;

	private static final class ChildEntry {
		final long elementKey;
		final String uri;
		final String lName;

		ChildEntry(long elementKey, String uri, String lName) {
			this.elementKey = elementKey;
			this.uri = uri;
			this.lName = lName;
		}
	}

	private static final class Frame {
		final Frame parent;
		final long elementKey;
		final List<ChildEntry> children = new ArrayList<ChildEntry>();
		boolean sawContent = false;

		Frame(Frame parent, long elementKey) {
			this.parent = parent;
			this.elementKey = elementKey;
		}
	}

	public StructureScanHandler(SelectorFacts facts) {
		this.facts = facts;
	}

	public void startElement(String uri, String lName, String qName, Attributes atts) {
		long key = this.nextElementKey++;
		if (this.current != null) {
			this.current.sawContent = true;
			this.current.children.add(new ChildEntry(key, uri, lName));
		}
		this.current = new Frame(this.current, key);
	}

	public void endElement(String uri, String lName, String qName) {
		Frame frame = this.current;
		if (frame == null) {
			// An end tag is not expected before a start tag,
			// but guard defensively.
			return;
		}
		if (!frame.sawContent) {
			this.facts.setEmpty(frame.elementKey);
		}

		int n = frame.children.size();
		Map<String, List<ChildEntry>> byType = new HashMap<String, List<ChildEntry>>();
		for (int i = 0; i < n; ++i) {
			ChildEntry child = frame.children.get(i);
			int positionFromEnd = n - i;
			this.facts.setPositionFromEnd(child.elementKey, positionFromEnd);
			if (positionFromEnd == 1) {
				this.facts.setLastChild(child.elementKey);
			}
			String typeKey = (child.uri == null ? "" : child.uri) + "|" + child.lName;
			List<ChildEntry> sameType = byType.get(typeKey);
			if (sameType == null) {
				sameType = new ArrayList<ChildEntry>();
				byType.put(typeKey, sameType);
			}
			sameType.add(child);
		}
		for (List<ChildEntry> sameType : byType.values()) {
			int m = sameType.size();
			for (int i = 0; i < m; ++i) {
				ChildEntry child = sameType.get(i);
				int typePositionFromEnd = m - i;
				this.facts.setTypePositionFromEnd(child.elementKey, typePositionFromEnd);
				if (typePositionFromEnd == 1) {
					this.facts.setLastOfType(child.elementKey);
				}
			}
		}

		this.current = frame.parent;
	}

	public void characters(char[] ch, int start, int length) {
		if (this.current == null || this.current.sawContent) {
			return;
		}
		for (int i = 0; i < length; ++i) {
			if (!Character.isWhitespace(ch[start + i])) {
				this.current.sawContent = true;
				return;
			}
		}
	}
}
