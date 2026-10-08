package net.zamasoft.foliojet.css;

import java.awt.Font;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSFontFaceRule;
import com.helger.css.decl.CSSImportRule;
import com.helger.css.decl.CSSLayerRule;
import com.helger.css.decl.CSSMediaExpression;
import com.helger.css.decl.CSSMediaQuery;
import com.helger.css.decl.CSSMediaRule;
import com.helger.css.decl.CSSPageMarginBlock;
import com.helger.css.decl.CSSPageRule;
import com.helger.css.decl.CSSSelector;
import com.helger.css.decl.CSSStyleRule;
import com.helger.css.decl.CSSUnknownRule;
import com.helger.css.decl.CSSSupportsConditionDeclaration;
import com.helger.css.decl.CSSSupportsConditionNegation;
import com.helger.css.decl.CSSSupportsConditionNested;
import com.helger.css.decl.CSSSupportsRule;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.decl.ECSSSupportsConditionOperator;
import com.helger.css.decl.ICSSPageRuleMember;
import com.helger.css.decl.ICSSSupportsConditionMember;
import com.helger.css.decl.ICSSTopLevelRule;
import com.helger.css.reader.CSSReader;

import net.zamasoft.foliojet.css.counterstyle.CounterStyleDef;
import net.zamasoft.foliojet.css.counterstyle.CounterStyleParser;
import net.zamasoft.foliojet.css.font.FontFeatureValues;
import net.zamasoft.foliojet.css.font.FontPaletteValues;
import net.zamasoft.foliojet.css.font.FontPaletteValues.BasePalette;
import com.helger.css.writer.CSSWriterSettings;

import net.zamasoft.foliojet.css.parser.CSSException;
import net.zamasoft.foliojet.css.parser.InputSource;
import net.zamasoft.foliojet.css.parser.SelectorConverter;
import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.FontFacePropertySet;
import net.zamasoft.foliojet.css.property.PagePropertySet;
import net.zamasoft.foliojet.css.selector.Selector;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.token.Tokens;
import net.zamasoft.foliojet.css.util.ColorValueUtils;
import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.css.value.ColorValue;
import net.zamasoft.foliojet.css.impl.property.font.CSSFontFamily;
import net.zamasoft.foliojet.css.impl.property.font.CSSFontStyle;
import net.zamasoft.foliojet.css.impl.property.font.FontWeight;
import net.zamasoft.foliojet.css.impl.property.font.CSSUnicodeRange;
import net.zamasoft.foliojet.css.impl.property.font.Src;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.foliojet.xml.util.XMLUtils;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.pdfg2d.gc.font.FontFace;
import net.zamasoft.pdfg2d.gc.font.FontManager;

/**
 * Builds CSSStyleSheet directly from a stylesheet parsed by ph-css.
 *
 * @author MIYABE Tatsuhiko
 */
public class CSSStyleSheetBuilder {
	private static final Logger LOG = Logger.getLogger(CSSStyleSheetBuilder.class.getName());

	private static final int MAX_DEPTH = 10;

	private final UserAgent ua;

	/** Stack of stylesheet URIs (import depth and cycle detection). */
	private final List<URI> uriStack = new ArrayList<URI>();

	private CSSStyleSheet cssStyleSheet;

	/** Cascade origin of rules to add. Defaults to the document (author) stylesheet. */
	private Origin origin = Origin.AUTHOR;

	public CSSStyleSheetBuilder(UserAgent ua) {
		this.ua = ua;
	}

	public void setCSSStyleSheet(CSSStyleSheet cssStyleSheet) {
		this.cssStyleSheet = cssStyleSheet;
	}

	/**
	 * Sets the cascade origin for rules to be parsed by {@link #parse(InputSource)}.
	 * Switch this before and after loading the user agent default stylesheet.
	 *
	 * @param origin
	 */
	public void setOrigin(Origin origin) {
		this.origin = origin;
	}

	/**
	 * Parses a stylesheet and adds it to the CSSStyleSheet under construction.
	 */
	public void parse(InputSource source) throws IOException, CSSException {
		String css = read(source.getReader());
		css = css.replace("{literal}", "").replace("{/literal}", "");
		// Implicitly close an unclosed comment at the end (see DeclarationParser;
		// ph-css lexical analysis cannot recover here and discards the entire sheet).
		css = DeclarationParser.closeUnterminatedComment(css);
		CascadingStyleSheet sheet = CSSReader.readFromStringReader(css, DeclarationParser.settings());
		if (sheet == null) {
			throw new CSSException("スタイルシートを解析できません");
		}
		final URI uri = URI.create(source.getURI());
		this.uriStack.add(uri);
		try {
			for (CSSImportRule importRule : sheet.getAllImportRules()) {
				this.importStyle(importRule.getLocationString(), toMediaTypes(importRule.getAllMediaQueries()), uri,
						source.getEncoding());
			}
			for (ICSSTopLevelRule rule : sheet.getAllRules()) {
				this.rule(rule, uri, true, Rule.NO_LAYER, null, null);
			}
		} finally {
			this.uriStack.remove(this.uriStack.size() - 1);
		}
	}

	private void rule(ICSSTopLevelRule rule, URI uri, boolean mediaOk, int layer, String layerNamePrefix,
			net.zamasoft.foliojet.css.container.ContainerQuery containerQuery) {
		if (rule instanceof CSSStyleRule styleRule) {
			if (!mediaOk) {
				return;
			}
			this.styleRule(styleRule, uri, layer, null, containerQuery);
		} else if (rule instanceof CSSMediaRule mediaRule) {
			boolean ok = false;
			for (CSSMediaQuery query : mediaRule.getAllMediaQueries()) {
				if (this.evaluateMediaQuery(query)) {
					ok = true;
					break;
				}
			}
			// Propagate a non-matching outer @media/@supports inward (fixed on 2026-07-19:
			// previously, only the inner condition determined the result, so an inner @media
			// that matched independently was applied even when the outer condition did not match).
			for (ICSSTopLevelRule inner : mediaRule.getAllRules()) {
				this.rule(inner, uri, mediaOk && ok, layer, layerNamePrefix, containerQuery);
			}
		} else if (rule instanceof CSSSupportsRule supportsRule) {
			boolean ok = this.evaluateSupports(supportsRule.getAllSupportConditionMembers(), uri, 0);
			for (ICSSTopLevelRule inner : supportsRule.getAllRules()) {
				this.rule(inner, uri, mediaOk && ok, layer, layerNamePrefix, containerQuery);
			}
		} else if (rule instanceof CSSPageRule pageRule) {
			this.page(pageRule, uri, mediaOk);
		} else if (rule instanceof CSSFontFaceRule fontFaceRule) {
			// Preserve previous behavior: register @font-face regardless of media.
			this.fontFace(fontFaceRule, uri);
		} else if (rule instanceof CSSLayerRule layerRule) {
			this.layer(layerRule, uri, mediaOk, layerNamePrefix, containerQuery);
		} else if (rule instanceof CSSUnknownRule unknownRule) {
			// ph-css passes at-rules without dedicated support as name, arguments, and body.
			final String decl = unknownRule.getDeclaration();
			if (mediaOk && "@counter-style".equalsIgnoreCase(decl)) {
				this.counterStyle(unknownRule);
			} else if (mediaOk && "@container".equalsIgnoreCase(decl)) {
				this.container(unknownRule, uri, mediaOk, layer, layerNamePrefix);
			} else if (mediaOk && "@font-feature-values".equalsIgnoreCase(decl)) {
				this.fontFeatureValues(unknownRule);
			} else if (mediaOk && "@font-palette-values".equalsIgnoreCase(decl)) {
				this.fontPaletteValues(unknownRule);
			} else if (mediaOk && "@footnote".equalsIgnoreCase(decl)) {
				// Leniently accept top-level declarations in addition to the specified location inside @page.
				final CSSStyleRule holder = declarationHolder(unknownRule.getBody());
				if (holder != null) {
					this.footnoteArea(holder.getAllDeclarations(), uri);
				}
			}
		}
		// Ignore others (@keyframes, @namespace, and unknown at-rules).
	}

