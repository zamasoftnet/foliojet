package net.zamasoft.foliojet.css;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.zamasoft.foliojet.css.property.ElementPropertySet;
import net.zamasoft.foliojet.css.property.PrimitivePropertyInfo;
import net.zamasoft.foliojet.css.token.CssToken;
import net.zamasoft.foliojet.css.value.Value;
import net.zamasoft.foliojet.css.impl.property.font.CSSFontFamily;
import net.zamasoft.foliojet.css.impl.property.font.CSSFontStyle;
import net.zamasoft.foliojet.css.impl.property.font.FontSize;
import net.zamasoft.foliojet.css.impl.property.font.FontWeight;
import net.zamasoft.foliojet.css.impl.property.ext.CSSJFontPolicy;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.font.FontFamilyList;
import net.zamasoft.pdfg2d.gc.font.FontPolicyList;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Direction;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Style;
import net.zamasoft.pdfg2d.gc.font.FontStyle.Weight;
import net.zamasoft.foliojet.css.value.KeywordValue;

/**
 * A CSS style.
 * 
 * @author MIYABE Tatsuhiko
 */
public class CSSStyle {
	public static final byte MODE_NORMAL = 0;
	public static final byte MODE_WEAK = -1;
	public static final byte MODE_IMPORTANT = 1;

	/**
	 * The style of an anonymous box.
	 * 
	 * @author MIYABE Tatsuhiko
	 */
	static class AnonStyle extends CSSStyle {
		AnonStyle() {
			// empty
		}

		public boolean isAnonStyle() {
			return true;
		}

		public String toString() {
			return "ANON:" + super.toString();
		}
	}

	static class InsertedAnonStyle extends AnonStyle {
		public boolean isInsertedAnonStyle() {
			return true;
		}
	}

	/**
	 * The corresponding markup language element.
	 */
	private CSSElement ce;

	/**
	 * The target UA.
	 */
	private UserAgent ua;

	/**
	 * The parent style.
	 */
	private CSSStyle parentStyle;

	private Value[] values = null;
	private Value[] computedValues = null;
	/** Records declarations consumed (cleared) by {@link #get} (for {@link #isDeclared}). */
	private java.util.BitSet consumedDeclared = null;
	/** Records consumed declarations whose value was {@code inherit} (for {@link #isDeclaredInherit}). */
	private java.util.BitSet consumedInherit = null;
	private boolean[] importants = null;

	/**
	 * Cascade order of the values stored on this element (2026-10-08): the n-th value {@link #set} stores gets n. A
	 * physical property and its logical counterpart (margin-left and margin-inline-start) set the same thing, and the
	 * later declaration wins (CSS Logical 1 §4), which needs the order across their separate slots. Allocated at the
	 * first logical property; values stored before it keep 0, which is earlier than any later one. Elements without
	 * logical properties carry nothing.
	 */
	private int[] declarationOrder = null;
	private int declarationCount = 0;

	/** Added to an {@code !important} declaration's order: important declarations win over normal ones. */
	private static final int IMPORTANT_RANK = 1 << 30;

	private FontStyle fontStyle = null;

	/**
	 * The logical footnote identifier (footnotes F4, an engine-owned side channel not exposed through CSS;
	 * see {@code Params.footnoteId}). StyleEventMachine sets the same ID
	 * on the source footnote element's style and its ::footnote-call pseudo-style;
	 * {@code BoxStyleMapper.setupParams} copies it to the box params. Defaults to -1.
	 */
	public long footnoteId = -1;

	/**
	 * Declared values of custom properties (--name): raw token sequences with var() still unresolved.
	 * Managed separately from the regular property values[]/computedValues[]
	 * (the fixed code space assigned by ElementPropertySet.CODES). Custom property names
	 * are arbitrary and unbounded per document, whereas CODES is a static registry shared across the JVM.
	 * Dynamic code assignment was rejected because it could cause code spaces to diverge
	 * between CSSStyle instances in concurrently processed documents (see the development plan).
	 */
	private Map<String, List<CssToken>> customProperties = null;
	private Set<String> importantCustomProperties = null;

