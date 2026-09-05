package jp.cssj.test.unit._0370_PAGE_CONTENT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.helger.css.decl.CSSPageRule;
import com.helger.css.decl.CSSUnknownRule;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.decl.ICSSPageRuleMember;
import com.helger.css.decl.ICSSTopLevelRule;
import com.helger.css.reader.CSSReader;
import com.helger.css.reader.CSSReaderSettings;
import com.helger.css.reader.errorhandler.DoNothingCSSParseErrorHandler;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * running-elements R0 の解析プローブです。
 *
 * <p>
 * ph-css の未知規則の扱いはライブラリ更新で変わり得るため、現時点の結果そのものは
 * assert せず stderr へ分類して出す。foliojet4 の既知の契約である旧
 * {@code @page :-cssj-page-content} の廃止警告だけを固定する。
 * </p>
 */
public class PageContentAtRuleParseProbeTest extends TestCase {
	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	private static final String PAGE_CONTENT_BODY = "{content:counter(page);bottom:0;width:100%;text-align:center}";
	private static final String NESTED_CSS = "@page{@-cssj-page-content footer" + PAGE_CONTENT_BODY + "}";
	private static final String TOP_LEVEL_CSS = "@-cssj-page-content footer" + PAGE_CONTENT_BODY;
	private static final String DEPRECATED_CSS = "@page :-cssj-page-content{margin:0}";

	private enum Outcome {
		UNKNOWN("(a) CSSUnknownRule として届く"),
		DROPPED("(b) 無言で捨てられる"),
		EXCEPTION("(c) 例外になる");

		private final String description;

		Outcome(final String description) {
			this.description = description;
		}
	}

	private record ParseProbe(Outcome outcome, String detail) {
	}

	private record Message(short code, String[] args, String text) {
	}

	private record BuilderProbe(int pdfBytes, List<Message> messages, Exception failure) {
	}

	/** ph-css API と foliojet4 の CSSStyleSheetBuilder 経路を同じ入力で観察する。 */
	public void testPageContentAtRuleParsePaths() throws Exception {
		final ParseProbe nested = probeNestedRule();
		final ParseProbe top = probeTopLevelRule();
		printParse("ph-css / @page 内", nested);
		printParse("ph-css / トップレベル", top);

		final BuilderProbe nestedBuilder = convertWithAuthorCss(NESTED_CSS, "nested");
		final BuilderProbe topBuilder = convertWithAuthorCss(TOP_LEVEL_CSS, "top-level");
		printBuilder("CSSStyleSheetBuilder / @page 内", nested, nestedBuilder);
		printBuilder("CSSStyleSheetBuilder / トップレベル", top, topBuilder);

		final BuilderProbe deprecated = convertWithAuthorCss(DEPRECATED_CSS, "deprecated-selector");
		System.err.println("[running R0] CSSStyleSheetBuilder / 旧セレクタ: " + describe(deprecated));
		assertTrue("@page :-cssj-page-content の廃止警告がありません: " + describe(deprecated),
				deprecated.messages().stream().anyMatch(PageContentAtRuleParseProbeTest::isDeprecatedWarning));
	}

	private static ParseProbe probeNestedRule() {
		try {
			final CascadingStyleSheet sheet = parse(NESTED_CSS);
			if (sheet == null) {
				return new ParseProbe(Outcome.EXCEPTION, "CSSReader が null を返した");
			}
			final List<String> rules = new ArrayList<>();
			for (final ICSSTopLevelRule rule : sheet.getAllRules()) {
				rules.add(rule.getClass().getName());
				if (rule instanceof CSSPageRule page) {
					final List<String> members = new ArrayList<>();
					for (final ICSSPageRuleMember member : page.getAllMembers()) {
						members.add(member.getClass().getName());
						if (member instanceof CSSUnknownRule unknown) {
							return new ParseProbe(Outcome.UNKNOWN, unknown(unknown) + ", members=" + members);
						}
					}
					return new ParseProbe(Outcome.DROPPED, "page members=" + members + ", rules=" + rules);
				}
			}
			return new ParseProbe(Outcome.DROPPED, "CSSPageRule 自体がない。rules=" + rules);
		} catch (final RuntimeException e) {
			return new ParseProbe(Outcome.EXCEPTION, exception(e));
		}
	}