	/** Applies supported @footnote descriptors to the document-wide area (F-1). */
	private void footnoteArea(final List<CSSDeclaration> declarations, final URI uri) {
		net.zamasoft.foliojet.ua.FootnoteArea area = this.ua.getUAContext().getFootnoteArea();
		final List<CSSDeclaration> separator = new ArrayList<CSSDeclaration>();
		for (final CSSDeclaration declaration : declarations) {
			final String property = declaration.getProperty().toLowerCase(Locale.ROOT);
			final List<CssToken> tokens = Tokens.fromExpression(declaration.getExpression());
			final String value = tokens.size() == 1 && tokens.get(0) instanceof CssToken.Ident ident
					? ident.lower() : "";
			boolean supported = true;
			switch (property) {
			case "height":
			case "min-height":
				if ("height".equals(property) && "auto".equals(value)) {
					area = area.withHeight(null);
					break;
				}
				final net.zamasoft.foliojet.css.value.LengthValue length = tokens.size() == 1
						? ValueUtils.toLength(this.ua, tokens.get(0)) : null;
				if (length == null || length.isNegative()) {
					supported = false;
					break;
				}
				// The document-wide area has no element style. Resolve relative lengths with the UA default font.
				final double points = length.toAbsoluteLength(CSSStyle.getCSSStyle(this.ua, null, CSSElement.BEFORE))
						.getLength();
				if (!Double.isFinite(points) || points < 0) {
					supported = false;
				} else {
					area = "height".equals(property) ? area.withHeight(points) : area.withMinHeight(points);
				}
				break;
			case "float":
				if ("bottom".equals(value)) {
					area = area.withPosition(net.zamasoft.foliojet.ua.FootnoteArea.Position.BOTTOM);
				} else if ("top".equals(value)) {
					// Top band (headnotes, 2026-09-11). Works only on pages with vertical writing;
					// there is no path yet to reserve a band at block-start on pages with horizontal writing.
					area = area.withPosition(net.zamasoft.foliojet.ua.FootnoteArea.Position.TOP);
				} else {
					area = area.withPosition(net.zamasoft.foliojet.ua.FootnoteArea.Position.BLOCK_END);
					supported = "block-end".equals(value);
				}
				break;
			case "writing-mode":
				final net.zamasoft.foliojet.layout.box.params.WritingMode flow = switch (value) {
				case "horizontal-tb" -> net.zamasoft.foliojet.layout.box.params.WritingMode.TB;
				case "vertical-rl" -> net.zamasoft.foliojet.layout.box.params.WritingMode.RL;
				case "vertical-lr" -> net.zamasoft.foliojet.layout.box.params.WritingMode.LR;
				default -> null;
				};
				if (flow != null) {
					area = area.withFlow(flow);
				} else {
					supported = false;
				}
				break;
			case "border-top":
			case "border-top-width":
			case "border-top-style":
			case "border-top-color":
				// Separator from the body text (2026-10-04). Interpret values as for elements and compute them together later.
				separator.add(declaration);
				break;
			default:
				supported = false;
				break;
			}
			if (!supported) {
				this.ua.message(MessageCodes.WARN_BAD_CSS_SYNTAX, uri.toString(),
						"未対応の脚注領域の記述子です: " + property + ": "
								+ declaration.getExpression().getAsCSSString(MEDIA_WRITER_SETTINGS, 0));
			}
		}
		if (!separator.isEmpty()) {
			// Compute border-top shorthand and longhand values using the same rules as elements;
			// use the computed width (0 if style is none) and color for the separator.
			final Declaration declaration = DeclarationParser.convert(separator, null,
					ElementPropertySet.getInstance(), this.ua, uri);
			final CSSStyle style = CSSStyle.getCSSStyle(this.ua, null, CSSElement.BEFORE);
			if (declaration != null) {
				declaration.applyProperties(style);
			}
			final net.zamasoft.foliojet.css.impl.property.box.Side top = net.zamasoft.foliojet.css.impl.property.box.Side.TOP;
			final short borderStyle = net.zamasoft.foliojet.css.impl.property.border.BorderStyle.get(style, top);
			// The width value does not account for line style (returns medium even for none), so set it to 0 here.
			final boolean visible = borderStyle != net.zamasoft.foliojet.css.value.BorderStyleValue.NONE
					&& borderStyle != net.zamasoft.foliojet.css.value.BorderStyleValue.HIDDEN;
			area = area.withSeparator(new net.zamasoft.foliojet.ua.FootnoteArea.Separator(
					visible ? net.zamasoft.foliojet.css.impl.property.border.BorderWidth.get(style, top) : 0,
					net.zamasoft.foliojet.css.impl.property.border.BorderColor.get(style,
							net.zamasoft.foliojet.css.impl.property.box.Side.TOP)));
		}
		this.ua.getUAContext().setFootnoteArea(area);
	}