	/**
	 * The value of a custom property declared {@code initial} (the guaranteed-invalid value, css-variables-1 §2.2):
	 * it hides the ancestors' value, and {@code var()} takes its fallback. Compared by identity.
	 */
	private static final List<CssToken> GUARANTEED_INVALID = java.util.Collections
			.unmodifiableList(new java.util.ArrayList<>());

	/**
	 * The source of custom properties for parentless styles (page and margin boxes): the root element's style.
	 * On 2026-10-06, jigensha reported that variables declared on {@code :root} failed to resolve in {@code @page}
	 * margin boxes, so page numbers used the default font. css-page-3 §6 makes the page context inherit from the root.
	 * As before, other inherited properties use initial values (allowed by that section); only variables are looked up.
	 */
	private CSSStyle customPropertyFallback = null;

	/** Sets the source of custom properties for a parentless style. */
	public void setCustomPropertyFallback(final CSSStyle fallback) {
		this.customPropertyFallback = fallback;
	}

	public static CSSStyle getCSSStyle(UserAgent ua, CSSStyle parentStyle, CSSElement ce) {
		CSSStyle style = new CSSStyle();
		style.init(ce, ua, parentStyle);
		return style;
	}

	/** Sets a computed value without recomputing it. Only for restoring immutable templates. */
	public void restoreComputed(final PrimitivePropertyInfo info, final Value value, final boolean declared) {
		final short code = ElementPropertySet.getCode(info);
		if (code < 0) {
			throw new IllegalArgumentException(info.getName());
		}
		if (this.computedValues == null) {
			this.computedValues = new Value[ElementPropertySet.getCodeSize()];
		}
		this.computedValues[code] = value;
		if (this.values != null) {
			this.values[code] = null;
		}
		if (this.consumedDeclared == null) {
			this.consumedDeclared = new java.util.BitSet(ElementPropertySet.getCodeSize());
		}
		this.consumedDeclared.set(code, declared);
		if (this.consumedInherit != null) {
			this.consumedInherit.clear(code);
		}
		this.fontStyle = null;
	}

	/**
	 * Computes a cached value once more from itself, when something it depends on is set after it was computed
	 * (2026-10-08). An inline SVG gets its image at its end tag, after the cascade computed {@code display}; the
	 * computed value turns grid/flex/table into block for images ({@code Display#getComputedValue}). Does nothing
	 * if the value is not computed yet.
	 */
	public void recompute(final PrimitivePropertyInfo info) {
		final short code = ElementPropertySet.getCode(info);
		if (code >= 0 && this.computedValues != null && this.computedValues[code] != null) {
			this.computedValues[code] = info.getComputedValue(this.computedValues[code], this);
		}
	}

	private static CSSStyle getAnonStyle(CSSElement anone, UserAgent ua, CSSStyle parentStyle, boolean inserted) {
		final AnonStyle style;
		if (inserted) {
			style = new InsertedAnonStyle();
		} else {
			style = new AnonStyle();
		}
		style.init(anone, ua, parentStyle);
		return style;
	}

	private CSSStyle() {
		// empty
	}

	protected void init(CSSElement ce, UserAgent ua, CSSStyle parentStyle) {
		this.ce = ce;
		this.ua = ua;
		this.parentStyle = parentStyle;
	}

	public CSSElement getCSSElement() {
		return this.ce;
	}

	public UserAgent getUserAgent() {
		return this.ua;
	}

	public CSSStyle getParentStyle() {
		return this.parentStyle;
	}

	public CSSStyle getRootStyle() {
		if (this.parentStyle == null) {
			return this;
		}
		return this.parentStyle.getRootStyle();
	}

	public CSSStyle getExplicitStyle() {
		if (!this.isAnonStyle()) {
			return this;
		}
		return this.parentStyle.getExplicitStyle();
	}

	/**
	 * Inserts an anonymous style above this style.
	 * 
	 * @return
	 */
	public CSSStyle insertAnonStyle(CSSElement anone) {
		return this.parentStyle = CSSStyle.getAnonStyle(anone, this.ua, this.parentStyle, true);
	}

	/**
	 * Inserts an anonymous style below this style.
	 * 
	 * @return
	 */
	public CSSStyle inheritAnonStyle(CSSElement anone) {
		return CSSStyle.getAnonStyle(anone, this.ua, this, false);
	}

