package net.zamasoft.foliojet.css;

/**
 * The origin of a CSS rule (cascade origin, CSS Cascading and Inheritance specification).
 * Enumeration order also defines ascending priority: USER_AGENT rules always
 * rank below AUTHOR rules, regardless of specificity or source order.
 */
public enum Origin {
	/** User agent default stylesheet. */
	USER_AGENT,
	/** Document (author) stylesheet and inline style attributes. */
	AUTHOR;
}
