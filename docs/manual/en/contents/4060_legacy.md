## <a id="style-legacy">Compatibility with typical browsers</a>

HTML and CSS are interpreted in the same general way as in typical browsers,
but the focus on printing introduces differences from browsers intended for screen display.
This section explains those differences and known limitations.

### Standards mode and quirks mode

The DOCTYPE declaration determines which of the following two modes is used.

<dl>

<dt>Standards mode</dt>
<dd>Applies to documents with <tt>&lt;!DOCTYPE html&gt;</tt> (the simplified HTML5 DOCTYPE), or
	a DOCTYPE declaration for HTML 4.01/XHTML 1.0/XHTML 1.1.</dd>
<dt>Quirks mode</dt>
<dd>Applies to documents without a DOCTYPE declaration and documents with any other DOCTYPE declaration.</dd>

</dl>

The two modes differ in only the following two ways.

- **Interpretation of tag nesting**. Standards mode applies nesting rules that include
	HTML5 elements (such as video, source, and track). Quirks mode applies the legacy rules,
	so elements specific to HTML5 are treated as unknown elements.
- **Table font size**. In quirks mode, the font size resets to
	the default (medium) at the table element. In standards mode, it is inherited.

**Include `<!DOCTYPE html>` in HTML5 documents.**
Without a DOCTYPE declaration, elements specific to HTML5, such as video, source, and track,
are treated as unknown elements, and their nesting is not interpreted correctly.

### Known limitations

#### Character widths in MS Mincho and related fonts

When MS Mincho or MS Gothic is used at a small font size, typical browsers use
the closest bitmap font, so the actual character size may differ from the specified size. Copper
PDF uses outline fonts for all text, so characters have the specified size.
As a result, character widths may differ from those in typical browsers, which can change where text wraps.

#### Unsupported CSS properties

The following Internet Explorer-specific CSS properties are not supported.

- <span class="cssprop">accelerator</span>
- <span class="cssprop">behavior</span>
- <span class="cssprop">block-progression</span>
- <span class="cssprop">filter</span>
- <span class="cssprop">layout-grid</span>
- <span class="cssprop">layout-grid-*</span>
- <span class="cssprop">interpolation-mode</span>
- <span class="cssprop">overflow-*</span>
- <span class="cssprop">scrollbar-*</span>
- <span class="cssprop">text-justify</span>
- <span class="cssprop">text-kashida-space</span>
- <span class="cssprop">text-overflow</span>
- <span class="cssprop">text-underline-position</span>
- <span class="cssprop">zoom</span>

<div class="note">

Support for <span class="cssprop">text-autospace</span> was added in 4.0.0 as part of the CSS standard
(CSS Text Module Level 4).
**The default is normal (adds spacing between Japanese text and Latin text or digits).**
To restore the appearance in earlier versions,
specify <span class="cssdecl">text-autospace: no-autospace;</span>.

</div>

<div class="note">

<span class="cssprop">word-break</span>,
<span class="cssprop">word-wrap</span>,
<span class="cssprop">writing-mode</span>
were introduced as Internet Explorer-specific names,
but are now supported as standard CSS or equivalent features<span class="since">4.0.0</span>.

</div>

### Automatic table layout

CSS 2.1 does not precisely specify how to adjust the layout of tables with automatic layout
(the default, or tables with <span class="cssdecl">table-layout:
	auto;</span>), so some layout differences from typical browsers are unavoidable.
Whenever possible, use one of the following approaches.

- Use tables with <span class="cssdecl">table-layout: fixed;</span>
- In tables with automatic layout, avoid percentage widths if any cells span multiple columns

### Differences for printing

The following behavior differs from that of browsers intended for screen display.
All of these differences follow from the purpose of producing output on paper.

#### Content that does not fit on the page is sliced and continued

Content that is larger than the page and cannot be split (such as an image or a single character in a large font)
is not scaled down. Instead, it is **sliced geometrically at the bottom of the page, and the remainder is sent to the next page**
(<a href="#pagebreak-rescue" class="pageref">Slicing content that cannot be split and continuing it</a>).
Other browsers discard the overflowing portion when printing. Here, because the purpose is output
on paper, preserving content takes priority.
To avoid slicing, make the content smaller than the page's content area.

#### Percentage heights cannot be specified for table cells

The setting is ignored, and the height is determined by the content. Specify row heights with
<span class="cssprop">height</span> on the row (tr element).