	/**
	 * Removes the anonymous style above this style.
	 */
	public void removeAnonStyle() {
		assert this.parentStyle.isAnonStyle();
		this.parentStyle = this.parentStyle.parentStyle;
	}

	public boolean isAnonStyle() {
		return false;
	}

	public boolean isInsertedAnonStyle() {
		return false;
	}

	public Value get(PrimitivePropertyInfo info) {
		short code = ElementPropertySet.getCode(info);
		if (code == -1) {
			// fail-loud (2026-08-01): Detect missing registrations as in set (production continues with the default value).
			assert false : "カスケード用コード未割当のプロパティがgetされました(登録漏れ): " + info.getName();
			return info.getDefault(this);
		}
		// The upward inheritance search originally recursed through this.parentStyle.get(info),
		// but deeply nested documents (e.g., legal HTML with over a thousand nested clause numbers)
		// caused actual StackOverflowError crashes. It was rewritten as an iteration
		// independent of stack depth (2026-07-18). Semantics remain identical to the recursive version:
		// clear values[code] at each level as soon as it is read, then cache the final value
		// in computedValues[code] down through the child levels, transforming it with
		// getComputedValue one level at a time.
		java.util.List<CSSStyle> chain = new java.util.ArrayList<CSSStyle>();
		CSSStyle style = this;
		Value resolved;
		for (;;) {
			Value cached = style.computedValues != null ? style.computedValues[code] : null;
			if (cached != null) {
				resolved = cached;
				break;
			}
			Value raw = style.values != null ? style.values[code] : null;
			if (raw != null) {
				// Inheritance (clear as soon as read). Record a consumed bit so that isDeclared
				// can still report whether a declaration exists after clearing.
				style.values[code] = null;
				if (style.consumedDeclared == null) {
					style.consumedDeclared = new java.util.BitSet(ElementPropertySet.getCodeSize());
				}
				style.consumedDeclared.set(code);
			}
			if (raw == KeywordValue.UNSET) {
				// unset: equivalent to inherit for inherited properties and initial for non-inherited properties
				// (CSS Cascading and Inheritance)
				raw = info.isInherited() ? KeywordValue.INHERIT : KeywordValue.INITIAL;
			}
			if (raw == KeywordValue.INHERIT) {
				if (style.consumedInherit == null) {
					style.consumedInherit = new java.util.BitSet(ElementPropertySet.getCodeSize());
				}
				style.consumedInherit.set(code);
			}
			if (raw == KeywordValue.INITIAL) {
				// initial: always use the property's initial value without inheriting
				resolved = info.getDefault(style);
				chain.add(style);
				break;
			}
			boolean needsParent = raw != null ? raw == KeywordValue.INHERIT
					: (style.parentStyle != null && info.isInherited());
			if (!needsParent || style.parentStyle == null) {
				// For the default case, inherit or use the default value
				resolved = (raw != null && !needsParent) ? raw : info.getDefault(style);
				chain.add(style);
				break;
			}
			chain.add(style);
			style = style.parentStyle;
		}
		for (int i = chain.size() - 1; i >= 0; --i) {
			CSSStyle level = chain.get(i);
			// Computed value
			resolved = info.getComputedValue(resolved, level);
			if (level.computedValues == null) {
				level.computedValues = new Value[ElementPropertySet.getCodeSize()];
			}
			level.computedValues[code] = resolved;
		}
		return resolved;
	}

	/**
	 * Returns whether a property is explicitly declared directly on this style
	 * (without following inheritance). Used to determine precedence when a logical property
	 * (such as margin-inline-start) and its physical counterpart (such as margin-top) refer to the same side,
	 * and to determine the Flex automatic minimum size (§4.5, presence of min declarations).
	 * <p>
	 * {@link #get} clears values[code] at this style level as soon as it is read
	 * (as part of inheritance resolution), but records a consumed bit when clearing.
	 * This check therefore returns the same result before or after get. Previously, it had
	 * to run before get; querying a FlexItemSpec after building BlockParams (MinWidth.get)
	 * always misreported the min-width declaration as absent on that path, making flex items
	 * with empty flow content ignore min-width and shrink to width 0 (2026-08-07;
	 * discovered when ranking badges disappeared on yahoo.co.jp).
	 * </p>
	 */
	public boolean isDeclared(PrimitivePropertyInfo info) {
		short code = ElementPropertySet.getCode(info);
		if (code == -1) {
			return false;
		}
		if (this.values != null && this.values[code] != null) {
			return true;
		}
		return this.consumedDeclared != null && this.consumedDeclared.get(code);
	}