	/**
	 * Author-defined counter styles ({@code @counter-style}, 2026-08-02;
	 * ranked fifth in PLAN §2. Supports Japanese needs such as kanji numerals and iroha, and CSS input from the Web).
	 *
	 * <p>
	 * ph-css passes this rule as {@link CSSUnknownRule} (strings for name, arguments, and body),
	 * so wrap the body in a dummy rule, parse it again, and pass the declaration sequence
	 * to {@link CounterStyleParser}. The registry resides in {@code UAContext};
	 * {@code list-style-type: <name>} only looks up a code by name,
	 * so it does not depend on rule source order.
	 * </p>
	 */
	private void counterStyle(final CSSUnknownRule rule) {
		final String name = rule.getParameterList();
		if (name == null || name.trim().isEmpty()) {
			return;
		}
		final String body = rule.getBody();
		if (body == null) {
			return;
		}
		final CascadingStyleSheet sheet = CSSReader.readFromStringReader("*{" + body + "}",
				DeclarationParser.settings());
		if (sheet == null || sheet.getRuleCount() != 1
				|| !(sheet.getRuleAtIndex(0) instanceof CSSStyleRule holder)) {
			return;
		}
		final List<String[]> descriptors = new ArrayList<>();
		for (final CSSDeclaration declaration : holder.getAllDeclarations()) {
			descriptors.add(new String[] { declaration.getProperty(),
					declaration.getExpression().getAsCSSString(MEDIA_WRITER_SETTINGS, 0) });
		}
		final CounterStyleDef def = CounterStyleParser.parse(descriptors);
		if (def != null) {
			this.ua.getUAContext().getCounterStyles().define(name.trim(), def);
		}
	}

	/** Registers {@code @font-feature-values} in the document's name table. */
	private void fontFeatureValues(final CSSUnknownRule rule) {
		final List<String> families = parseNamedFontFamilies(rule.getParameterList());
		final String body = rule.getBody();
		if (families == null || body == null) {
			return;
		}
		final CascadingStyleSheet inner = CSSReader.readFromStringReader(body, DeclarationParser.settings());
		if (inner == null) {
			return;
		}
		final FontFeatureValues definitions = this.ua.getUAContext().getFontFeatureValues();
		for (final ICSSTopLevelRule member : inner.getAllRules()) {
			if (!(member instanceof CSSUnknownRule block)) {
				continue;
			}
			String blockName = block.getDeclaration();
			if (blockName == null) {
				continue;
			}
			blockName = blockName.startsWith("@") ? blockName.substring(1) : blockName;
			final FontFeatureValues.Type type = FontFeatureValues.Type.fromCssName(blockName);
			final String blockBody = block.getBody();
			final CSSStyleRule holder = type == null ? null : declarationHolder(blockBody);
			if (holder == null) {
				continue;
			}
			for (final CSSDeclaration declaration : holder.getAllDeclarations()) {
				final String featureName = originalDeclarationName(blockBody, declaration);
				if (declaration.isImportant() || isReservedFeatureName(featureName)) {
					continue;
				}
				final int[] indexes = parseFeatureIndexes(type, Tokens.fromExpression(declaration.getExpression()));
				if (indexes != null) {
					definitions.define(families, type, featureName, indexes);
				}
			}
		}
	}

	/**
	 * Parses and registers {@code @font-palette-values}. The resolved result is
	 * accessible from {@code font-palette}, but does not affect rendering.
	 */
	private void fontPaletteValues(final CSSUnknownRule rule) {
		final String name = parseDashedIdent(rule.getParameterList());
		final CSSStyleRule holder = name == null ? null : declarationHolder(rule.getBody());
		if (holder == null) {
			return;
		}
		List<String> families = null;
		BasePalette basePalette = BasePalette.index(0);
		Map<Integer, ColorValue> overrideColors = Map.of();
		for (final CSSDeclaration declaration : holder.getAllDeclarations()) {
			if (declaration.isImportant()) {
				continue;
			}
			final List<CssToken> tokens = Tokens.fromExpression(declaration.getExpression());
			switch (declaration.getProperty().toLowerCase(Locale.ROOT)) {
			case "font-family": {
				final List<String> parsed = parseNamedFontFamilies(tokens);
				if (parsed != null) {
					families = parsed;
				}
				break;
			}
			case "base-palette": {
				final BasePalette parsed = parseBasePalette(tokens);
				if (parsed != null) {
					basePalette = parsed;
				}
				break;
			}
			case "override-colors": {
				final Map<Integer, ColorValue> parsed = this.parseOverrideColors(tokens);
				if (parsed != null) {
					overrideColors = parsed;
				}
				break;
			}
			default:
				break;
			}
		}
		if (families != null) {
			this.ua.getUAContext().getFontPaletteValues().define(name,
					new FontPaletteValues.Definition(families, basePalette, overrideColors));
		}
	}

	private static CSSStyleRule declarationHolder(final String body) {
		if (body == null) {
			return null;
		}
		final CascadingStyleSheet sheet = CSSReader.readFromStringReader("*{" + body + "}",
				DeclarationParser.settings());
		return sheet != null && sheet.getRuleCount() == 1
				&& sheet.getRuleAtIndex(0) instanceof CSSStyleRule holder ? holder : null;
	}

	private static List<String> parseNamedFontFamilies(final String text) {
		if (text == null || text.trim().isEmpty()) {
			return null;
		}
		final CSSStyleRule holder = declarationHolder("font-family:" + text + ";");
		if (holder == null || holder.getAllDeclarations().size() != 1) {
			return null;
		}
		final CSSDeclaration declaration = holder.getAllDeclarations().get(0);
		return "font-family".equalsIgnoreCase(declaration.getProperty())
				? parseNamedFontFamilies(Tokens.fromExpression(declaration.getExpression())) : null;
	}

	private static List<String> parseNamedFontFamilies(final List<CssToken> tokens) {
		final List<List<CssToken>> groups = splitStrictComma(tokens);
		if (groups == null) {
			return null;
		}
		final List<String> families = new ArrayList<>(groups.size());
		for (final List<CssToken> group : groups) {
			if (group.size() == 1 && group.get(0) instanceof CssToken.Str str) {
				if (str.value().isEmpty()) {
					return null;
				}
				families.add(str.value());
				continue;
			}
			final StringBuilder family = new StringBuilder();
			for (final CssToken token : group) {
				if (!(token instanceof CssToken.Ident ident)) {
					return null;
				}
				if (family.length() > 0) {
					family.append(' ');
				}
				family.append(ident.name());
			}
			if (group.size() == 1 && isGenericFamily(family.toString())) {
				return null;
			}
			families.add(family.toString());
		}
		return families.isEmpty() ? null : List.copyOf(families);
	}

	private static boolean isGenericFamily(final String name) {
		return switch (name.toLowerCase(Locale.ROOT)) {
		case "serif", "sans-serif", "cursive", "fantasy", "monospace", "system-ui", "emoji", "math",
				"fangsong", "ui-serif", "ui-sans-serif", "ui-monospace", "ui-rounded" -> true;
		default -> false;
		};
	}

