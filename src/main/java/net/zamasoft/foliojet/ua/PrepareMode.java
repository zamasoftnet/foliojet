package net.zamasoft.foliojet.ua;

/**
 * Processing stages of UserAgent.prepare.
 */
public enum PrepareMode {
	/** Start of document processing. */
	DOCUMENT,
	/**
	 * Structure scan (added 2026-07-19). Lightweight preliminary pass with no box construction
	 * or layout. Resolves selectors whose truth is unknown until element end, such as :has()
	 * and the :last-child family (see the development plan, "2パス制御モード").
	 * An independent phase, not counted in the iterations of the existing
	 * {@code processing.pass-count}.
	 */
	STRUCTURE_SCAN,
	/** Intermediate pass (measurement only). */
	MIDDLE_PASS,
	/** Final pass. */
	LAST_PASS;
}