	/**
	 * Whether the value declared directly on this style is {@code inherit} ({@code unset} on an inherited property),
	 * before or after {@link #get} (2026-10-08). A physical property and its logical counterpart share one computed
	 * value, so an explicit {@code inherit} of either takes the parent's value of the pair, not of the parent's slot
	 * with the same name ({@link net.zamasoft.foliojet.css.impl.property.box.LogicalSide#resolve}).
	 */
	public boolean isDeclaredInherit(PrimitivePropertyInfo info) {
		final short code = ElementPropertySet.getCode(info);
		if (code == -1) {
			return false;
		}
		final Value raw = this.values != null ? this.values[code] : null;
		if (raw != null) {
			return raw == KeywordValue.INHERIT || (raw == KeywordValue.UNSET && info.isInherited());
		}
		return this.consumedInherit != null && this.consumedInherit.get(code);
	}

	/**
	 * Records a custom property (--name) declaration. Retains the value
	 * as a raw token sequence without type validation (actual var() resolution is deferred until use,
	 * {@link net.zamasoft.foliojet.css.property.DeferredProperty#applyProperty}
	 * ). !important priority follows the same rule as regular {@link #set}:
	 * once important, ignore subsequent NORMAL declarations.
	 */
	public void setCustomProperty(String name, List<CssToken> tokens, byte mode) {
		if (mode == MODE_IMPORTANT) {
			if (this.importantCustomProperties == null) {
				this.importantCustomProperties = new HashSet<String>();
			}
			this.importantCustomProperties.add(name);
		} else if (this.importantCustomProperties != null && this.importantCustomProperties.contains(name)) {
			return;
		}
		// CSS-wide keywords (2026-10-09; stripe-docs declares --col-repeat: initial for
		// repeat(var(--col-repeat, auto-fill), ...)): they used to be substituted as the word itself, which made
		// the declaration that used the variable invalid. initial is the guaranteed-invalid value; inherit, unset
		// and revert take the parent's value, as custom properties inherit and the UA declares none.
		if (tokens.size() == 1 && tokens.get(0) instanceof CssToken.Keyword keyword) {
			if (keyword == CssToken.Keyword.INITIAL) {
				tokens = GUARANTEED_INVALID;
			} else {
				if (this.customProperties != null) {
					this.customProperties.remove(name);
				}
				return;
			}
		} else if (tokens.size() == 1 && tokens.get(0) instanceof CssToken.Ident ident
				&& (ident.is("revert") || ident.is("revert-layer"))) {
			if (this.customProperties != null) {
				this.customProperties.remove(name);
			}
			return;
		}
		if (this.customProperties == null) {
			this.customProperties = new HashMap<String, List<CssToken>>();
		}
		this.customProperties.put(name, tokens);
	}

	/**
	 * Resolves a custom property value, accounting for inheritance from ancestors.
	 * Returns null if not found. Unlike regular {@link #get}, it does not
	 * clear the value after reading, because multiple descendants may independently
	 * read the same ancestor's value repeatedly.
	 */
	public List<CssToken> getCustomProperty(String name) {
		final CSSStyle owner = this.getCustomPropertyOwner(name);
		final List<CssToken> tokens = owner == null ? null : owner.customProperties.get(name);
		return tokens == GUARANTEED_INVALID ? null : tokens;
	}

	/**
	 * Returns the style that <b>declares</b> this custom property
	 * (added on 2026-08-03). Returns null if not found.
	 *
	 * <p>
	 * {@code var()} within the value must resolve in the <b>declaring element's context</b>
	 * (CSS Variables 1: a custom property's computed value is the
	 * token sequence after {@code var()} substitution, computed <b>before inheritance</b>).
	 * If an ancestor declares {@code --y: calc(var(--x) + 1px)} and a child changes
	 * only {@code --x}, the inherited {@code --y} retains the value computed
	 * with the <b>ancestor's</b> {@code --x}. Chrome, Firefox, and Safari all follow
	 * the specification (confirmed in an independent consultation on 2026-08-03).
	 * </p>
	 */
	public CSSStyle getCustomPropertyOwner(String name) {
		for (CSSStyle style = this; style != null; style = style.parentStyle) {
			if (style.customProperties != null && style.customProperties.get(name) != null) {
				return style;
			}
			if (style.parentStyle == null && style.customPropertyFallback != null) {
				return style.customPropertyFallback.getCustomPropertyOwner(name);
			}
		}
		return null;
	}