	private static boolean isReservedFeatureName(final String name) {
		return switch (name.toLowerCase(Locale.ROOT)) {
		case "initial", "inherit", "unset", "default", "revert", "revert-layer" -> true;
		default -> false;
		};
	}

	/** Restores declaration names lowercased by ph-css, preserving case from their original source positions. */
	private static String originalDeclarationName(final String body, final CSSDeclaration declaration) {
		final com.helger.css.CSSSourceLocation location = declaration.getSourceLocation();
		if (location == null || !location.hasFirstTokenArea()) {
			return declaration.getProperty();
		}
		final String wrapped = "*{" + body + "}";
		final int start = sourceOffset(wrapped, location.getFirstTokenBeginLineNumber(),
				location.getFirstTokenBeginColumnNumber());
		final int end = sourceOffset(wrapped, location.getFirstTokenEndLineNumber(),
				location.getFirstTokenEndColumnNumber()) + 1;
		if (start < 0 || end <= start || end > wrapped.length()) {
			return declaration.getProperty();
		}
		return Tokens.unescape(wrapped.substring(start, end));
	}

	private static int sourceOffset(final String source, final int line, final int column) {
		if (line < 1 || column < 1) {
			return -1;
		}
		int offset = 0;
		for (int currentLine = 1; currentLine < line; ++currentLine) {
			offset = source.indexOf('\n', offset);
			if (offset < 0) {
				return -1;
			}
			++offset;
		}
		final int result = offset + column - 1;
		return result <= source.length() ? result : -1;
	}

	private static int[] parseFeatureIndexes(final FontFeatureValues.Type type, final List<CssToken> tokens) {
		final int[] indexes = new int[tokens.size()];
		for (int i = 0; i < tokens.size(); ++i) {
			if (!(tokens.get(i) instanceof CssToken.Num num) || !num.integer() || num.value() < 0
					|| num.value() > Integer.MAX_VALUE) {
				return null;
			}
			indexes[i] = num.intValue();
		}
		return switch (type) {
		case STYLESET -> indexes.length > 0 && java.util.Arrays.stream(indexes).allMatch(index -> index <= 20)
				? indexes : null;
		case CHARACTER_VARIANT -> indexes.length >= 1 && indexes.length <= 2 && indexes[0] <= 99 ? indexes : null;
		default -> indexes.length == 1 ? indexes : null;
		};
	}

	private static String parseDashedIdent(final String text) {
		if (text == null) {
			return null;
		}
		final CSSStyleRule holder = declarationHolder("font-palette:" + text + ";");
		if (holder == null || holder.getAllDeclarations().size() != 1) {
			return null;
		}
		final List<CssToken> tokens = Tokens.fromExpression(holder.getAllDeclarations().get(0).getExpression());
		return tokens.size() == 1 && tokens.get(0) instanceof CssToken.Ident ident
				&& ident.name().startsWith("--") && ident.name().length() > 2 ? ident.name() : null;
	}

	private static BasePalette parseBasePalette(final List<CssToken> tokens) {
		if (tokens.size() != 1) {
			return null;
		}
		final CssToken token = tokens.get(0);
		if (token instanceof CssToken.Ident ident) {
			if (ident.is("light")) {
				return BasePalette.light();
			}
			if (ident.is("dark")) {
				return BasePalette.dark();
			}
			return null;
		}
		if (token instanceof CssToken.Num num && num.integer() && num.value() >= 0
				&& num.value() <= Integer.MAX_VALUE) {
			return BasePalette.index(num.intValue());
		}
		return null;
	}

	private Map<Integer, ColorValue> parseOverrideColors(final List<CssToken> tokens) {
		final List<List<CssToken>> groups = splitStrictComma(tokens);
		if (groups == null) {
			return null;
		}
		final Map<Integer, ColorValue> colors = new LinkedHashMap<>();
		for (final List<CssToken> group : groups) {
			if (group.size() != 2 || !(group.get(0) instanceof CssToken.Num index) || !index.integer()
					|| index.value() < 0 || index.value() > Integer.MAX_VALUE) {
				return null;
			}
			final ColorValue color = ColorValueUtils.toColor(this.ua, group.get(1));
			if (color == null) {
				return null;
			}
			colors.put(index.intValue(), color);
		}
		return colors.isEmpty() ? null : colors;
	}

	private static List<List<CssToken>> splitStrictComma(final List<CssToken> tokens) {
		if (tokens.isEmpty()) {
			return null;
		}
		final List<List<CssToken>> groups = new ArrayList<>();
		List<CssToken> group = new ArrayList<>();
		for (final CssToken token : tokens) {
			if (token == CssToken.Op.COMMA) {
				if (group.isEmpty()) {
					return null;
				}
				groups.add(group);
				group = new ArrayList<>();
			} else {
				group.add(token);
			}
		}
		if (group.isEmpty()) {
			return null;
		}
		groups.add(group);
		return groups;
	}

	/**
	 * A {@code @container} query (wired to condition evaluation in 2026-08-15 stage 4;
	 * development record §6).
	 *
	 * <p>
	 * As with {@code @counter-style}, ph-css passes this rule as {@link CSSUnknownRule}
	 * (strings for name, arguments, and body). However, the body differs:
	 * it is a <b>sequence of rules</b> (nested style rules), not declarations,
	 * so parse body directly as an independent stylesheet
	 * without wrapping it in {@code "*{" + body + "}"}. Pass the resulting rules
	 * through the same conditional rule registration path as {@code @media}/
	 * {@code @supports} ({@link #rule}).
	 * </p>
	 *
	 * <p>
	 * Stages 1-3 had no mechanism to record or look up dimension facts, so they only
	 * registered queries as always non-matching. From stage 4, {@link net.zamasoft.foliojet.css.container.ContainerQuery}
	 * (the stage 3 parser) parses the conditions and stores them on the rule
	 * as {@link Rule#getContainerQuery}. {@code StyleContext.merge} performs the actual match
	 * (ancestor container lookup and {@code ContainerFacts} access). Here, always pass {@code mediaOk}
	 * through unchanged and <b>only register</b> the rules (stage 1's "always false" was removed).
	 * </p>
	 *
	 * <p>
	 * For nested {@code @container} (an inner @container enclosed in an outer one),
	 * the inner condition overwrites the outer condition rather than retaining it.
	 * This simplification reflects the lack of a composition rule in the specification
	 * and of examples in the real corpus (each rule can hold only one {@code ContainerQuery}).
	 * </p>
	 */
	private void container(final CSSUnknownRule rule, final URI uri, final boolean mediaOk, final int layer,
			final String layerNamePrefix) {
		final String body = rule.getBody();
		if (body == null) {
			return;
		}
		final CascadingStyleSheet sheet = CSSReader.readFromStringReader(body, DeclarationParser.settings());
		if (sheet == null) {
			return;
		}
		final net.zamasoft.foliojet.css.container.ContainerQuery query = net.zamasoft.foliojet.css.container.ContainerQuery
				.parse(rule.getParameterList(), this.ua);
		for (final ICSSTopLevelRule inner : sheet.getAllRules()) {
			this.rule(inner, uri, mediaOk, layer, layerNamePrefix, query);
		}
	}

