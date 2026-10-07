package net.zamasoft.foliojet.css;

import java.util.Locale;

import org.xml.sax.Attributes;

/**
 * Information about a CSS element.
 *
 * <p>
 * E-6 increment 3b-4 (2026-07-24): Implements the read contract {@link StructureElement}
 * for consumers after layout (Tagged PDF, annotations, and string-set).
 * The contract is shared with the frozen source replay result ({@code StructureToken});
 * {@code Params.element} stores this interface type.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class CSSElement implements StructureElement {
	private static final boolean DEBUG_CHAIN = false;

	public static final byte PC_FIRST = 1;
	public static final byte PC_LEFT = 2;
	public static final byte PC_RIGHT = 3;
	public static final byte PC_EVEN = 4;
	public static final byte PC_ODD = 5;
	public static final byte PC_FIRST_CHILD = 6;
	public static final byte PC_LINK = 7;
	public static final byte PC_ROOT = 8;

	/**
	 * The first at-page page for left-bound duplex printing.
	 */
	public static final CSSElement PAGE_FIRST_RIGHT = new CSSElement(new byte[] { PC_FIRST, PC_RIGHT, PC_ODD });

	/**
	 * A left at-page page for left binding.
	 */
	public static final CSSElement PAGE_LEFT_EVEN = new CSSElement(new byte[] { PC_LEFT, PC_EVEN });

	/**
	 * A right at-page page for left binding.
	 */
	public static final CSSElement PAGE_RIGHT_ODD = new CSSElement(new byte[] { PC_RIGHT, PC_ODD });

	/**
	 * The first at-page page for right-bound duplex printing.
	 */
	public static final CSSElement PAGE_FIRST_LEFT = new CSSElement(new byte[] { PC_FIRST, PC_LEFT, PC_ODD });

	/**
	 * A left at-page page for right binding.
	 */
	public static final CSSElement PAGE_LEFT_ODD = new CSSElement(new byte[] { PC_LEFT, PC_ODD });

	/**
	 * A right at-page page for right binding.
	 */
	public static final CSSElement PAGE_RIGHT_EVEN = new CSSElement(new byte[] { PC_RIGHT, PC_EVEN });

	/**
	 * The first at-page page for simplex printing.
	 */
	public static final CSSElement PAGE_SINGLE_FIRST = new CSSElement(new byte[] { PC_FIRST });

	/**
	 * An at-page page for simplex printing.
	 */
	public static final CSSElement PAGE_SINGLE = new CSSElement((byte[]) null);

	/**
	 * The at-page first-line pseudo-element.
	 */
	public static final CSSElement FIRST_LINE = new CSSElement("first-line");

	/**
	 * The at-page first-letter pseudo-element.
	 */
	public static final CSSElement FIRST_LETTER = new CSSElement("first-letter");

	/**
	 * The at-page before pseudo-element.
	 */
	public static final CSSElement BEFORE = new CSSElement("before");

	/**
	 * The at-page after pseudo-element.
	 */
	public static final CSSElement AFTER = new CSSElement("after");

	/**
	 * The {@code ::marker} pseudo-element (added on 2026-07-21, CSS Lists).
	 * Used to resolve the cascade for a limited set of properties such as {@code color}/{@code font-*}
	 * on the list marker (list-item) itself. Uses the same mechanism as BEFORE/AFTER:
	 * a synthetic CSSElement with atts=null.
	 */
	public static final CSSElement MARKER = new CSSElement("marker");

	/**
	 * The {@code ::footnote-call} pseudo-element (footnotes F0, 2026-07-31; design:
	 * consult-codex-2026-07-31-footnote.txt). The number marker that remains
	 * at the call site. Synthesis is wired in F1.
	 */
	public static final CSSElement FOOTNOTE_CALL = new CSSElement("footnote-call");

	/**
	 * The {@code ::footnote-marker} pseudo-element (footnotes F0). The number at the start of the footnote body.
	 * Synthesis is wired in F1.
	 */
	public static final CSSElement FOOTNOTE_MARKER = new CSSElement("footnote-marker");

	/**
	 * An anonymous element.
	 */
	public static final CSSElement ANON = new CSSElement((String)null);
	public static final CSSElement ANON_TABLE = new CSSElement("table");
	public static final CSSElement ANON_TBODY = new CSSElement("tbody");
	public static final CSSElement ANON_TR = new CSSElement("tr");
	public static final CSSElement ANON_TD = new CSSElement("td");

	/** An XML/HTML element. */
	public final String uri, lName;

	/** The ID corresponding to a CSS ID selector. */
	public final String id;

	/** All classes corresponding to CSS class selectors. */
	public final String[] styleClasses;

	/** CSS pseudo-classes. */
	public final byte[] pseudoClasses;

	/** The language. */
	public final Locale lang;

	/**
	 * Directionality for :dir() ("ltr" / "rtl" / null=unspecified). Inherited through
	 * the document tree from the dir attribute. Determining the first strong directional character
	 * for dir="auto" requires lookahead, so it is outside the 1P policy and falls through to the inherited value.
	 */
	public final String dir;

	/** XML/HTML attributes. */
	public final Attributes atts;

	/** The preceding element. */
	public final CSSElement precedingElement;

	/** The position in the document. */
	public final int charOffset;

	/**
	 * A sequence number in document order (zero-based, stable across passes such as STRUCTURE_SCAN/LAYOUT,
	 * because the same input receives numbers in the same traversal order).
	 * Used as a stable key for features that cache matching results across passes,
	 * such as {@code :has()}/{@code :last-child} (see {@code SelectorFacts}).
	 * Not assigned to pseudo-elements (remains -1, since only actual DOM elements are eligible).
	 * This dedicated counter is used instead of charOffset (the byte position in the source)
	 * because charOffset becomes -1 on paths without a locator and may collide.
	 */
	public final long elementKey;

	/**
	 * Constructs an HTML element.
	 *
	 * @param uri
	 * @param lName
	 * @param id
	 * @param styleClasses
	 * @param pseudoClasses
	 * @param atts
	 * @param precedingElement
	 * @param charOffset
	 * @param elementKey       sequence number in document order (zero-based); -1 for pseudo-elements
	 */
	public CSSElement(String uri, String lName, String id, String[] styleClasses, byte[] pseudoClasses, Locale lang,
			String dir, Attributes atts, CSSElement precedingElement, int charOffset, long elementKey) {
		this.uri = uri;
		this.lName = lName;
		this.id = id;
		this.styleClasses = styleClasses;
		this.pseudoClasses = pseudoClasses;
		this.lang = lang;
		this.dir = dir;
		this.atts = atts;
		this.precedingElement = precedingElement;
		this.charOffset = charOffset;
		this.elementKey = elementKey;
	}

	/**
	 * Constructs a pseudo-element.
	 *
	 * @param pseudoElement
	 */
	private CSSElement(String pseudoElement) {
		this(null, pseudoElement, null, null, null, null, null, null, null, -1, -1);
	}

	/**
	 * Constructs a pseudo-class.
	 *
	 * @param pseudoClasses
	 */
	private CSSElement(byte[] pseudoClasses) {
		this(null, null, null, null, pseudoClasses, null, null, null, null, -1, -1);
	}

	@Override
	public long elementKey() {
		return this.elementKey;
	}

	@Override
	public String lName() {
		return this.lName;
	}

	@Override
	public String id() {
		return this.id;
	}

	@Override
	public Attributes atts() {
		return this.atts;
	}

	/**
	 * Returns true if this element has the given class.
	 *
	 * @param styleClass
	 * @return
	 */
	public boolean isStyleClass(String styleClass) {
		if (this.styleClasses != null) {
			for (int i = 0; i < this.styleClasses.length; ++i) {
				if (styleClass.equalsIgnoreCase(this.styleClasses[i])) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Returns true if this element has the given pseudo-class.
	 * 
	 * @param pseudoClass
	 * @return
	 */
	public boolean isPseudoClass(byte pseudoClass) {
		if (pseudoClass == 0) {
			return false;
		}
		if (this.pseudoClasses != null) {
			for (int i = 0; i < this.pseudoClasses.length; ++i) {
				if (pseudoClass == this.pseudoClasses[i]) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isPseudoElement() {
		return this.atts == null && this.lName != null;
	}

	public String toString() {
		StringBuilder buff = new StringBuilder();
		buff.append(super.toString());
		buff.append("@uri='");
		buff.append(this.uri);
		buff.append("',lName='");
		buff.append(this.lName);
		buff.append("'");
		if (this.id != null) {
			buff.append(",id='");
			buff.append(this.id);
			buff.append("'");
		}
		if (this.pseudoClasses != null) {
			buff.append(",pseudoClasses='");
			for (int i = 0; i < this.pseudoClasses.length; ++i) {
				if (i > 0) {
					buff.append(",");
				}
				buff.append(this.pseudoClasses[i]);
			}
			buff.append("'");
		}
		if (this.styleClasses != null) {
			buff.append(",styleClasses='");
			for (int i = 0; i < this.styleClasses.length; ++i) {
				if (i > 0) {
					buff.append(",");
				}
				buff.append(this.styleClasses[i]);
			}
			buff.append("'");
		}
		if (this.lang != null) {
			buff.append(",lang='");
			buff.append(this.lang);
			buff.append("'");
		}
		if (this.precedingElement != null) {
			buff.append(",precedingElement='");
			buff.append(this.precedingElement.chain());
			buff.append("'");
		}
		if (this.atts != null && this.atts.getLength() > 0) {
			buff.append("[");
			for (int i = 0; i < this.atts.getLength(); ++i) {
				if (i > 0) {
					buff.append(",");
				}
				buff.append(this.atts.getLocalName(i));
				buff.append('=');
				buff.append(this.atts.getValue(i));
			}
			buff.append("]");
		}
		return buff.toString();
	}

	private String chain() {
		if (DEBUG_CHAIN) {
			if (this.precedingElement == null) {
				return this.lName;
			}
			return this.lName + "/" + this.precedingElement.chain();
		}
		return this.lName;
	}
}