	public void set(PrimitivePropertyInfo info, Value value) {
		this.set(info, value, MODE_NORMAL);
	}

	public void set(PrimitivePropertyInfo info, Value value, byte mode) {
		short code = ElementPropertySet.getCode(info);
		if (code == -1) {
			// fail-loud (2026-08-01): A supported property without an assigned code
			// means a missing registration (ElementPropertySet reg/regCode); fail immediately
			// in development and tests (-ea). This prevents a recurrence of the @page size incident,
			// where silent discarding led to a lengthy debugging session. Production (without -ea)
			// continues with WARN as before (to avoid crashes). Static coverage checks
			// are performed by PropertyCodeRegistryTest.
			assert false : "カスケード用コード未割当のプロパティがsetされました(登録漏れ): " + info.getName();
			this.ua.message(MessageCodes.WARN_UNSUPPORTED_CSS_PROPERTY, info.getName());
			return;
		}
		if (mode == MODE_IMPORTANT) {
			if (this.importants == null) {
				this.importants = new boolean[ElementPropertySet.getCodeSize()];
			}
			this.importants[code] = true;
		} else {
			if (this.importants != null && this.importants[code]) {
				return;
			}
		}
		if (this.values == null) {
			this.values = new Value[ElementPropertySet.getCodeSize()];
		}
		if (mode == MODE_WEAK) {
			if (this.values[code] != null) {
				return;
			}
		}
		this.values[code] = value;
		if (this.computedValues != null) {
			this.computedValues[code] = null;
		}
		if (this.consumedInherit != null) {
			this.consumedInherit.clear(code);
		}
		// A null value is a shorthand's omitted part (border-left: 2px leaves the style out), which resets the
		// property to its initial value: it takes part in the order like any declaration (2026-10-08).
		if (this.declarationOrder == null && isLogical(info)) {
			this.declarationOrder = new int[ElementPropertySet.getCodeSize()];
		}
		if (this.declarationOrder != null) {
			this.declarationOrder[code] = ++this.declarationCount;
		}
	}

	/**
	 * Logical properties by name: margin-block-start, border-inline-end-color, inline-size, min-block-size,
	 * border-start-end-radius...
	 */
	public static boolean isLogical(final PrimitivePropertyInfo info) {
		final String name = info.getName();
		return name.contains("-block") || name.contains("-inline") || name.equals("block-size")
				|| name.equals("inline-size") || name.startsWith("border-start-") || name.startsWith("border-end-");
	}

	/**
	 * The cascade rank of a declaration on this element (2026-10-08): {@code !important} first, then the order of
	 * declaration; 0 if it came before the element's first logical property or was not declared here. A shorthand's
	 * omitted part has a rank but is not {@link #isDeclared declared}.
	 */
	public int declarationRank(final PrimitivePropertyInfo info) {
		final short code = ElementPropertySet.getCode(info);
		if (code == -1 || this.declarationOrder == null) {
			return 0;
		}
		final int order = this.declarationOrder[code];
		return this.importants != null && this.importants[code] ? IMPORTANT_RANK + order : order;
	}

	/** Sets a declaration's cascade rank without a declaration. Only for restoring immutable templates. */
	public void restoreDeclarationRank(final PrimitivePropertyInfo info, final int rank) {
		final short code = ElementPropertySet.getCode(info);
		if (code == -1 || rank == 0) {
			return;
		}
		if (this.declarationOrder == null) {
			this.declarationOrder = new int[ElementPropertySet.getCodeSize()];
		}
		this.declarationOrder[code] = rank;
	}