	/**
	 * A style rule (CSS Nesting support, 2026-08-02; ranked fourth in PLAN §2).
	 * Flattens the nested AST from ph-css 8.2.
	 *
	 * <p>
	 * Nested selectors join their parent by **text substitution**: replace {@code &} with the parent
	 * selector string; without {@code &}, use a descendant combinator ({@code parent child}). If the parent is
	 * a selector list, expand its Cartesian product. Unlike the specified {@code :is()} desugaring, specificity
	 * is evaluated per branch (the same behavior as preprocessors such as Sass; a documented
	 * simplification). Declarations after nesting (CSSNestedDeclarations) are registered in source order
	 * as additional rules with the same selector, preserving cascade order. Conditional rules
	 * nested inside rules (@media, etc.) are outside the supported subset and are ignored.
	 * </p>
	 */
	private void styleRule(final CSSStyleRule styleRule, final URI uri, final int layer,
			final List<String> parentSelectorTexts,
			final net.zamasoft.foliojet.css.container.ContainerQuery containerQuery) {
		// Combined selector strings (always computed for nested recursion)
		final List<String> selfTexts = new ArrayList<>();
		for (final CSSSelector selector : styleRule.getAllSelectors()) {
			final String text = selector.getAsCSSString();
			if (parentSelectorTexts == null) {
				selfTexts.add(text);
			} else {
				for (final String parent : parentSelectorTexts) {
					selfTexts.add(combineNestedSelector(parent, text));
				}
			}
		}
		final List<Selector> selectors;
		try {
			if (parentSelectorTexts == null) {
				selectors = SelectorConverter.convertList(styleRule.getAllSelectors());
			} else {
				selectors = this.parseSelectorTexts(selfTexts);
			}
		} catch (final CSSException e) {
			// Ignore rules containing unparseable selectors, including their nested rules.
			return;
		}
		if (selectors == null) {
			return;
		}
		if (styleRule.hasDeclarations()) {
			final Declaration declaration = DeclarationParser.convert(styleRule.getAllDeclarations(), null,
					ElementPropertySet.getInstance(), this.ua, uri);
			this.cssStyleSheet.addRule(selectors, declaration, this.origin, layer, containerQuery);
			this.collectSVGStyleRule(selfTexts, styleRule.getAllDeclarations());
		}
		for (final com.helger.css.decl.ICSSNestedRule nested : styleRule.getAllRules()) {
			if (nested instanceof CSSStyleRule nestedStyle) {
				this.styleRule(nestedStyle, uri, layer, selfTexts, containerQuery);
			} else if (nested instanceof com.helger.css.decl.CSSNestedDeclarations nestedDecls) {
				// Declarations after nested rules: append in order with the same selector.
				if (nestedDecls.hasDeclarations()) {
					final Declaration declaration = DeclarationParser.convert(nestedDecls.getAllDeclarations(),
							null, ElementPropertySet.getInstance(), this.ua, uri);
					this.cssStyleSheet.addRule(selectors, declaration, this.origin, layer, containerQuery);
					this.collectSVGStyleRule(selfTexts, nestedDecls.getAllDeclarations());
				}
			}
			// @media/@supports, etc. inside rules are outside the supported subset (ignored).
		}
	}

	/**
	 * Collects a subset of author CSS for inline SVG (2026-08-07).
	 * Inline SVG renders as a separate Batik document, so the HTML document's stylesheet
	 * does not reach it (icon systems that set fill/stroke through CSS classes
	 * turn entirely black). Collect only rules containing SVG presentation declarations
	 * into {@link net.zamasoft.foliojet.ua.DocumentContext},
	 * then inject &lt;style&gt; into the SVG document so Batik can apply the cascade.
	 *
	 * <p>
	 * Pass only selectors that Batik's CSS2-era parser can read (tag, class, id,
	 * descendant, {@code >}, and {@code *}). Discard pseudo-classes, attribute selectors,
	 * and escaped class names. HTML ancestors do not exist inside the SVG document,
	 * so selectors requiring that context cannot be evaluated correctly anyway.
	 * For the same reason, descendant selectors including HTML ancestors fail to match
	 * (avoiding over-application). Discard declarations containing var(), which Batik cannot resolve.
	 * </p>
	 */
	private void collectSVGStyleRule(final List<String> selectorTexts,
			final Iterable<CSSDeclaration> declarations) {
		if (this.origin != Origin.AUTHOR) {
			return;
		}
		List<SVGAuthorCss.Decl> decls = null;
		boolean hasCore = false;
		for (final CSSDeclaration d : declarations) {
			final String prop = d.getProperty().toLowerCase(java.util.Locale.ROOT);
			final boolean core = SVG_PAINT_PROPS.contains(prop);
			if (!core && !SVG_AUX_PROPS.contains(prop)) {
				continue;
			}
			hasCore |= core;
			// Keep values as raw token sequences. var() cannot resolve here
			// (it needs the element context), so defer until injection (see SVGAuthorCss).
			final List<CssToken> tokens = Tokens.fromExpression(d.getExpression());
			if (tokens.isEmpty()) {
				continue;
			}
			if (decls == null) {
				decls = new ArrayList<SVGAuthorCss.Decl>();
			}
			decls.add(new SVGAuthorCss.Decl(prop, tokens, d.isImportant()));
		}
		if (decls == null || !hasCore) {
			// Do not import rules that contain no SVG-specific rendering properties.
			// color/display/font properties are general HTML properties; collecting rules with only these
			// would import thousands of rules on real sites (6,234 on qiita), bloating injection
			// and increasing the chance of encountering values Batik cannot parse (such as display:flex).
			return;
		}
		StringBuilder sels = null;
		for (final String text : selectorTexts) {
			final String t = text.trim();
			if (t.isEmpty() || !BATIK_SAFE_SELECTOR.matcher(t).matches()) {
				continue;
			}
			if (sels == null) {
				sels = new StringBuilder();
			} else {
				sels.append(',');
			}
			sels.append(t);
		}
		if (sels == null) {
			return;
		}
		this.ua.getDocumentContext().getSVGAuthorCss().addRule(new SVGAuthorCss.Rule(sels.toString(), decls));
	}

