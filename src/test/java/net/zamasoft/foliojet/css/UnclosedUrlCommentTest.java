package net.zamasoft.foliojet.css;

import junit.framework.TestCase;

/**
 * A comment left open after {@code url(} is closed at the end of the input when the {@code url(} does not start a
 * URL token. An unquoted URL token needs its closing parenthesis; without it the lexer reads {@code url(} as the
 * start of a function and a following {@code /*} as the start of a comment.
 * {@link DeclarationParser#closeUnterminatedComment} took everything after {@code url(} for the URL, left that
 * comment open, and the lexer failed on it: in a style attribute with an exception that ended the conversion, in a
 * style sheet by dropping the whole sheet.
 */
public class UnclosedUrlCommentTest extends TestCase {
	/** {@code url(} that is not closed: a comment after it is a comment. */
	public void testCommentAfterUnclosedUrl() {
		assertEquals("background:url(/**/", DeclarationParser.closeUnterminatedComment("background:url(/*"));
		assertEquals("p{color:red} a{background:url(x.png /* c*/",
				DeclarationParser.closeUnterminatedComment("p{color:red} a{background:url(x.png /* c"));
		assertEquals("a{background:url( x.png\n/* c*/",
				DeclarationParser.closeUnterminatedComment("a{background:url( x.png\n/* c"));
	}

	/** White space inside the URL, or a parenthesis: no URL token either. */
	public void testCommentAfterUrlThatIsNoToken() {
		assertEquals("a{background:url(x y) /* c*/",
				DeclarationParser.closeUnterminatedComment("a{background:url(x y) /* c"));
		assertEquals("a{background:url(x /* c */ ) }/* d*/",
				DeclarationParser.closeUnterminatedComment("a{background:url(x /* c */ ) }/* d"));
	}

	/** A URL token is skipped whole: what looks like a comment or a quote inside it is part of the URL. */
	public void testUrlToken() {
		assertEquals("a{background:url(a/*b)}", DeclarationParser.closeUnterminatedComment("a{background:url(a/*b)}"));
		assertEquals("a{background:url( /* )}", DeclarationParser.closeUnterminatedComment("a{background:url( /* )}"));
		assertEquals("a{background:url(a\\)/*b)}",
				DeclarationParser.closeUnterminatedComment("a{background:url(a\\)/*b)}"));
		assertEquals("a{background:url(a/*b)}/* c*/",
				DeclarationParser.closeUnterminatedComment("a{background:url(a/*b)}/* c"));
		// A quoted URL is a string
		assertEquals("a{background:url(\"a/*b\")}",
				DeclarationParser.closeUnterminatedComment("a{background:url(\"a/*b\")}"));
		assertEquals("a{background:url( 'a/*b' )}/* c*/",
				DeclarationParser.closeUnterminatedComment("a{background:url( 'a/*b' )}/* c"));
	}

	/** Input that ends in or right after {@code url(}. */
	public void testEndOfInput() {
		assertEquals("a{background:url(", DeclarationParser.closeUnterminatedComment("a{background:url("));
		assertEquals("a{background:url(x", DeclarationParser.closeUnterminatedComment("a{background:url(x"));
		assertEquals("a{background:url(x\\", DeclarationParser.closeUnterminatedComment("a{background:url(x\\"));
		assertEquals("a{background:ur", DeclarationParser.closeUnterminatedComment("a{background:ur"));
	}
}
