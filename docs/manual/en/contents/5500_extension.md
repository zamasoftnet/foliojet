## Extensions

This typesetting engine provides its own processing instructions, CSS properties, CSS functions, XML elements, and XML attributes. It also implements properties
not supported in CSS 2.1 ahead of their addition to CSS 3.

### Processing instruction extensions

**Processing instruction list**

| Name | Version | Description |
| --- | --- | --- |
| <a id="appx-pi-jp.cssj.base-uri"></a>jp.cssj.base-uri | 1.1.0 | Provides an alternative to the HTML base element. Specifies the base URI for resolving relative URIs in the document.<br /> Use it when you pass a document as a stream and no base URI is available, or when you want a base URI different from the document's location. |
| jp.cssj.default-encoding | 1.1.0 | Provides an alternative to the HTML <![CDATA[<meta http-equiv="Content-Type" content="text/html; charset=...">]]> element. Use an encoding name as the value. |
| jp.cssj.default-style-type | 1.1.0 | Provides an alternative to the HTML <![CDATA[<meta name="content-style-type" content="...">]]> element. Use a MIME type as the value. |
| jp.cssj.document-info | 1.1.0 | Provides an alternative to the HTML <![CDATA[<meta name="..." content="...">]]> element. The name,value pseudo-attributes correspond to the name,content attributes. |
| <a id="appx-pi-jp.cssj.property"></a>jp.cssj.property | 2.0.0 | Available only when <span class="ioprop">input.property-pi</span> is true.<br /> Resets an I/O property within the document. Provides name,value pseudo-attributes. If you omit value, the property is set to its default value. |
| <a id="appx-pi-jp.cssj.stylesheet"></a>jp.cssj.stylesheet | 1.1.0 | Provides an alternative to the HTML style element. Provides pseudo-attributes with the same names as the type,media attributes. Enclose the style sheet in '[]'. |

### <a id="appx-cssprop-ext">CSS property extensions</a>

#### CSS properties

<div class="note">

**Use the standard name when one is available.**
When the table below lists multiple names, **the first is the recommended name**.
Names beginning with `-cssj-` were provided before the standard names were established,
and remain accepted for compatibility.

Some features can only be specified with `-cssj-` names (emphasis marks, outlined text, tate-chu-yoko, ruby,
characters subject to kinsoku (line-breaking rules), font types, and others). These either have no corresponding standard name,
or the implementation does not accept it even if one exists.

</div>


**CSS property list**

