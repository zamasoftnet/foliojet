package net.zamasoft.foliojet.css.style.running;

import java.util.List;

/** An immutable subtree independent of live styles, retaining generated content unevaluated. */
public final class RunningTemplate {
	public sealed interface Event permits Start, End, Text, Token {
	}

	/** Pseudo-elements also use start/end events; pseudo names are before/after/first-letter, or null for real elements. */
	public record Start(StyleSnapshot style, String pseudo) implements Event {
	}

	public record End() implements Event {
	}

	public record Text(String text) implements Event {
	}

	/** Assigns inner running content independently, retaining only its name and document order in the outer content. */
	public record Token(String name, long order) implements Event {
	}

	private final String name;
	private final List<Event> events;
	private final int textBytes;
	private final int imageReferences;

	RunningTemplate(final String name, final List<Event> events, final int textBytes, final int imageReferences) {
		this.name = name;
		this.events = List.copyOf(events);
		this.textBytes = textBytes;
		this.imageReferences = imageReferences;
	}

	public String name() {
		return this.name;
	}

	public List<Event> events() {
		return this.events;
	}

	/** Budget consumed by the copied payload (strings counted in UTF-16 bytes). */
	public int textBytes() {
		return this.textBytes;
	}

	public int imageReferences() {
		return this.imageReferences;
	}
}
