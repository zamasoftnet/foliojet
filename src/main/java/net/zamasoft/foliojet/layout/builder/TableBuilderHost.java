package net.zamasoft.foliojet.layout.builder;

import net.zamasoft.foliojet.layout.box.params.Params;

/**
 * Narrow callback through which {@link TableBuilder} invokes inline-context operations
 * on DocumentBuilder (C4-C refinement, 2026-07-19).
 *
 * <p>
 * Instead of exposing DocumentBuilder's private implementation details wholesale (the temporary
 * inline-box stack and StyledTextUnitizer's container-nesting counter), exposes only the three
 * operations actually needed for table construction through this narrow interface.
 * DocumentBuilder itself owns the implementation; callers (TableBuilder implementations) need not
 * know what the three operations do internally, only the contract to call them as needed when
 * entering/leaving a table (tell-don't-ask).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public interface TableBuilderHost {
	/**
	 * Closes open inline boxes and saves them for later restoration.
	 */
	void closeInlines(Params params);

	/**
	 * Ends the current container's text-formatting context.
	 */
	void endContainer();

	/**
	 * Starts a new container's text-formatting context.
	 */
	void startContainer();
}
