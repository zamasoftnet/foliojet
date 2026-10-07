package net.zamasoft.foliojet.css.impl.property.grid;

import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.GridLineValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.ua.UserAgent;

/**
 * Parsing tests for Grid G0 (consult-codex-2026-07-31-grid.txt §2/G0).
 * Fix acceptance/rejection of track lists (fixed lengths, auto, fr, integer repeat expansion,
 * limit 4096) and grid-line (auto, integer line numbers, span).
 * Computed values (absolutization) need a style context, so cover only acceptance of the Raw
 * intermediate form here; GridBox integration tests fix absolutization.
 */
public class GridTrackParserTest extends TestCase {

	private static TokenStream tokens(final String declaration) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList decls = CSSReaderDeclarationList.readFromString(declaration, settings);
		assertNotNull("宣言のパースに失敗: " + declaration, decls);
		final List<CSSDeclaration> all = decls.getAllDeclarations();
		assertEquals(1, all.size());
		final List<CssToken> ts = Tokens.fromExpression(all.get(0).getExpression());
		return new TokenStream(ts);
	}

	private static UserAgent ua() {
		return (UserAgent) java.lang.reflect.Proxy.newProxyInstance(GridTrackParserTest.class.getClassLoader(),
				new Class[] { UserAgent.class }, (proxy, method, args) -> {
					if ("getPixelsPerInch".equals(method.getName())) {
						return 96.0;
					}
					if ("toString".equals(method.getName())) {
						return "GridTrackParserTest.UserAgent";
					}
					if ("hashCode".equals(method.getName())) {
						return System.identityHashCode(proxy);
					}
					if ("equals".equals(method.getName())) {
						return proxy == args[0];
					}
					throw new UnsupportedOperationException(method.toString());
				});
	}

	private static Value parseTracks(final String value) throws PropertyException {
		return ((GridTemplateTracks) GridTemplateTracks.COLUMNS)
				.parseValue(tokens("grid-template-columns: " + value), ua(), null);
	}

	private static void assertTracksRejected(final String value) {
		try {
			parseTracks(value);
			fail("拒否されるべきtrack list: " + value);
		} catch (PropertyException e) {
			// expected
		}
	}

	public void testTrackListAccepted() throws Exception {
		assertNotNull(parseTracks("100pt auto 1fr"));
		assertNotNull(parseTracks("repeat(3, 50pt 1fr)"));
		assertNotNull(parseTracks("2em 0.5fr"));
		assertNotNull(parseTracks("none"));
		// minmax() preserves both ends as of 2026-08-29 (GridShorthandParserTest.
		// testMinMaxTracks). max()/min() are approximate support outside the specification
		// (2026-08-06; see the class Javadoc in GridTemplateTracks.java).
		assertNotNull(parseTracks("minmax(50pt, 1fr)"));
		assertNotNull(parseTracks("minmax(0, 1fr)")); // The form always used by Tailwind grid-cols-N.
		assertNotNull(parseTracks("repeat(9, minmax(30pt, auto))")); // Actual input from yahoo.co.jp
		assertNotNull(parseTracks("minmax(auto, 1fr)"));
		assertNotNull(parseTracks("minmax(min-content, max-content)"));
		assertNotNull(parseTracks("max(44px, 4.4rem)")); // Actual input from yahoo.co.jp
		assertNotNull(parseTracks("min(44px, 4.4rem)"));
	}

	public void testTrackListRejected() throws Exception {
		assertTracksRejected("-1fr"); // Negative fr
		assertTracksRejected("-50%"); // Negative %
		assertTracksRejected("repeat(0, 50pt)"); // Zero repetitions
		assertTracksRejected("repeat(2, repeat(2, 50pt))"); // Nested repeat
		assertTracksRejected("repeat(5000, 50pt)"); // Expansion exceeds the 4096 limit
		assertTracksRejected("minmax(50pt)"); // Too few arguments (two required)
		assertTracksRejected("minmax(1fr, 100pt)"); // fr is invalid on the min side
		assertTracksRejected("minmax(-10pt, 100pt)"); // Negative length
		assertTracksRejected("minmax(auto, minmax(0, 1fr))"); // Nested
		assertTracksRejected("max(50%, 1fr)"); // Percentage arguments to max()/min() remain outside the subset.
		// Percentage tracks, percentages in minmax, and line names [a] are accepted as of 2026-08-29
		// (see GridShorthandParserTest).
		assertNotNull(parseTracksSafe("50%"));
		assertNotNull(parseTracksSafe("minmax(50pt, 50%)"));
		assertNotNull(parseTracksSafe("[a] 100pt [b]"));
	}

	private static Value parseTracksSafe(final String value) {
		try {
			return parseTracks(value);
		} catch (PropertyException e) {
			return null;
		}
	}

	public void testGridLine() throws Exception {
		final GridPlacement info = (GridPlacement) GridPlacement.COLUMN_START;
		assertTrue(((GridLineValue) info.parseValue(tokens("grid-column-start: auto"), ua(), null)).isAuto());
		final GridLineValue line = (GridLineValue) info.parseValue(tokens("grid-column-start: -2"), ua(), null);
		assertFalse(line.isAuto());
		assertFalse(line.isSpan());
		assertEquals(-2, line.getNumber());
		final GridLineValue span = (GridLineValue) info.parseValue(tokens("grid-column-start: span 3"), ua(), null);
		assertTrue(span.isSpan());
		assertEquals(3, span.getNumber());
	}

	public void testGridLineRejected() {
		final GridPlacement info = (GridPlacement) GridPlacement.ROW_START;
		// "span 0" is accepted as span 1 as of 2026-08-29 (GridShorthandParserTest).
		// "a" (a line name) is also accepted.
		for (final String bad : new String[] { "0", "span -1", "1.5", "auto auto", "span" }) {
			try {
				info.parseValue(tokens("grid-row-start: " + bad), ua(), null);
				fail("拒否されるべきgrid-line: " + bad);
			} catch (PropertyException e) {
				// expected
			}
		}
	}
}