	private static ParseProbe probeTopLevelRule() {
		try {
			final CascadingStyleSheet sheet = parse(TOP_LEVEL_CSS);
			if (sheet == null) {
				return new ParseProbe(Outcome.EXCEPTION, "CSSReader が null を返した");
			}
			final List<String> rules = new ArrayList<>();
			for (final ICSSTopLevelRule rule : sheet.getAllRules()) {
				rules.add(rule.getClass().getName());
				if (rule instanceof CSSUnknownRule unknown) {
					return new ParseProbe(Outcome.UNKNOWN, unknown(unknown) + ", rules=" + rules);
				}
			}
			return new ParseProbe(Outcome.DROPPED, "rules=" + rules);
		} catch (final RuntimeException e) {
			return new ParseProbe(Outcome.EXCEPTION, exception(e));
		}
	}

	private static CascadingStyleSheet parse(final String css) {
		final CSSReaderSettings settings = new CSSReaderSettings().setBrowserCompliantMode(true)
				.setCustomErrorHandler(new DoNothingCSSParseErrorHandler());
		return CSSReader.readFromStringReader(css, settings);
	}

	private static BuilderProbe convertWithAuthorCss(final String css, final String id) throws Exception {
		final String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>" + css
				+ "</style></head><body>probe</body></html>";
		final List<Message> messages = new ArrayList<>();
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		final DirectSession session = (DirectSession) new DirectDriver().getSession(URI.create("copper:direct:"),
				null);
		Exception failure = null;
		try {
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.setMessageHandler((code, args, text) -> messages
					.add(new Message(code, args == null ? new String[0] : args.clone(), text)));
			session.property("input.include", "**");
			CTISessionHelper.transcodeStream(session,
					new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)),
					URI.create("file:///running-r0-" + id + ".html"), "text/html", "UTF-8");
		} catch (final Exception e) {
			failure = e;
		} finally {
			try {
				session.close();
			} catch (final Exception e) {
				if (failure == null) {
					failure = e;
				} else {
					failure.addSuppressed(e);
				}
			}
		}
		return new BuilderProbe(out.size(), List.copyOf(messages), failure);
	}

	private static boolean isDeprecatedWarning(final Message message) {
		if (message.code() != MessageCodes.WARN_BAD_CSS_SYNTAX) {
			return false;
		}
		return Arrays.stream(message.args()).anyMatch(arg -> arg != null && arg.contains("-cssj-page-content"));
	}

	private static void printParse(final String path, final ParseProbe probe) {
		System.err.println("[running R0] " + path + ": " + probe.outcome().description + "; " + probe.detail());
	}

	private static void printBuilder(final String path, final ParseProbe parser, final BuilderProbe builder) {
		final Outcome outcome = builder.failure() == null ? parser.outcome() : Outcome.EXCEPTION;
		System.err.println("[running R0] " + path + ": " + outcome.description + "; " + describe(builder)
				+ "; direct AST=" + parser.detail());
	}

	private static String describe(final BuilderProbe probe) {
		final StringBuilder s = new StringBuilder("pdfBytes=").append(probe.pdfBytes()).append(", messages=[");
		for (int i = 0; i < probe.messages().size(); ++i) {
			if (i != 0) {
				s.append(", ");
			}
			final Message m = probe.messages().get(i);
			s.append(m.code() & 0xFFFF).append(':').append(Arrays.toString(m.args()));
		}
		s.append(']');
		if (probe.failure() != null) {
			s.append(", failure=").append(exception(probe.failure()));
		}
		return s.toString();
	}

	private static String unknown(final CSSUnknownRule rule) {
		return "declaration=" + rule.getDeclaration() + ", parameters=" + rule.getParameterList() + ", body="
				+ rule.getBody();
	}

	private static String exception(final Throwable e) {
		return e.getClass().getName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
	}
}
