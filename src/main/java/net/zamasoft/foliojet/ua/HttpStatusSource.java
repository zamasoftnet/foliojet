package net.zamasoft.foliojet.ua;

/**
 * Additional Source contract that provides HTTP response status to diagnostics.
 *
 * <p>
 * Exposes neither the body nor response headers, only the status code needed to indicate
 * the retrieval failure stage. Returns a negative value if no response exists yet or for non-HTTP sources.
 * </p>
 */
public interface HttpStatusSource {
	public int httpStatus();
}
