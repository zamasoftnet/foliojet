package net.zamasoft.foliojet.layout.fragment;

import java.io.IOException;

/**
 * A typed layout failure indicating failed text payload spill I/O (write/read)
 * (E-6 increment 3b-2, introduced 2026-07-24).
 *
 * <p>
 * SpillStore's {@link IOException} is neither ignored nor handled by falling back to live continuation.
 * It propagates through this exception as a layout failure, avoiding nondeterministic output depending
 * on spill success (crash consistency; design consultation §5).
 * </p>
 */
public class TextSpillException extends RuntimeException {
	private static final long serialVersionUID = 0L;

	public TextSpillException(final String message, final IOException cause) {
		super(message, cause);
	}
}
