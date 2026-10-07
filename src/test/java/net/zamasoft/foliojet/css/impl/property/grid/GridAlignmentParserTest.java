package net.zamasoft.foliojet.css.impl.property.grid;

import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.BoxAlignmentValue;
import net.zamasoft.foliojet.layout.box.params.BoxAlignment;
import net.zamasoft.foliojet.layout.box.params.GridItemSpec;
import net.zamasoft.foliojet.css.value.GridLineValue;

/**
 * Alignment parsing tests for Grid G5a (consult-codex-2026-07-31-grid-g5.txt Q1/Q4).
 * Fix accepted sets (items/content: five normal-family values; self: auto plus those five)
 * and declaration invalidation for baseline, space-*, and safe/unsafe prefixes.
 */
public class GridAlignmentParserTest extends TestCase {

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

	private static Object parse(final PrimitivePropertyInfo info, final String css) {
		try {
			return ((GridAlignmentProperty) info).parseValue(tokens(info.getName() + ": " + css), null, null);
		} catch (final PropertyException e) {
			return e;
		}
	}

	public void testAccepted() {
		assertEquals(BoxAlignmentValue.CENTER, parse(GridAlignmentProperty.JUSTIFY_ITEMS, "center"));
		assertEquals(BoxAlignmentValue.STRETCH, parse(GridAlignmentProperty.ALIGN_ITEMS, "stretch"));
		assertEquals(BoxAlignmentValue.NORMAL, parse(GridAlignmentProperty.JUSTIFY_CONTENT, "normal"));
		assertEquals(BoxAlignmentValue.END, parse(GridAlignmentProperty.ALIGN_CONTENT, "end"));
		assertEquals(BoxAlignmentValue.AUTO, parse(GridAlignmentProperty.JUSTIFY_SELF, "auto"));
		assertEquals(BoxAlignmentValue.START, parse(GridAlignmentProperty.ALIGN_SELF, "start"));
	}

	/** auto is valid only for self properties. space-* invalidates items/self declarations. */
	public void testRejected() {
		assertTrue(parse(GridAlignmentProperty.JUSTIFY_ITEMS, "auto") instanceof PropertyException);
		// baseline and safe/unsafe are accepted as of 2026-08-29 (normalized to flex-start, etc.).
		// See ModernCssValuesTest.
		assertSame(BoxAlignmentValue.FLEX_START, parse(GridAlignmentProperty.ALIGN_ITEMS, "baseline"));
		// space-* became accepted for content properties in Flex F3a (the Grid mapper reduces them to NORMAL).
		// It still invalidates self/items declarations.
		assertTrue(parse(GridAlignmentProperty.JUSTIFY_ITEMS, "space-between") instanceof PropertyException);
		assertTrue(parse(GridAlignmentProperty.ALIGN_SELF, "space-evenly") instanceof PropertyException);
		assertTrue(parse(GridAlignmentProperty.JUSTIFY_CONTENT, "auto") instanceof PropertyException);
		assertSame(BoxAlignmentValue.CENTER, parse(GridAlignmentProperty.ALIGN_SELF, "safe center"));
		assertSame(BoxAlignmentValue.END, parse(GridAlignmentProperty.JUSTIFY_SELF, "unsafe end"));
		assertTrue(parse(GridAlignmentProperty.ALIGN_ITEMS, "center extra") instanceof PropertyException);
	}

	/** Used-value resolution (recommendation Q2) and exclusion of the AUTO singleton. */
	public void testUsedValueAndSpecSingleton() {
		assertEquals(BoxAlignment.STRETCH, BoxAlignment.resolve(BoxAlignment.AUTO, BoxAlignment.NORMAL));
		assertEquals(BoxAlignment.CENTER, BoxAlignment.resolve(BoxAlignment.AUTO, BoxAlignment.CENTER));
		assertEquals(BoxAlignment.END, BoxAlignment.resolve(BoxAlignment.END, BoxAlignment.CENTER));
		assertEquals(BoxAlignment.STRETCH, BoxAlignment.resolve(BoxAlignment.NORMAL, BoxAlignment.END));

		final GridItemSpec spec = GridItemSpec.of(GridLineValue.AUTO_VALUE, GridLineValue.AUTO_VALUE,
				GridLineValue.AUTO_VALUE, GridLineValue.AUTO_VALUE, BoxAlignment.CENTER, BoxAlignment.AUTO);
		assertFalse("非既定selfはAUTO singletonにならない", spec.isAuto());
		assertTrue(GridItemSpec.of(GridLineValue.AUTO_VALUE, GridLineValue.AUTO_VALUE, GridLineValue.AUTO_VALUE,
				GridLineValue.AUTO_VALUE).isAuto());
	}
}