| Name | Version | Inherited | Default value | Applies to | Description |
| --- | --- | --- | --- | --- | --- |
| <a id="appx-cssprop--cssj-font-policy"></a>-cssj-font-policy | 2.0.0 |  | cid-keyed | All elements | A proprietary property.<br /> Specifies the types of fonts to use. The values are cid-keyed, cid-identity, embedded, and outlines<span class="since">3.1.1</span>. For details, see the <b>Font configuration</b> chapter in the pdfg2d manual. <br /> The old names generic(=cid-keyed), external(=cid-identity), and embed(=embedded) are also accepted. <br /> Since 2.0.1, you can specify multiple values. For example, "embedded cid-keyed" uses a CID-keyed font if no embedded font is found. <br /> Core fonts are always used. However, specifying -core excludes them<span class="since">3.0.0</span>. <br /> When you output <a href="#style-pdf-profiles">PDF/A or PDF/X</a>, this setting is ignored and only embedded fonts are used. |
| background-size<br />-cssj-background-size | 2.0.8 |  | auto | All elements | An implementation based on CSS Backgrounds and Borders Module Level 3.<br /> Specifies the size of a background image. The first value is the image width, and the second is the height. A % value is relative to the element's width or height. You can also specify it with background<span class="since">3.2.16</span>. |
| text-align-last<br />-cssj-text-align-last<br />-epub-text-align-last | 2.0.8 |  | start | All elements | An implementation based on CSS Text Module Level 3.<br /> Specifies text alignment at the end of a paragraph. The values are start, end, left, right, center, and justify.<br /> You can also use the property name <span class="cssprop">-epub-text-align-last</span>. |
| writing-mode<br />-cssj-writing-mode<br />-epub-writing-mode | 3.0.0 |  | horizontal-tb | All elements except table row groups, table column groups, table rows, and table columns | An implementation based on CSS Writing Modes Level 3.<br /> Sets vertical writing or horizontal writing. The values are horizontal-tb (horizontal writing), vertical-rl (vertical writing), and vertical-lr (vertical writing, left to right)<span class="since">4.0.0</span>. For compatibility with Internet Explorer/SVG 1.1, you can also specify lr, lr-tb, rl, tb, and tb-rl.<br /> You can also use the property name <span class="cssprop">-epub-writing-mode</span>. |
| container-type | 4.0.0 |  | normal | All elements | Specifies the query container type from CSS Containment Module Level 3. Accepts normal, inline-size, and size, but actual container queries support only inline-size. size does not function as a query container. |
| container-name | 4.0.0 |  | none | All elements | Assigns one or more names to a query container. You can reference them in named @container conditions. |
| container | 4.0.0 |  |  | All elements | A shorthand for <span class="cssprop">container-name</span> and <span class="cssprop">container-type</span>. Specify it in the form `name / inline-size`. |
| column-width<br />-cssj-column-width | 3.0.0 |  | auto | Non-replaced block-level elements (except tables), table cells, and inline blocks | An implementation based on CSS Multi-column Layout Module Level 1.<br /> Sets the column width for multi-column layout. The value is auto or a length. |
| columns<br />-cssj-columns | 3.0.0 |  |  | Non-replaced block-level elements (except tables), table cells, and inline blocks | An implementation based on CSS Multi-column Layout Module Level 1.<br /> You can set either <span class="cssprop">column-width</span> or <span class="cssprop">column-count</span>, or both values together. |
| column-count<br />-cssj-column-count<br />oeb-column-number | 3.0.0 |  | auto | Non-replaced block-level elements (except tables), table cells, and inline blocks | An implementation based on CSS Multi-column Layout Module Level 1.<br /> Sets the number of columns for multi-column layout. The value is auto or a column count.<br /> You can also use the property name <span class="cssprop">oeb-column-number</span>. |
| column-gap<br />-cssj-column-gap | 3.0.0 |  | normal | Multi-column elements | An implementation based on CSS Multi-column Layout Module Level 1.<br /> Sets the gap between columns. The value is normal or a length. |
| column-rule-color<br />-cssj-column-rule-color | 3.0.0 |  | Color | Multi-column elements | An implementation based on CSS Multi-column Layout Module Level 1.<br /> Sets the color of the rule between columns. |
| column-rule-style<br />-cssj-column-rule-style | 3.0.0 |  | none | Multi-column elements | An implementation based on CSS Multi-column Layout Module Level 1.<br /> Sets the style of the rule between columns. The values are the same as those for <span class="cssprop">border-*-style</span>. |
| column-rule-width<br />-cssj-column-rule-width | 3.0.0 |  | medium | Multi-column elements | An implementation based on CSS Multi-column Layout Module Level 1.<br /> Sets the width of the rule between columns. The values are the same as those for <span class="cssprop">border-*-width</span>. |
| column-rule<br />-cssj-column-rule | 3.0.0 |  |  | Multi-column elements | An implementation based on CSS Multi-column Layout Module Level 1.<br /> Sets the color, style, and width of the rule between columns together. The values are the same as those for <span class="cssprop">border-*</span>. |
| column-span<br />-cssj-column-span | 3.0.0 |  | 1 | Static, non-floating elements | An implementation based on CSS Multi-column Layout Module Level 1.<br /> Specifies whether a paragraph spans columns. The values are 1 and all. |
| -cssj-text-combine<br />-epub-text-combine<br />text-combine-upright | 3.0.4 |  | none | All elements | Enables tate-chu-yoko.<br /> <tt>horizontal</tt> places horizontally laid-out content <strong>at its natural width</strong> (equivalent to specifying <span class="cssdecl">writing-mode: horizontal-tb;</span>). Content with many digits extends beyond the line.<br /> With the standard name <span class="cssprop">text-combine-upright</span>, specifying <tt>all</tt> <strong>fits the content within one character width (1 em)</strong>, shrinking it horizontally if it does not fit<span class="since">4.0.0</span>. <tt>digits</tt> is not supported. |
| -cssj-text-emphasis<br />-epub-text-emphasis<br />text-emphasis<br />text-emphasis-style<br />text-emphasis-color | 3.0.4<br />Standard names: 4.0.0 |  | none | All elements | An implementation based on CSS Text Module Level 3. The standard names and the existing -cssj- names use the same implementation.<br /> Specifies <span class="cssprop">text-emphasis-style</span> and <span class="cssprop">text-emphasis-color</span> together. For details, see <a href="#style-text-emphasis" class="pageref">Emphasis marks</a>. |
| src | 3.0.0 |  |  | @font-face rules | An implementation based on CSS Fonts Module Level 3.<br /> Specifies the font location. For details, see <a href="#style-webfont" class="pageref">WebFont</a>. |
| unicode-range | 3.0.0 |  | U+0-10FFFF | @font-face rules | An implementation based on CSS Fonts Module Level 3.<br /> Specifies the font's code point range. For details, see <a href="#style-webfont" class="pageref">WebFont</a>. |
| word-wrap | 3.0.0 |  | normal | All elements | An implementation based on CSS Text Module Level 3.<br /> Specifies whether to allow wrapping within English words. The values are normal and break-word. |
| word-break | 3.2.2 |  | normal | All elements | An implementation based on CSS Text Module Level 3.<br /> Configures kinsoku. The values are normal, break-all, and keep-all. |
| page | 4.0.0 |  | auto | Block-level elements | An implementation based on CSS Paged Media Module Level 3.<br /> Applies a named page. For details, see <a href="#style-named-pages">Named pages</a>. |
| size | 4.0.0 |  | auto | @page rules | An implementation based on CSS Paged Media Module Level 3.<br /> Specifies the page dimensions. Takes precedence over the I/O properties <span class="ioprop">output.page-width</span> and <span class="ioprop">output.page-height</span>. |
| text-autospace | 4.0.0 | Yes | normal | All elements | An implementation based on CSS Text Module Level 4.<br /> Controls spacing between Japanese and Latin text or digits. The values are normal, no-autospace, or a combination of ideograph-alpha/ideograph-numeric. <strong>Since 4.0.0, the default is normal (adds spacing).</strong> Specify no-autospace to restore the previous appearance. |
| text-spacing-trim | 4.0.0 | Yes | normal | All elements | An implementation based on CSS Text Module Level 4.<br /> Reduces spacing between consecutive punctuation marks (brackets, commas and periods, and middle dots), even across style-run boundaries. Spacing after middle dots does not expand even with justification. The values are normal and space-all. |
| hanging-punctuation | 4.0.0 | Yes | none | All elements | A subset of CSS Text Module Level 3.<br /> Specify none or allow-end. With allow-end, end-of-line punctuation that does not fit in its normal position is squeezed in or allowed to hang. first, last, and force-end are not supported. |
| initial-letter | 4.0.0 |  | normal | ::first-letter | Implements drop caps from CSS Inline Layout Module Level 3. Specify `initial-letter: line-count` or `line-count sink-line-count`. drop/raise are also accepted. Uses the actual font's cap height and implements text wrapping with a float. |
| font-feature-settings | 4.0.0 | Yes | normal | All elements | An implementation based on CSS Fonts Module Level 3.<br /> Directly specifies OpenType font features for glyph substitution and spacing adjustments (palt, jp78, pwid, and others). |
| font-variation-settings | 4.0.0 |  | normal | @font-face rules | A subset of CSS Fonts Module Level 4.<br /> As an `@font-face` descriptor, converts a variable font to a static instance at the specified axis coordinates. It does not apply as a property on individual elements. Use <span class="cssprop">font-weight</span> for the weight of individual elements. |
| font-variant-east-asian | 4.0.0 | Yes | normal | All elements | An implementation based on CSS Fonts Module Level 3.<br /> Specifies Japanese glyph variants (jis78, jis83, full-width, proportional-width, and others). |
| grid-template-columns<br />grid-template-rows | 4.0.0 |  | none | Grid containers | An implementation based on CSS Grid Layout Module Level 1.<br /> Defines grid tracks (columns and rows). Use with <span class="cssdecl">display: grid;</span>. |
| grid-column-start<br />grid-column-end<br />grid-row-start<br />grid-row-end | 4.0.0 |  | auto | Grid items | An implementation based on CSS Grid Layout Module Level 1.<br /> Specifies grid item placement. |
| grid-column<br />grid-row | 4.0.0 |  |  | Grid items | An implementation based on CSS Grid Layout Module Level 1.<br /> Shorthands that specify the start/end of placement together. |
| flex-direction<br />flex-wrap<br />flex-flow | 4.0.0 |  | row / nowrap | Flex containers | An implementation based on CSS Flexible Box Layout Module Level 1.<br /> Specifies the main axis direction (row / row-reverse / column / column-reverse) and wrapping (nowrap / wrap / wrap-reverse). flex-flow is a shorthand. Use with <span class="cssdecl">display: flex;</span>. |
| flex-grow<br />flex-shrink<br />flex-basis<br />flex | 4.0.0 |  | 0 / 1 / auto | Flex items | An implementation based on CSS Flexible Box Layout Module Level 1.<br /> Specifies the item's grow and shrink factors and basis size. As specified by the standard, omitted values in the flex shorthand are grow=1, shrink=1, and basis=0 (different from the individual properties' initial values). |
| order | 4.0.0 |  | 0 | Flex items | An implementation based on CSS Flexible Box Layout Module Level 1.<br /> Changes the visual order of items. The reading order in tagged PDF remains the document order. |
| row-gap<br />gap | 4.0.0 |  | normal | Grid containers, flex containers, and multi-column elements | An implementation based on CSS Box Alignment Module Level 3.<br /> Specifies spacing between tracks, items, or columns. gap is a shorthand for row-gap and column-gap. |
| justify-items<br />align-items<br />justify-self<br />align-self<br />justify-content<br />align-content | 4.0.0 |  |  | Grid/flex containers and items. align-content also applies to normal block containers and table cells | An implementation based on CSS Box Alignment Module Level 3.<br /> Specifies placement and alignment within grid and flex containers. In normal block containers and table cells, applies align-content's start / center / end along the block axis, as well as the space-* / stretch fallbacks when the content forms a single alignment subject. You can also use space-between / space-around / space-evenly for justify-content / align-content in flex containers. |
| column-fill | 4.0.0 |  | balance | Multi-column elements | An implementation based on CSS Multi-column Layout Module Level 1.<br /> Specifies whether to balance column heights (balance) or fill columns sequentially (auto). |
| string-set | 4.0.0 |  | none | All elements | An implementation based on CSS Generated Content for Paged Media Module.<br /> Captures element content in a named string for running headers. For details, see <a href="#style-running-heading">Running headers</a>. |
| hyphens | 4.0.0 | Yes | manual | All elements | An implementation based on CSS Text Module Level 3.<br /> Controls hyphenation of English words. The values are none, manual, and auto. |
| counter-set | 4.0.0 |  | none | All elements | An implementation based on CSS Lists Module Level 3. Sets the innermost existing counter to the specified value, or creates it on the element if it does not exist. If you omit the value, it is 0. |
| text-wrap-style | 4.0.0 | Yes | auto | Block-level elements | An implementation based on CSS Text Module Level 4.<br /> Specifies line-breaking quality (balance, pretty). For details, see <a href="#style-line-breaking">Line-breaking quality</a>. |
| opacity | 3.0.6 |  | 1 | All elements | An implementation based on CSS Color Module Level 3.<br /> Specifies the element's transparency. For details, see <a href="#style-opacity" class="pageref">Transparency</a>. |
| border-top-left-radius | 3.0.6 |  | 0 | All elements | An implementation based on CSS Backgrounds and Borders Module Level 3.<br /> Specifies the radius of the top-left border corner. For details, see <a href="#style-border-radius" class="pageref">Rounded borders</a>. |
| border-top-right-radius | 3.0.6 |  | 0 | All elements | An implementation based on CSS Backgrounds and Borders Module Level 3.<br /> Specifies the radius of the top-right border corner. For details, see <a href="#style-border-radius" class="pageref">Rounded borders</a>. |
| border-bottom-left-radius | 3.0.6 |  | 0 | All elements | An implementation based on CSS Backgrounds and Borders Module Level 3.<br /> Specifies the radius of the bottom-left border corner. For details, see <a href="#style-border-radius" class="pageref">Rounded borders</a>. |
| border-bottom-right-radius | 3.0.6 |  | 0 | All elements | An implementation based on CSS Backgrounds and Borders Module Level 3.<br /> Specifies the radius of the bottom-right border corner. For details, see <a href="#style-border-radius" class="pageref">Rounded borders</a>. |
| border-radius | 3.0.6 |  |  | All elements | An implementation based on CSS Backgrounds and Borders Module Level 3.<br /> Specifies the border corner radii together. For details, see <a href="#style-border-radius" class="pageref">Rounded borders</a>. |
| transform<span class="since">3.2.16</span><br />-cssj-transform<br />-webkit-transform<br />-moz-transform | 3.0.8 |  | none | Block-level elements | An implementation based on CSS Transforms Module Level 1.<br /> Specifies a two-dimensional affine transformation. Three-dimensional transformations are not supported. For details, see <a href="#style-transform" class="pageref">Rotation, scaling, and transformation</a>. |
| transform-origin<span class="since">3.2.16</span><br />-cssj-transform-origin<br />-webkit-transform-origin<br />-moz-transform-origin | 3.0.8 |  | 50% 50% | Block-level elements | An implementation based on CSS Transforms Module Level 1.<br /> Specifies the origin for transformations applied by the transform property. For details, see <a href="#style-transform" class="pageref">Rotation, scaling, and transformation</a>. |
| -cssj-text-fill-color<br />-webkit-text-fill-color | 3.0.8 |  |  | All elements | A proprietary property for compatibility with Chrome/Safari.<br /> Specifies the text fill color. For details, see <a href="#style-text-stroke" class="pageref">Outlined text</a>. |
| -cssj-text-stroke-color<br />-webkit-text-stroke-color | 3.0.8 |  |  | All elements | A proprietary property for compatibility with Chrome/Safari.<br /> Specifies the text stroke color. For details, see <a href="#style-text-stroke" class="pageref">Outlined text</a>. |
| -cssj-text-stroke-width<br />-webkit-text-stroke-width | 3.0.8 |  | 0 | All elements | A proprietary property for compatibility with Chrome/Safari.<br /> Specifies the text stroke width. For details, see <a href="#style-text-stroke" class="pageref">Outlined text</a>. |
| -cssj-text-stroke<br />-webkit-text-stroke | 3.0.8 |  |  | All elements | A proprietary property for compatibility with Chrome/Safari.<br /> Specifies the text stroke width and color. For details, see <a href="#style-text-stroke" class="pageref">Outlined text</a>. |
| text-shadow | 3.0.8 |  | none | All elements | An implementation based on CSS Text Module Level 3.<br /> Specifies text shadows. For details, see <a href="#style-text-shadow" class="pageref">Text shadows</a>. |
| -cssj-no-break-characters | 3.0.6 |  | none | All elements | A proprietary property.<br /> Adds characters subject to kinsoku. For details, see <a href="#style-no-break" class="pageref">Kinsoku (line-breaking rules)</a>. |
| -cssj-break-characters | 3.0.6 |  | none | All elements | A proprietary property.<br /> Removes characters from kinsoku restrictions. For details, see <a href="#style-no-break" class="pageref">Kinsoku (line-breaking rules)</a>. |
| background-clip | 3.2.16 |  | border-box | All elements. | An implementation based on CSS Backgrounds and Borders Module Level 4. The value text is not supported for tables or multiple columns, and only works with fonts other than CID-keyed fonts or emoji fonts. |
| box-sizing | 3.1.10 |  | content-box | Elements on which you can specify <span class="cssprop">width</span> and <span class="cssprop">height</span> | An implementation based on CSS Basic User Interface Module Level 3. |
| -cssj-ruby | 3.0.0 |  | none | All elements | A proprietary property.<br /> Specifies the ruby role (base text, ruby text, or annotation container). The values are none, ruby, rb, rt, and rtc. You normally do not need to specify it because the default style sheet sets it for HTML ruby/rb/rt/rtc elements. For details, see <a href="#style-xml-ruby" class="pageref">Ruby</a>. |
| -cssj-warichu | 4.0.0 | Yes | none | Inline elements | A proprietary property that specifies JLREQ warichu. The values are none and auto. auto creates two lines of half-size text, with long text spanning body text lines in fragments that follow kinsoku. For details, see <a href="#style-autospace" class="pageref">Japanese text spacing</a>. |
| word-wrap<br />-cssj-word-wrap | 3.0.0 |  | normal | All elements | An implementation based on CSS Text Module Level 3.<br /> Specifies whether to wrap within long words that do not fit on a line. The values are normal and break-word. |
| block-flow<br />-cssj-block-flow | 3.0.0 |  | tb | All elements | Specifies the writing direction for compatibility with Internet Explorer. The values are tb, rl, and lr. Use <span class="cssprop">writing-mode</span> in new documents. |
| hyphens | 4.0.0 |  | manual | All elements | Specifies whether to split Latin words with hyphens. The values are none, manual, and auto. auto works only in ranges where the lang attribute is en. For details, see <a href="#style-hyphens" class="pageref">Hyphenation</a>. |
| text-wrap-style<br />text-wrap | 4.0.0 |  | auto | All elements | Specifies the line-breaking method. The values are auto and pretty (balance and stable are accepted but treated as auto). For details, see <a href="#style-line-breaking" class="pageref">Line-breaking quality</a>. |
| string-set | 4.0.0 |  | none | All elements | An implementation of CSS Generated Content for Paged Media.<br /> Captures element content in a named string. Reference it with the string() function in <span class="cssprop">content</span>. For details, see <a href="#style-running-heading" class="pageref">Running headers</a>. |