	/**
	 * SVG-specific rendering properties to import into inline SVG. Accept a rule
	 * if it contains at least one property in this set (see collectSVGStyleRule).
	 */
	private static final java.util.Set<String> SVG_PAINT_PROPS = java.util.Set.of( //
			"fill", "fill-opacity", "fill-rule", //
			"stroke", "stroke-width", "stroke-opacity", "stroke-linecap", "stroke-linejoin", //
			"stroke-miterlimit", "stroke-dasharray", "stroke-dashoffset", //
			"stop-color", "stop-opacity", "opacity", //
			"clip-path", "clip-rule", "mask", "filter", //
			"marker-start", "marker-mid", "marker-end", //
			"text-anchor", "dominant-baseline", "baseline-shift");

	/**
	 * General HTML properties carried along only with accepted rules (for inheritance,
	 * currentColor, and visibility).
	 */
	private static final java.util.Set<String> SVG_AUX_PROPS = java.util.Set.of( //
			"color", "display", "visibility", //
			"font-family", "font-size", "font-weight", "font-style", //
			"letter-spacing", "word-spacing");

	/** Selector forms safe to pass to Batik's CSS2-era parser. */
	private static final java.util.regex.Pattern BATIK_SAFE_SELECTOR = java.util.regex.Pattern
			.compile("[-_a-zA-Z0-9.#*>\\s]+");

	/** Combines nested selectors ({@code &}=parent substitution; otherwise a descendant combinator). */
	private static String combineNestedSelector(final String parent, final String child) {
		final String trimmed = child.trim();
		if (trimmed.indexOf('&') >= 0) {
			return trimmed.replace("&", parent);
		}
		return parent + " " + trimmed;
	}

	/** Reparses combined selector strings (null if unparseable). */
	private List<Selector> parseSelectorTexts(final List<String> texts) throws CSSException {
		final CascadingStyleSheet sheet = CSSReader
				.readFromStringReader(String.join(",", texts) + "{}", DeclarationParser.settings());
		if (sheet == null || sheet.getRuleCount() != 1
				|| !(sheet.getRuleAtIndex(0) instanceof CSSStyleRule reparsed)) {
			return null;
		}
		return SelectorConverter.convertList(reparsed.getAllSelectors());
	}

	/**
	 * Handles {@code @layer} (CSS Cascade Layers, added on 2026-07-21).
	 * Supports both block form ({@code @layer name { ... }} or anonymous {@code @layer { ... }})
	 * and statement form ({@code @layer a, b, c;}, which fixes only layer source order
	 * without accompanying rules). Nested {@code @layer} (another {@code @layer}
	 * inside a layer block) is registered as an independent layer
	 * using the full dot-joined name (e.g., outer {@code a} and inner {@code b}
	 * become {@code "a.b"}), following the same naming approach
	 * as CSS Cascade Layers. Reversing layer priority for {@code !important}
	 * is unsupported (see the support table).
	 */
	private void layer(CSSLayerRule layerRule, URI uri, boolean mediaOk, String layerNamePrefix,
			net.zamasoft.foliojet.css.container.ContainerQuery containerQuery) {
		final List<String> names = layerRule.getAllSelectors();
		if (layerRule.getAllRules().isEmpty()) {
			// Statement form (@layer a, b;) or empty block (@layer a {}):
			// fix source order without adding rules.
			for (String name : names) {
				this.cssStyleSheet.registerNamedLayer(qualifyLayerName(layerNamePrefix, name));
			}
			return;
		}
		// Block form: should have zero names (anonymous) or one name (named).
		final int childLayer;
		final String childPrefix;
		if (names.isEmpty()) {
			childLayer = this.cssStyleSheet.registerAnonymousLayer();
			childPrefix = null;
		} else {
			childPrefix = qualifyLayerName(layerNamePrefix, names.get(0));
			childLayer = this.cssStyleSheet.registerNamedLayer(childPrefix);
		}
		for (ICSSTopLevelRule inner : layerRule.getAllRules()) {
			this.rule(inner, uri, mediaOk, childLayer, childPrefix, containerQuery);
		}
	}

	private static String qualifyLayerName(String prefix, String name) {
		return prefix == null ? name : prefix + "." + name;
	}

	/**
	 * Evaluates one @media query (media type plus feature expressions, implicitly ANDed).
	 * If `not` is present, invert the entire result (SPEC Media Queries).
	 */
	private boolean evaluateMediaQuery(CSSMediaQuery query) {
		String medium = query.getMedium();
		if (medium == null) {
			medium = "all";
		}
		boolean result = this.ua.is(medium.toLowerCase());
		if (result) {
			for (CSSMediaExpression expression : query.getAllMediaExpressions()) {
				if (!this.evaluateMediaExpression(expression)) {
					result = false;
					break;
				}
			}
		}
		return query.isNot() ? !result : result;
	}

	private static final CSSWriterSettings MEDIA_WRITER_SETTINGS = new CSSWriterSettings();

	/**
	 * Evaluates media feature expressions (such as `(min-width: 400px)`). Page dimensions
	 * are statically determined by `output.page-width`/`output.page-height`
	 * before document parsing, so evaluation needs no lookahead and works in 1P.
	 * <p>
	 * ph-css 8.2.1 can parse only up to the equivalent of Media Queries Level 3.
	 * (Rules using Level 4's `or` combinator, `not (...)` without enclosing parentheses,
	 * or range syntax such as `(width &gt;= 400px)` are ignored during parsing.
	 * See the support table.)
	 * </p>
	 */
	private boolean evaluateMediaExpression(CSSMediaExpression expression) {
		String feature = expression.getFeature();
		if (feature == null) {
			return false;
		}
		feature = feature.toLowerCase(java.util.Locale.ROOT);
		if (feature.equals("orientation")) {
			String value = expression.getValue() != null
					? expression.getValue().getAsCSSString(MEDIA_WRITER_SETTINGS, 0).trim().toLowerCase(java.util.Locale.ROOT)
					: null;
			boolean landscape = this.resolvePageWidth() > this.resolvePageHeight();
			if ("landscape".equals(value)) {
				return landscape;
			}
			if ("portrait".equals(value)) {
				return !landscape;
			}
			return false;
		}
		if (expression.getValue() == null) {
			// Boolean context queries without values (e.g., (color), (monochrome)) are unsupported.
			return false;
		}
		String valueText = expression.getValue().getAsCSSString(MEDIA_WRITER_SETTINGS, 0);
		AbsoluteLengthValue value = ValueUtils.toAbsoluteLength(this.ua, false, valueText);
		if (value == null) {
			value = this.mediaFontRelativeLength(valueText);
		}
		if (value == null) {
			return false;
		}
		double length = value.getLength();
		switch (feature) {
		case "width":
			return length == this.resolvePageWidth();
		case "min-width":
			return this.resolvePageWidth() >= length;
		case "max-width":
			return this.resolvePageWidth() <= length;
		case "height":
			return length == this.resolvePageHeight();
		case "min-height":
			return this.resolvePageHeight() >= length;
		case "max-height":
			return this.resolvePageHeight() <= length;
		default:
			// Conservatively treat unsupported features such as aspect-ratio as non-matching.
			return false;
		}
	}

