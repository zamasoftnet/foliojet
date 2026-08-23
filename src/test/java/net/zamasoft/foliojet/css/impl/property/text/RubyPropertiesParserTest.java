package net.zamasoft.foliojet.css.impl.property.text;

import java.util.List;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSDeclarationList;
import com.helger.css.reader.CSSReaderDeclarationList;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.property.AbstractPrimitivePropertyInfo;
import net.zamasoft.foliojet.css.property.PropertyException;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.TokenStream;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.value.RubyAlignValue;
import net.zamasoft.foliojet.css.value.RubyMergeValue;
import net.zamasoft.foliojet.css.value.RubyOverhangValue;
import net.zamasoft.foliojet.css.value.RubyPositionValue;

/** CSS Ruby Level 1の4プロパティの受理値を固定する。 */
public class RubyPropertiesParserTest extends TestCase {
	private static TokenStream tokens(final String declaration) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		final CSSDeclarationList decls = CSSReaderDeclarationList.readFromString(declaration, settings);
		assertNotNull(decls);
		final List<CSSDeclaration> all = decls.getAllDeclarations();
		assertEquals(1, all.size());
		final List<CssToken> ts = Tokens.fromExpression(all.get(0).getExpression());
		return new TokenStream(ts);
	}

	private static Object parse(final AbstractPrimitivePropertyInfo info, final String property, final String value) {
		try {
			return info.parseValue(tokens(property + ": " + value), null, null);
		} catch (final PropertyException e) {
			return e;
		}
	}

	public void testAlign() {
		assertEquals(RubyAlignValue.START, parse((RubyAlign) RubyAlign.INFO, "ruby-align", "start"));
		assertEquals(RubyAlignValue.CENTER, parse((RubyAlign) RubyAlign.INFO, "ruby-align", "center"));
		assertEquals(RubyAlignValue.SPACE_BETWEEN,
				parse((RubyAlign) RubyAlign.INFO, "ruby-align", "space-between"));
		assertEquals(RubyAlignValue.SPACE_AROUND,
				parse((RubyAlign) RubyAlign.INFO, "ruby-align", "space-around"));
		assertTrue(parse((RubyAlign) RubyAlign.INFO, "ruby-align", "end") instanceof PropertyException);
	}

	public void testMergeAndOverhang() {
		assertEquals(RubyMergeValue.SEPARATE, parse((RubyMerge) RubyMerge.INFO, "ruby-merge", "separate"));
		assertEquals(RubyMergeValue.MERGE, parse((RubyMerge) RubyMerge.INFO, "ruby-merge", "merge"));
		assertEquals(RubyMergeValue.AUTO, parse((RubyMerge) RubyMerge.INFO, "ruby-merge", "auto"));
		assertEquals(RubyOverhangValue.AUTO, parse((RubyOverhang) RubyOverhang.INFO, "ruby-overhang", "auto"));
		assertEquals(RubyOverhangValue.NONE, parse((RubyOverhang) RubyOverhang.INFO, "ruby-overhang", "none"));
	}

	public void testPosition() {
		assertEquals(RubyPositionValue.ALTERNATE,
				parse((RubyPosition) RubyPosition.INFO, "ruby-position", "alternate"));
		assertEquals(RubyPositionValue.OVER, parse((RubyPosition) RubyPosition.INFO, "ruby-position", "over"));
		assertEquals(RubyPositionValue.UNDER, parse((RubyPosition) RubyPosition.INFO, "ruby-position", "under"));
		assertEquals(RubyPositionValue.ALTERNATE_OVER,
				parse((RubyPosition) RubyPosition.INFO, "ruby-position", "over alternate"));
		assertEquals(RubyPositionValue.ALTERNATE_UNDER,
				parse((RubyPosition) RubyPosition.INFO, "ruby-position", "alternate under"));
		assertEquals(RubyPositionValue.INTER_CHARACTER,
				parse((RubyPosition) RubyPosition.INFO, "ruby-position", "inter-character"));
		assertTrue(parse((RubyPosition) RubyPosition.INFO, "ruby-position", "over under") instanceof PropertyException);
		assertTrue(parse((RubyPosition) RubyPosition.INFO, "ruby-position", "alternate alternate") instanceof PropertyException);
	}
}
