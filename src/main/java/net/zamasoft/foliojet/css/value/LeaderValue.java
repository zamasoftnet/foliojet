package net.zamasoft.foliojet.css.value;

/**
 * {@code leader()} in the {@code content} property (css-content-3,
 * consult-codex-2026-07-31-leader.txt). Parsing normalizes the pattern:
 * {@code dotted} to "." / {@code solid} to "_" / {@code space} to " " /
 * custom strings remain unchanged (an empty string is a syntax error, to avoid
 * infinite repetition with a zero period).
 *
 * @author MIYABE Tatsuhiko
 */
public class LeaderValue implements Value {
	public static final LeaderValue DOTTED = new LeaderValue(".");
	public static final LeaderValue SOLID = new LeaderValue("_");
	public static final LeaderValue SPACE = new LeaderValue(" ");

	private final String pattern;

	public LeaderValue(final String pattern) {
		assert !pattern.isEmpty();
		this.pattern = pattern;
	}

	public String getPattern() {
		return this.pattern;
	}

	public String toString() {
		return "leader(\"" + this.pattern + "\")";
	}
}
