package net.zamasoft.foliojet.css.impl.property.box;

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
import net.zamasoft.foliojet.css.util.BoxValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.FitContentValue;
import net.zamasoft.foliojet.css.value.KeywordValue;
import net.zamasoft.foliojet.css.value.PercentageValue;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.layout.box.params.IntrinsicSize;
import net.zamasoft.foliojet.layout.box.params.Length;
import net.zamasoft.foliojet.layout.box.params.LengthType;

/**
 * 固有寸法キーワード {@code max-content}/{@code min-content}/
 * {@code fit-content}/{@code fit-content(L)}(css-sizing-3、2026-08-29)の
 * 解析とレイアウト側表現への変換のテストです。
 */
public class IntrinsicSizeKeywordTest extends TestCase {

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

	private static Value parse(final AbstractPrimitivePropertyInfo info, final String value)
			throws PropertyException {
		return info.parseValue(tokens(info.getName() + ": " + value), null, null);
	}

	public void testKeywordsOnEveryProperty() throws Exception {
		final AbstractPrimitivePropertyInfo[] infos = { (AbstractPrimitivePropertyInfo) Width.INFO,
				(AbstractPrimitivePropertyInfo) Height.INFO, (AbstractPrimitivePropertyInfo) MinWidth.INFO,
				(AbstractPrimitivePropertyInfo) MaxWidth.INFO, (AbstractPrimitivePropertyInfo) MinHeight.INFO,
				(AbstractPrimitivePropertyInfo) MaxHeight.INFO, (AbstractPrimitivePropertyInfo) InlineSize.INFO,
				(AbstractPrimitivePropertyInfo) BlockSize.INFO, (AbstractPrimitivePropertyInfo) MinInlineSize.INFO,
				(AbstractPrimitivePropertyInfo) MaxInlineSize.INFO, (AbstractPrimitivePropertyInfo) MinBlockSize.INFO,
				(AbstractPrimitivePropertyInfo) MaxBlockSize.INFO };
		for (final AbstractPrimitivePropertyInfo info : infos) {
			assertSame(info.getName(), KeywordValue.MAX_CONTENT, parse(info, "max-content"));
			assertSame(info.getName(), KeywordValue.MIN_CONTENT, parse(info, "min-content"));
			assertSame(info.getName(), KeywordValue.FIT_CONTENT, parse(info, "fit-content"));
			final Value fit = parse(info, "fit-content(10pt)");
			assertTrue(info.getName(), fit instanceof FitContentValue);
			assertEquals(info.getName(), 10.0, ((AbsoluteLengthValue) ((FitContentValue) fit).argument()).getLength(),
					1e-9);
		}
	}

	public void testKeywordIsCaseInsensitive() throws Exception {
		assertSame(KeywordValue.MAX_CONTENT, parse((AbstractPrimitivePropertyInfo) Width.INFO, "MAX-CONTENT"));
		assertTrue(parse((AbstractPrimitivePropertyInfo) Width.INFO, "FIT-CONTENT(1pt)") instanceof FitContentValue);
	}

	public void testFitContentPercentage() throws Exception {
		final Value fit = parse((AbstractPrimitivePropertyInfo) Width.INFO, "fit-content(50%)");
		assertEquals(0.5, ((PercentageValue) ((FitContentValue) fit).argument()).getRatio(), 1e-9);
	}

	public void testPlainLengthStillParses() throws Exception {
		final Value v = parse((AbstractPrimitivePropertyInfo) Width.INFO, "12pt");
		assertEquals(12.0, ((AbsoluteLengthValue) v).getLength(), 1e-9);
		assertSame(KeywordValue.AUTO, parse((AbstractPrimitivePropertyInfo) Width.INFO, "auto"));
	}

	public void testFitContentRejectsBadArguments() {
		for (final String bad : new String[] { "fit-content()", "fit-content(-1px)", "fit-content(auto)",
				"fit-content(1px 2px)", "fit-content(3)" }) {
			try {
				parse((AbstractPrimitivePropertyInfo) Width.INFO, bad);
				fail("must be rejected: " + bad);
			} catch (PropertyException e) {
				// expected
			}
		}
	}

	public void testLayoutConversionTreatsKeywordAsAutoLength() {
		assertSame(Length.AUTO_LENGTH, BoxValueUtils.toLength(KeywordValue.MAX_CONTENT));
		assertSame(Length.AUTO_LENGTH, BoxValueUtils.toLength(KeywordValue.MIN_CONTENT));
		assertSame(Length.AUTO_LENGTH, BoxValueUtils.toLength(KeywordValue.FIT_CONTENT));
		assertEquals(LengthType.AUTO,
				BoxValueUtils.toDimension(KeywordValue.MAX_CONTENT, KeywordValue.AUTO).getWidthType());
		assertNull(BoxValueUtils.toIntrinsicSize(KeywordValue.AUTO));
		assertNull(BoxValueUtils.toIntrinsicSize(AbsoluteLengthValue.ZERO));
	}

	public void testIntrinsicSizeConversion() throws Exception {
		assertSame(IntrinsicSize.MAX_CONTENT, BoxValueUtils.toIntrinsicSize(KeywordValue.MAX_CONTENT));
		assertSame(IntrinsicSize.MIN_CONTENT, BoxValueUtils.toIntrinsicSize(KeywordValue.MIN_CONTENT));
		assertSame(IntrinsicSize.FIT_CONTENT, BoxValueUtils.toIntrinsicSize(KeywordValue.FIT_CONTENT));
		final Value fit = parse((AbstractPrimitivePropertyInfo) Width.INFO, "fit-content(50%)");
		final IntrinsicSize bounded = BoxValueUtils.toIntrinsicSize(fit);
		assertEquals(IntrinsicSize.Kind.FIT_CONTENT, bounded.kind());
		assertTrue(bounded.hasArgument());
		assertEquals(LengthType.RELATIVE, bounded.argument().getType());
		assertEquals(0.5, bounded.argument().getLength(), 1e-9);
		// 引数が長さでなくなった(attr()解決失敗)場合は引数無し
		assertSame(IntrinsicSize.FIT_CONTENT, BoxValueUtils.toIntrinsicSize(new FitContentValue(KeywordValue.NONE)));
	}

	public void testResolve() {
		// min-content=40, max-content=100
		assertEquals(100.0, IntrinsicSize.MAX_CONTENT.resolve(40, 100, 70), 1e-9);
		assertEquals(40.0, IntrinsicSize.MIN_CONTENT.resolve(40, 100, 70), 1e-9);
		assertEquals(70.0, IntrinsicSize.FIT_CONTENT.resolve(40, 100, 70), 1e-9);
		assertEquals(100.0, IntrinsicSize.FIT_CONTENT.resolve(40, 100, 500), 1e-9);
		assertEquals(40.0, IntrinsicSize.FIT_CONTENT.resolve(40, 100, 10), 1e-9);
	}
}