	/**
	 * Resolves em/rem in media queries. Media queries have no element context,
	 * so both units can resolve statically against the <b>initial font size</b>
	 * (medium; Media Queries Level 3 §6). Real sites sometimes
	 * use rem, as in {@code (min-width: 70rem)}; rejecting it here
	 * makes the entire @media fail (observed in a defect where the sidebar
	 * on 5ch.io stayed display:none and disappeared entirely). ex/ch still
	 * require font metrics and remain unsupported (return null).
	 */
	private AbsoluteLengthValue mediaFontRelativeLength(String valueText) {
		String text = valueText.trim().toLowerCase(java.util.Locale.ROOT);
		String number;
		if (text.endsWith("rem")) {
			number = text.substring(0, text.length() - 3);
		} else if (text.endsWith("em")) {
			number = text.substring(0, text.length() - 2);
		} else {
			return null;
		}
		final double ratio;
		try {
			ratio = Double.parseDouble(number.trim());
		} catch (NumberFormatException e) {
			return null;
		}
		return AbsoluteLengthValue.create(this.ua,
				ratio * this.ua.getFontSize(net.zamasoft.foliojet.ua.AbsoluteFontSize.MEDIUM));
	}

	private Double pageWidth, pageHeight;

	private double resolvePageWidth() {
		if (this.pageWidth == null) {
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(this.ua, false,
					UAProps.OUTPUT_PAGE_WIDTH.getString(this.ua));
			this.pageWidth = length != null ? length.getLength() : 0;
		}
		return this.pageWidth;
	}

	private double resolvePageHeight() {
		if (this.pageHeight == null) {
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(this.ua, false,
					UAProps.OUTPUT_PAGE_HEIGHT.getString(this.ua));
			this.pageHeight = length != null ? length.getLength() : 0;
		}
		return this.pageHeight;
	}

	/**
	 * Evaluates @supports conditions (and/or/not and parenthesized nesting). The CSS specification
	 * does not allow and/or to mix at the same level (parentheses are required to mix them),
	 * so a left fold suffices. Nesting depth comes from syntax (levels in handwritten CSS),
	 * so use bounded recursion, as for nested calc() functions.
	 */
	private boolean evaluateSupports(List<ICSSSupportsConditionMember> members, URI uri, int depth) {
		if (depth > MAX_DEPTH || members.isEmpty()) {
			return false;
		}
		Boolean result = null;
		ECSSSupportsConditionOperator pendingOp = null;
		for (ICSSSupportsConditionMember member : members) {
			if (member instanceof ECSSSupportsConditionOperator op) {
				pendingOp = op;
				continue;
			}
			boolean value = this.evaluateSupportsMember(member, uri, depth);
			if (result == null) {
				result = value;
			} else if (pendingOp == ECSSSupportsConditionOperator.OR) {
				result = result || value;
			} else {
				result = result && value;
			}
		}
		return result != null && result;
	}

	private boolean evaluateSupportsMember(ICSSSupportsConditionMember member, URI uri, int depth) {
		if (depth > MAX_DEPTH) {
			return false;
		}
		if (member instanceof CSSSupportsConditionDeclaration declMember) {
			CSSDeclaration declaration = declMember.getDeclaration();
			List<CssToken> tokens = Tokens.fromExpression(declaration.getExpression());
			return ElementPropertySet.getInstance().supports(declaration.getProperty(), tokens, this.ua, uri);
		}
		if (member instanceof CSSSupportsConditionNegation negation) {
			return !this.evaluateSupportsMember(negation.getSupportsMember(), uri, depth + 1);
		}
		if (member instanceof CSSSupportsConditionNested nested) {
			return this.evaluateSupports(nested.getAllMembers(), uri, depth + 1);
		}
		// Syntax that ph-css 8.2.1 cannot parse, such as selector(), is unsupported (non-matching).
		return false;
	}

	private static List<CSSDeclaration> pageDeclarations(CSSPageRule pageRule) {
		List<CSSDeclaration> declarations = new ArrayList<CSSDeclaration>();
		for (ICSSPageRuleMember member : pageRule.getAllMembers()) {
			if (member instanceof CSSDeclaration declaration) {
				declarations.add(declaration);
			}
			// page() handles page margin boxes (@top-center, etc.) separately.
		}
		return declarations;
	}

	private void page(CSSPageRule pageRule, URI uri, boolean mediaOk) {
		if (!mediaOk) {
			return;
		}
		// Named pages N1a (consult-codex-2026-07-31-named-pages.txt Q1):
		// process every entry in the selector list, converting names plus compound pseudo-pages
		// (chapter:first, etc.) to structured PageRule objects. Unsupported pseudo-pages (:nth(), etc.) invalidate only that selector.
		final List<String> selectors = pageRule.getAllSelectors();
		final List<String> names = new ArrayList<String>();
		final List<Byte> masks = new ArrayList<Byte>();
		if (selectors.isEmpty()) {
			names.add(null);
			masks.add((byte) 0);
		} else {
			selector: for (final String selector : selectors) {
				String name = null;
				byte mask = 0;
				final String[] parts = selector.split(":", -1);
				if (!parts[0].isEmpty()) {
					name = parts[0];
				}
				for (int i = 1; i < parts.length; ++i) {
					final String pseudo = parts[i];
					if ("first".equalsIgnoreCase(pseudo)) {
						mask |= net.zamasoft.foliojet.css.PageRule.PSEUDO_FIRST;
					} else if ("left".equalsIgnoreCase(pseudo)) {
						mask |= net.zamasoft.foliojet.css.PageRule.PSEUDO_LEFT;
					} else if ("right".equalsIgnoreCase(pseudo)) {
						mask |= net.zamasoft.foliojet.css.PageRule.PSEUDO_RIGHT;
					} else if ("single".equalsIgnoreCase(pseudo)) {
						mask |= net.zamasoft.foliojet.css.PageRule.PSEUDO_SINGLE;
					} else if ("blank".equalsIgnoreCase(pseudo)) {
						mask |= net.zamasoft.foliojet.css.PageRule.PSEUDO_BLANK;
					} else {
						this.ua.message(MessageCodes.WARN_BAD_CSS_SYNTAX, uri.toString(),
								"未対応のページ擬似クラスです: :" + pseudo);
						continue selector;
					}
				}
				names.add(name);
				masks.add(mask);
			}
		}
		if (names.isEmpty()) {
			return;
		}
		final Declaration declaration = DeclarationParser.convert(pageDeclarations(pageRule), null,
				PagePropertySet.getInstance(), this.ua, uri);
		for (int s = 0; s < names.size(); ++s) {
			final net.zamasoft.foliojet.css.PageRule rule = this.cssStyleSheet.addPageRule(names.get(s),
					masks.get(s), declaration);

			// Page margin boxes (@top-center, etc., css-page-3 §7)
			for (ICSSPageRuleMember member : pageRule.getAllMembers()) {
				if (member instanceof CSSPageMarginBlock marginBlock) {
					if ("@footnote".equalsIgnoreCase(marginBlock.getPageMarginSymbol())) {
						// There is one area per document. Do not duplicate it for later entries in the selector list.
						if (s == 0) {
							this.footnoteArea(marginBlock.getAllDeclarations(), uri);
						}
						continue;
					}
					final MarginBoxName box = MarginBoxName.fromSymbol(marginBlock.getPageMarginSymbol());
					if (box == null) {
						if (s == 0) {
							this.ua.message(MessageCodes.WARN_BAD_CSS_SYNTAX, uri.toString(),
									"未知のページマージンボックスです: " + marginBlock.getPageMarginSymbol());
						}
						continue;
					}
					Declaration boxDeclaration = DeclarationParser.convert(marginBlock.getAllDeclarations(), null,
							ElementPropertySet.getInstance(), this.ua, uri);
					this.cssStyleSheet.addPageRuleMarginBox(rule, box, boxDeclaration);
				}
			}
		}
	}