	/**
	 * The content language (2026-10-07). Pseudo-elements (::before, ::after, ::marker, etc.) use shared
	 * {@link CSSElement} instances without a language, so return the parent element's language. Previously, null
	 * caused language-specific generic family chains, kinsoku (line-breaking rules), and hyphenation to fall back
	 * to Japanese defaults (Japanese glyph forms appeared in {@code content} text in lang=zh or ko documents).
	 */
	public java.util.Locale getLang() {
		for (CSSStyle style = this; style != null; style = style.parentStyle) {
			if (style.ce != null && !style.ce.isPseudoElement()) {
				return style.ce.lang;
			}
		}
		return null;
	}

	public FontStyle getFontStyle() {
		if (this.fontStyle != null) {
			return this.fontStyle;
		}
		FontFamilyList family = CSSFontFamily.get(this);
		double size = FontSize.get(this);
		Style style = CSSFontStyle.get(this);
		Weight weight = FontWeight.get(this);
		Direction direction = net.zamasoft.foliojet.css.impl.property.text.Direction.getFontDirection(this);
		FontPolicyList policy = CSSJFontPolicy.get(this);
		final var alternates = net.zamasoft.foliojet.css.impl.property.font.FontVariantAlternates.get(this);
		final var featureValues = this.ua.getUAContext().getFontFeatureValues();
		// Documents without @font-feature-values use the same method as before, preserving the default path.
		final var alternateFeatures = featureValues.isEmpty() ? alternates.featureSet()
				: alternates.featureSet(featureValues, family.get(0).getName());
		// Override tags from font-variant-* with explicit font-feature-settings tags
		// and normalize them to an OpenType feature sequence (css-fonts-3 priority order).
		final var features = net.zamasoft.foliojet.css.impl.property.font.FontVariantCaps.get(this).featureSet()
				.override(net.zamasoft.foliojet.css.impl.property.font.FontVariantLigatures.get(this).featureSet())
				.override(alternateFeatures)
				.override(net.zamasoft.foliojet.css.impl.property.font.FontVariantEastAsian.get(this).featureSet())
				.override(net.zamasoft.foliojet.css.impl.property.font.FontVariantNumeric.get(this).featureSet())
				// font-kerning:none explicitly disables kern (2026-08-29). font-feature-settings takes precedence.
				.override(net.zamasoft.foliojet.css.impl.property.font.FontKerning.featureSet(this))
				.override(net.zamasoft.foliojet.css.impl.property.font.FontFeatureSettings.get(this));

		// Carry font-stretch (2026-08-29) in FontStyle as a width class (usWidthClass 1..9);
		// pdfg2d font selection chooses the nearest width class among faces tied on italic/weight.
		this.fontStyle = new FontStyleImpl(family, size, style, weight, direction, policy, features,
				net.zamasoft.foliojet.css.impl.property.font.FontSynthesisWeight.get(this),
				net.zamasoft.foliojet.css.impl.property.font.FontSynthesisStyle.get(this),
				net.zamasoft.foliojet.layout.box.params.TypesettingMode.usedTextOrientation(
						net.zamasoft.foliojet.css.impl.property.text.WritingModeVariant.get(this),
						net.zamasoft.foliojet.css.impl.property.text.TextOrientation.get(this)),
				net.zamasoft.foliojet.css.impl.property.font.FontStretch.getWidthClass(this),
				// Content language (2026-08-31). Carried to select language-specific
				// generic family chains. The default chain targets Japanese; without this,
				// Chinese uses Japanese glyph forms and Korean sans-serif uses a Mincho face.
				this.getLang());
		return this.fontStyle;
	}

	public String toString() {
		StringBuilder buff = new StringBuilder(super.toString());
		buff.append("\n").append(this.ce).append("\n");
		if (this.values != null) {
			buff.append("values[");
			for (int i = 0; i < this.values.length; ++i) {
				Value value = this.values[i];
				if (value == null) {
					continue;
				}
				buff.append(value).append(";");
			}
			buff.deleteCharAt(buff.length() - 1);
			buff.append("]\n");
		}
		if (this.computedValues != null) {
			buff.append("computed values[");
			for (int i = 0; i < this.computedValues.length; ++i) {
				Value value = this.computedValues[i];
				if (value == null) {
					continue;
				}
				buff.append(value).append(";");
			}
			buff.deleteCharAt(buff.length() - 1);
			buff.append("]\n");
		}
		buff.deleteCharAt(buff.length() - 1);
		return buff.toString();
	}

}
