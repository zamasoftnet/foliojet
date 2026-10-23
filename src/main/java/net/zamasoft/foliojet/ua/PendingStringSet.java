package net.zamasoft.foliojet.ua;

import java.util.List;

import net.zamasoft.foliojet.css.value.ContentFunctionValue;

/**
 * {@code string-set} entry awaiting finalized placement.
 * {@code parts} mixes {@link String} (fragments resolved at build time) and {@link Content}
 * (markers for positions to replace with text of the element).
 * When placement is finalized, {@code AbstractVisitor.visitAssignment} materializes
 * {@link Content} using {@code box.getText()}, concatenates the parts,
 * and passes them to {@link PageAssignmentState#assign}.
 *
 * @author MIYABE Tatsuhiko
 */
public final class PendingStringSet {
	/**
	 * The text typeset for the element's own {@code ::before} and {@code ::after}, collected while they are typeset
	 * (2026-10-23), and of a list item's marker, which comes before them. A floated pseudo-element is not part of the
	 * element's box text.
	 */
	public static final class Capture {
		public final StringBuilder marker = new StringBuilder();
		public final StringBuilder before = new StringBuilder();
		public final StringBuilder after = new StringBuilder();
		public boolean beforeInFlow = true;
		public boolean afterInFlow = true;
	}

	/**
	 * A {@code content()} position (2026-10-23): {@code text} (also {@code content()}), {@code before}, {@code after}
	 * or {@code first-letter}.
	 */
	public static final class Content implements java.util.function.Function<String, String> {
		private final ContentFunctionValue kind;
		private final Capture capture;

		public Content(final ContentFunctionValue kind, final Capture capture) {
			this.kind = kind;
			this.capture = capture;
		}

		/**
		 * @param boxText the element's whole text as laid out, its own {@code ::before} and {@code ::after} included
		 * @return the text this position takes
		 */
		@Override
		public String apply(final String boxText) {
			if (this.kind == ContentFunctionValue.BEFORE) {
				return this.capture.before.toString();
			}
			if (this.kind == ContentFunctionValue.AFTER) {
				return this.capture.after.toString();
			}
			if (this.kind == ContentFunctionValue.FIRST_LETTER) {
				// ::first-letter takes generated content too (CSS Pseudo-Elements 4).
				return firstLetter(boxText);
			}
			final String text = withoutPseudo(boxText, this.capture.marker.toString(), "");
			return withoutPseudo(text, this.capture.beforeInFlow ? this.capture.before.toString() : "",
					this.capture.afterInFlow ? this.capture.after.toString() : "");
		}
	}

	public final String name;
	public final List<Object> parts;

	/** Stable document order of the assignment source. */
	public final long order;

	public PendingStringSet(final String name, final List<Object> parts, final long order) {
		this.name = name;
		this.parts = List.copyOf(parts);
		this.order = order;
	}

	/** Whether the parts need text of the element, known only when it is laid out. */
	public static boolean needsContent(final List<Object> parts) {
		for (final Object part : parts) {
			if (part instanceof Content) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The element's text without its own {@code ::before} and {@code ::after}, which begin and end it. The laid-out
	 * text may differ from what was typeset in white space and case ({@code white-space}, {@code text-transform}), so
	 * runs of white space match each other and letters match regardless of case; if the text does not begin (end) with
	 * the pseudo-element, it is left as it is.
	 */
	static String withoutPseudo(final String text, final String before, final String after) {
		int start = 0;
		int end = text.length();
		if (!before.isEmpty()) {
			final int matched = matchPrefix(text, before);
			if (matched >= 0) {
				start = matched;
			}
		}
		if (!after.isEmpty()) {
			final String rest = text.substring(start);
			final int matched = matchPrefix(new StringBuilder(rest).reverse().toString(),
					new StringBuilder(after).reverse().toString());
			if (matched >= 0) {
				end = text.length() - matched;
			}
		}
		return text.substring(start, end);
	}

	/**
	 * Where {@code prefix} ends in {@code text}, or -1 if the text does not begin with it. White space at the start and
	 * runs of white space inside match loosely; letters match regardless of case.
	 */
	private static int matchPrefix(final String text, final String prefix) {
		int i = skipWhiteSpace(text, 0);
		int j = skipWhiteSpace(prefix, 0);
		while (j < prefix.length()) {
			final int d = prefix.codePointAt(j);
			if (Character.isWhitespace(d) && skipWhiteSpace(prefix, j) == prefix.length()) {
				// Trailing white space of the pseudo-element, which the line layout may have dropped.
				return skipWhiteSpace(text, i);
			}
			if (i >= text.length()) {
				return -1;
			}
			final int c = text.codePointAt(i);
			if (Character.isWhitespace(c) && Character.isWhitespace(d)) {
				i = skipWhiteSpace(text, i);
				j = skipWhiteSpace(prefix, j);
				continue;
			}
			if (Character.toLowerCase(Character.toUpperCase(c)) != Character.toLowerCase(Character.toUpperCase(d))) {
				return -1;
			}
			i += Character.charCount(c);
			j += Character.charCount(d);
		}
		return i;
	}

	private static int skipWhiteSpace(final String s, int i) {
		while (i < s.length() && Character.isWhitespace(s.codePointAt(i))) {
			i += Character.charCount(s.codePointAt(i));
		}
		return i;
	}

	/**
	 * The first letter as {@code ::first-letter} takes it (CSS Pseudo-Elements 4): punctuation before it, the first
	 * letter with its combining marks, and punctuation right after it.
	 */
	static String firstLetter(final String text) {
		int i = skipWhiteSpace(text, 0);
		final int start = i;
		while (i < text.length() && isPunctuation(text.codePointAt(i))) {
			i += Character.charCount(text.codePointAt(i));
		}
		if (i >= text.length()) {
			return text.substring(start);
		}
		i += Character.charCount(text.codePointAt(i));
		while (i < text.length() && isMark(text.codePointAt(i))) {
			i += Character.charCount(text.codePointAt(i));
		}
		while (i < text.length() && isPunctuation(text.codePointAt(i))) {
			i += Character.charCount(text.codePointAt(i));
		}
		return text.substring(start, i);
	}

	private static boolean isPunctuation(final int c) {
		return switch (Character.getType(c)) {
		case Character.START_PUNCTUATION, Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION,
				Character.FINAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION -> true;
		default -> false;
		};
	}

	private static boolean isMark(final int c) {
		return switch (Character.getType(c)) {
		case Character.NON_SPACING_MARK, Character.ENCLOSING_MARK, Character.COMBINING_SPACING_MARK -> true;
		default -> false;
		};
	}
}