	private void fontFace(CSSFontFaceRule fontFaceRule, URI uri) {
		Declaration decl = DeclarationParser.convert(fontFaceRule.getAllDeclarations(), null,
				FontFacePropertySet.getInstance(), this.ua, uri);
		if (decl == null) {
			return;
		}
		CSSStyle style = CSSStyle.getCSSStyle(this.ua, null, null);
		decl.applyProperties(style);
		final URI[] uris = Src.get(style);
		if (uris == null) {
			return;
		}
		final FontFace face = new FontFace();
		face.fontFamily = CSSFontFamily.get(style);
		face.fontWeight = FontWeight.get(style);
		face.fontStyle = CSSFontStyle.get(style);
		face.widthClass = net.zamasoft.foliojet.css.impl.property.font.FontStretch.getWidthClass(style);
		face.unicodeRange = CSSUnicodeRange.get(style);
		face.variationSettings = net.zamasoft.foliojet.css.impl.property.font.FontVariationSettings.get(style);
		// The sources are tried when a font style first names the family (2026-10-08): a face the document never uses
		// is not fetched or read, and a source that cannot be read is reported only for a face that is used. Reading
		// every face when the style sheet was parsed doubled the time of a page that imports a CJK web font in 61
		// unicode-range files and never uses it (wordpress-docs: 9 -> 18 seconds).
		face.loader = (pending, reader) -> {
			for (final URI srcUri : uris) {
				try {
					Source src = null;
					try {
						if (srcUri.getScheme() != null && srcUri.getScheme().equals("local-font")) {
							final String name = srcUri.getSchemeSpecificPart();
							final Font local = Font.decode(name);
							// Font.decode never fails: a name not installed comes back as the Dialog logical font. Try
							// the next source instead (2026-10-08); once the policy stopped hiding @font-face fonts,
							// local('Meiryo') on a server without Meiryo set Japanese text in Dialog.
							if (local == null || ("Dialog".equals(local.getFamily(java.util.Locale.ROOT))
									&& !"Dialog".equalsIgnoreCase(name))) {
								continue;
							}
							pending.src = null;
							pending.local = local;
						} else {
							src = this.ua.resolve(srcUri);
							if (!src.exists()) {
								continue;
							}
							pending.local = null;
							pending.src = src;
						}
						reader.read(pending);
						return true;
					} finally {
						if (src != null) {
							this.ua.release(src);
						}
					}
				} catch (Exception e) {
					LOG.log(Level.FINE, "Font error", e);
				}
			}
			pending.src = null;
			pending.local = null;
			this.ua.message(MessageCodes.WARN_MISSING_FONT_FILE, Arrays.asList(uris).toString());
			return false;
		};
		try {
			final FontManager fm = this.ua.getFontManager();
			fm.addFontFace(face);
		} catch (Exception e) {
			LOG.log(Level.FINE, "Font error", e);
		}
	}

	private void importStyle(String href, String mediaTypes, URI baseURI, String encoding) {
		if (!this.ua.is(mediaTypes)) {
			return;
		}
		if (this.uriStack.size() > MAX_DEPTH) {
			this.ua.message(MessageCodes.WARN_DEEP_IMPORT, baseURI.toString(), String.valueOf(MAX_DEPTH));
			return;
		}
		URI uri;
		try {
			uri = URIHelper.resolve(this.ua.getDocumentContext().getEncoding(), baseURI, href);
		} catch (URISyntaxException e) {
			this.ua.message(MessageCodes.WARN_MISSING_CSS_STYLESHEET, href);
			return;
		}
		for (int i = 0; i < this.uriStack.size(); ++i) {
			if (this.uriStack.get(i).equals(uri)) {
				this.ua.message(MessageCodes.WARN_LOOP_IMPORT, baseURI.toString(), uri.toString());
				return;
			}
		}
		try {
			Source source = this.ua.resolve(uri);
			try {
				InputSource inputSource = XMLUtils.toCSSInputSource(source, encoding);
				this.parse(inputSource);
			} finally {
				this.ua.release(source);
			}
		} catch (CSSException e) {
			this.ua.message(MessageCodes.WARN_BAD_CSS_SYNTAX, uri.toString(), e.getMessage());
			LOG.log(Level.FINE, "CSS文法エラー", e);
		} catch (IOException e) {
			this.ua.message(MessageCodes.WARN_MISSING_CSS_STYLESHEET, uri.toString());
			LOG.log(Level.FINE, "CSS読み込みエラー", e);
		}
	}

	private static String toMediaTypes(List<CSSMediaQuery> queries) {
		StringBuilder buff = new StringBuilder();
		for (CSSMediaQuery query : queries) {
			String medium = query.getMedium();
			if (medium == null) {
				continue;
			}
			if (buff.length() > 0) {
				buff.append(' ');
			}
			buff.append(medium);
		}
		return buff.toString();
	}

	private static String read(Reader reader) throws IOException {
		StringBuilder builder = new StringBuilder();
		char[] buffer = new char[4096];
		for (int len = reader.read(buffer); len != -1; len = reader.read(buffer)) {
			builder.append(buffer, 0, len);
		}
		return builder.toString();
	}
}