#### <a id="appx-css-func">CSS functions</a>

**CSS function list**

| Name | Version | Number of arguments | Argument types | Applicable properties | Description |
| --- | --- | --- | --- | --- | --- |
| string | 4.0.0 | 1,2 | String[, identifier] | content | Outputs a named string captured by <span class="cssprop">string-set</span>. The second argument selects which value to use on the page: first (default), last, start, or first-except. For details, see <a href="#style-running-heading" class="pageref">Running headers</a>. |
| target-counter | 4.0.0 | 2,3 | Target[, counter name, number style] | content | Outputs a counter value, such as the page on which the target element appears. Specify the target with an identifier, string, url(), or attr(). The third argument is the number style (the same names as for <span class="cssprop">list-style-type</span>), and defaults to decimal if omitted. |
| target-counters | 4.0.0 | 3,4 | Target, counter name, separator[, number style] | content | Outputs nested counter values joined by a separator. The arguments have the same meaning as in target-counter, with the separator as the third argument. |
| target-text | 4.0.0 | 1,2 | Target[, content] | content | Outputs the content of the target element. The only value for the second argument is content (optional). |
| -cssj-page-ref | 2.0.0 | 2,3,4 | String[, string, string] | content | Outputs the counter value at the specified document fragment. For details, see the [Links and fragments](#style-cssj-page-ref) section. |
| -cssj-cmyk | 1.0.0<br />Overprint setting: 3.1.0 | 3,4 | Integer<br />Decimal number<br />Percentage | color<br />Other properties that specify colors | Specifies a color in CMYK instead of CSS rgb. The arguments are Cyan, Magenta, Yellow, Black, and overprint mode, in that order.<br />The overprint mode is standard or illustrator. standard does not overprint inks, while illustrator does. The default is standard. The overprint mode setting is effective only for PDF output. |
| -cssj-spot | 4.0.0 | 2,3,4 | String<br />Color<br />Integer<br />Decimal number<br />Percentage | color<br />Other properties that specify colors | Specifies a spot color. The arguments are color name, alternate color, tint, and overprint mode, in that order.<br />The alternate color is used in environments that cannot handle spot colors. Specify it with rgb, -cssj-cmyk, or similar functions. The tint is a decimal number from 0 to 1 or a percentage, and defaults to 100% if omitted. The overprint mode is standard or illustrator.<br />If you specify registration as the only argument, the result is a registration color for crop marks and similar uses. Effective only for PDF output. |
| -cssj-gray | 2.0.0 | 1 | Integer<br />Decimal number<br />Percentage | color<br />Other properties that specify colors | Specifies a grayscale color instead of CSS rgb. The argument value is the intensity of black. |
| linear-gradient | 3.2.16 | - | Angle<br />Color<br />Percentage | background | Applies a gradient fill. You can specify any number of color stops<span class="since">4.0.0</span>. <tt>radial-gradient</tt>, <tt>conic-gradient</tt>, and repeating variants (such as <tt>repeating-linear-gradient</tt>) are also supported. |
| rgba | 3.0.8 | 4 | Integer<br />Decimal number<br />Percentage | color<br />Other properties that specify colors | An implementation based on CSS Color Module Level 3.<br />Specifies opacity (Alpha) in addition to an rgb color. The arguments are Red, Green, Blue, and Alpha, in that order. For details, see <a href="#style-alpha" class="pageref">Transparent colors</a>. |

#### CSS identifiers

**CSS identifier list**

| Name | Version | Applies to | Description |
| --- | --- | --- | --- |
| <a id="appx-cssprop--cssj-decimal-full-width"></a><s>-cssj-decimal-full-width</s><br /> -cssj-full-width-decimal<span class="since">3.0.0</span> | 2.1.2 | list-style-type property<br />counter function | Outputs numbers like decimal, but uses full-width characters. |
| -cssj-cjk-decimal | 3.0.0 | list-style-type property<br />counter function | Outputs positional kanji numerals. |
| pages | 3.1.4 | counter function | An implementation based on CSS Paged Media Module Level 3.<br /> A counter that records the total number of pages in the document. Effective in <a href="#style-multipass">processing with two or more passes</a>. |
| page | 3.0.0 | page-break-before property<br />page-break-after property | An implementation based on CSS Paged Media Module Level 3.<br /> Has the same meaning as always. This is separate from the <span class="cssprop">page</span> property<span class="since">4.0.0</span> that applies named pages. |
| column | 3.0.0 | page-break-before property<br />page-break-after property | An implementation based on CSS Multi-column Layout Module Level 1.<br /> Forces a column break. |
| transparent | 3.2.16 | Color specifications in general, such as color | Equivalent to rgba(0, 0, 0, 0). |

#### CSS rules

**CSS rule list**

| Name | Version | Description |
| --- | --- | --- |
| @page margin boxes (@top-center, etc.) | 4.0.0 | Defines 16 margin boxes within the @page rule for placing page numbers and running headers. For details, see <a href="#style-page-margin-boxes">Page margin boxes</a>. |
| @layer | 4.0.0 | An implementation based on CSS Cascading and Inheritance Level 5. Defines cascade layers. |
| @supports | 4.0.0 | An implementation based on CSS Conditional Rules Module Level 3. Provides conditional rules based on property support. |
| @media (feature queries) | 4.0.0 | Supports feature queries such as width/height/orientation in addition to media types. |
| @container | 4.0.0 | For named or unnamed `container-type: inline-size` containers, you can use min/max/exact conditions on width/inline-size, the and operator, and not with a single condition. Set <span class="ioprop">processing.pass-count</span> to 2 or more because queries use the measured width from the previous pass. The or operator, style query, and block-axis conditions are not supported. |

#### CSS pseudo-classes

**CSS pseudo-class list**

| Name | Version | Description |
| --- | --- | --- |
| root | 3.2.16 | Matches the document's body element. |
| has() / is() / not() / where() | 4.0.0 | An implementation based on Selectors Level 4. Some selectors, such as :has(), require <a href="#style-multipass">conversion with two or more passes</a>. |
| nth-child() family / last-child family / empty | 4.0.0 | An implementation based on Selectors Level 4. Those that depend on later siblings require two or more passes. |
| dir() / scope | 4.0.0 | An implementation based on Selectors Level 4. |
| ::marker | 4.0.0 | Specifies list marker styles. |
| ::footnote-call / ::footnote-marker | 4.0.0 | The call number and the number at the start of the <a href="#style-footnotes">footnote</a> body. |

#### CSS units

**CSS unit list**

| Unit | Version | Description |
| --- | --- | --- |
| rem | 3.1.9 | The font size of the root element (HTML or BODY). |
| ch | 3.1.9 | The CSS3 specification defines this as the width of the digit 0, but it currently has the same value as ex. |

### <a id="appx-xml">XML extensions</a>

The engine processes elements and attributes that have special meanings in XML.

For convenience, the following tables assume the prefix-to-namespace mappings shown below.
You can, of course, use different prefixes in actual documents (except prefixes beginning with xml).
An entry without a prefix means that it can belong to any namespace.

| Prefix | Namespace |
| --- | --- |
| cssj | http://www.cssj.jp/ns/cssjml |
| html | http://www.w3.org/1999/xhtml |
| svg | http://www.w3.org/2000/svg |

#### XML elements

**XML element list**

| Name | Version | Attributes | Description |
| --- | --- | --- | --- |
| cssj:make-toc | 2.0.0 | counter, type | Generates a table of contents. counter is the name of the page counter used for page numbering, and type is the page number style. For details, see the [Table of contents](#style-xml-toc) chapter. |
| html:img | 1.0.0 | alt, src, width, height | Functions like the HTML img element. |
| html:a | 1.0.0 | href,name | Functions like the HTML a element. |
| html:br | 2.0.0 |  | Functions like the HTML br element. |
| html:h1～html:h6 | 1.0.0 |  | Functions like the HTML h1 through h6 elements. |
| svg:svg | 1.2.0 |  | Processes the element as an SVG image. For details, see the [Inline SVG](#style-image-inline-svg) section. |

#### XML attributes

<table class="spec">
<caption>XML attribute list</caption>
<thead>
<tr>
<th>Name</th>
<th>Version</th>
<th>Description</th>
</tr>
</thead>
<tbody>
<tr>
<td class="nowrap">cssj:annot</td>
<td class="nowrap">1.2.0</td>
<td>Outputs an annotation message during processing. The specified value is sent back to the driver as an <a href="#appx-messages-annot">annot</a> message.
</td>
</tr>
<tr id="appx-xml-cssj:header">
<td class="nowrap">cssj:header</td>
<td class="nowrap">1.2.0</td>
<td>Gives a general element the same meaning as HTML h1 through h6, so it can serve as a heading for generating a table of contents or bookmarks.
The value is the heading level.</td>
</tr>
<tr>
<td class="nowrap">html:style</td>
<td class="nowrap">1.0.0</td>
<td>Functions like the HTML style attribute.</td>
</tr>
<tr>
<td class="nowrap">html:class</td>
<td class="nowrap">1.0.0</td>
<td>Functions like the HTML class attribute.</td>
</tr>
<tr>
<td class="nowrap">html:colspan</td>
<td class="nowrap">1.0.0</td>
<td>Functions like the HTML colspan attribute.</td>
</tr>
<tr>
<td class="nowrap">html:rowspan</td>
<td class="nowrap">1.0.0</td>
<td>Functions like the HTML rowspan attribute.</td>
</tr>
<tr>
<td class="nowrap">xml:lang</td>
<td class="nowrap">2.0.0</td>
<td>Functions like the HTML lang attribute.</td>
</tr>
<tr>
<td class="nowrap">id</td>
<td class="nowrap">1.0.0</td>
<td>Functions like the HTML id attribute.</td>
</tr>
</tbody>
</table>
