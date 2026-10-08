## <a id="style-pagebreak">Page break control</a>

### Definitions

This chapter uses the following terms.

<dl>

<dt>Absolutely positioned box</dt>
<dd>An element with <span class="cssdecl">position: absolute;</span>. In a broader sense, elements with <span class="cssdecl">position: fixed;</span> and content generated for each page are also absolutely positioned boxes.</dd>
<dt>Floating box</dt>
<dd>An element with <span class="cssdecl">float: left;</span> or <span class="cssdecl">float: right;</span>.</dd>
<dt>Block in normal flow</dt>
<dd>A &lt;p&gt; or &lt;div&gt; element with no special settings, or an element with <span class="cssdecl">display: block;</span>, that is neither an absolutely positioned box nor a floating box.</dd>

</dl>

A table consists of the following parts.

<dl>

<dt>Table caption</dt>
<dd>The HTML caption element, or a part with display set to table-caption.</dd>
<dt>Table header</dt>
<dd>The HTML thead element, or a part with display set to table-header-group.</dd>
<dt>Table footer</dt>
<dd>The HTML tfoot element, or a part with display set to table-footer-group.</dd>
<dt>Table row group</dt>
<dd>The HTML tbody element, or a part with display set to table-row-group. Rows placed directly inside a table without tbody or table-row-group are also considered to belong to a row group.</dd>

</dl>

### Forced page breaks

Forced page breaks force a new page at a specified location. You can specify forced page breaks in the following locations.

- Immediately before a block in normal flow
- Immediately after a block in normal flow
- Immediately before a floating box
- Immediately after a floating box
- Immediately before a table
- Immediately after a table
- Immediately before a table row group
- Immediately after a table row group
- Immediately before a table row
- Immediately after a table row
- Immediately before a table cell
- Immediately after a table cell

However, even in these locations, forced page breaks cannot occur inside floating boxes, absolutely positioned boxes, or table cells.

To force a page break immediately before an element, specify <span class="cssdecl">page-break-before: always;</span>. To force a page break immediately after an element, specify <span class="cssdecl">page-break-after: always;</span>.

The following example uses a forced page break to create a cover.

```html
<html>
  <head>
    <title>Document</title>
  </head>
  <body>
    <h1 style="page-break-after: always;">Cover</h1>
    <p>Body text...</p>
  </body>
</html>
```

You can also specify whether the page immediately after the break is a left or right page, rather than simply starting a new page. In this case, one blank page may be inserted to adjust the page side.

To specify the page side after a forced page break, set the page-break-before or page-break-after property to left (for a left page) or right (for a right page) instead of always.

The following example creates a title page that always appears on the right.

```html
<html>
  <head>
    <title>Document</title>
  </head>
  <body>
    <h1 style="page-break-after: always;">Cover</h1>
    <p style="page-break-after: always;">Body text 1...</p>
    <p>Body text 2...</p>
    <h1 style="page-break-before: right;">Title page</h1>
  </body>
</html>
```

However, left and right do not apply to forced page breaks within tables; both are interpreted as always.

### <a id="style-pagebreak-widows-orphans"></a><span class="cssprop">orphans</span> and <span class="cssprop">widows</span>

The <span class="cssprop">orphans</span> and <span class="cssprop">widows</span> properties specify the number of lines that must remain on the previous page and appear on the next page when a page break occurs within a paragraph. Here, a paragraph means a block in normal flow; blank lines created with &lt;br&gt; and similar constructs are not recognized as paragraph boundaries.

Both properties have an initial value of 2, as specified by CSS. This prevents a single line of a paragraph from being isolated before or after a page break or column break.

Japanese typesetting often allows isolated single lines. Particularly in vertical writing and multi-column layout, the initial value of 2 can push a line that would otherwise fit to the next page or column, leaving an unnatural gap at the end of the page or column. For Japanese body text, especially in vertical writing or multi-column layout, we therefore recommend explicitly setting both properties to 1, as follows. A value of 1 requires at least one line before and after a page break or column break.

```css
body {
  orphans: 1;
  widows: 1;
}
```

For Latin text or documents where you want to avoid isolated single lines, keep the initial value of 2 or specify a larger value as needed.

The calculation uses the distance between lines divided by the standard line height (the height determined by the <span class="cssprop">line-height</span> applied to the paragraph), rounded to an integer, rather than the actual number of lines. For example, a line expanded to twice the normal height by a large inline image or a <span class="cssprop">font-size</span> setting on inline content counts as two lines. Although CSS 2.1 does not specify this behavior, it is intended to produce more intuitive page breaks.

#### <span class="cssprop">orphans</span>

When a paragraph reaches the bottom of a page, it needs to be split across pages. <span class="cssprop">orphans</span> specifies the minimum number of lines that must remain on the page before the break. In the following example, <span class="cssprop">orphans</span> is 3 and three lines remain on the page before the break, so the requirement is met.

<div title="orphans set to 3" class="example">
<table class="pages">
<tr>
<td style="vertical-align: top; width: 15em;">
<div>
(Paragraph 1) Line 1...<br /> Line 2...<br /> Line 3...<br /> Line 4...<br />
Line 5...<br /> Line 6...<br /> Line 7...<br /> Line 8...<br />
</div> <br />
<div>
(Paragraph 2) Line 1...<br /> Line 2...<br /> Line 3...<br />
</div>
</td>
<td style="vertical-align: top; width: 15em;">
<div>
Line 4...<br /> Line 5...<br />
</div>
</td>
</tr>
<tr>
<th>Page 1</th>
<th>Page 2</th>
</tr>
</table>
</div>

If you set <span class="cssprop">orphans</span> to 4 for the same document, the current layout cannot satisfy <span class="cssprop">orphans</span>, so the entire paragraph moves to the next page.

<div title="orphans set to 4" class="example">
<table class="pages">
<tr>
<td style="vertical-align: top; width: 15em;">
<div>
(Paragraph 1) Line 1...<br /> Line 2...<br /> Line 3...<br /> Line 4...<br />
Line 5...<br /> Line 6...<br /> Line 7...<br /> Line 8...<br />
</div> <br /> <br /> <br /> <br />
</td>
<td style="vertical-align: top; width: 15em;">
<div>
(Paragraph 2) Line 1...<br /> Line 2...<br /> Line 3...<br /> Line 4...<br />
Line 5...<br />
</div>
</td>
</tr>
<tr>
<th>Page 1</th>
<th>Page 2</th>
</tr>
</table>
</div>

#### <span class="cssprop">widows</span>

When the document content is slightly taller than the page, some lines need to move to the next page. <span class="cssprop">widows</span> specifies the minimum number of lines that must appear on the page after the break. The number of lines moved is adjusted to satisfy <span class="cssprop">widows</span>. For example, if <span class="cssprop">widows</span> is 2, the following example meets the requirement because two lines appear on the second page.

<div title="widows set to 2" class="example">
<table class="pages">
<tr>
<td style="vertical-align: top; width: 15em;">
<div>
(Paragraph 1) Line 1...<br /> Line 2...<br /> Line 3...<br /> Line 4...<br />
Line 5...<br /> Line 6...<br /> Line 7...<br /> Line 8...<br />
</div> <br />
<div>
(Paragraph 2) Line 1...<br /> Line 2...<br /> Line 3...<br />
</div>
</td>
<td style="vertical-align: top; width: 15em;">
<div>
Line 4...<br /> Line 5...<br />
</div>
</td>
</tr>
<tr>
<th>Page 1</th>
<th>Page 2</th>
</tr>
</table>
</div>

If you set <span class="cssprop">widows</span> to 3 for the same document, lines move from the previous page to the next page as shown below to satisfy <span class="cssprop">widows</span>.

<div title="widows set to 3" class="example">
<table class="pages">
<tr>
<td style="vertical-align: top; width: 15em;">
<div>
(Paragraph 1) Line 1...<br /> Line 2...<br /> Line 3...<br /> Line 4...<br />
Line 5...<br /> Line 6...<br /> Line 7...<br /> Line 8...<br />
</div> <br />
<div>
(Paragraph 2) Line 1...<br /> Line 2...<br />
</div> <br />
</td>
<td style="vertical-align: top; width: 15em;">
<div>
Line 3...<br /> Line 4...<br /> Line 5...<br />
</div>
</td>
</tr>
<tr>
<th>Page 1</th>
<th>Page 2</th>
</tr>
</table>
</div>

#### Conflicts between <span class="cssprop">orphans</span> and <span class="cssprop">widows</span>

If the requirements of both <span class="cssprop">orphans</span> and <span class="cssprop">widows</span> cannot be met at the same time, the entire paragraph moves to the next page, just as when <span class="cssprop">orphans</span> cannot be satisfied.

For example, the following layout satisfies both <span class="cssprop">orphans</span> and <span class="cssprop">widows</span>.

<div title="orphans set to 3 and widows set to 2" class="example">
<table class="pages">
<tr>
<td style="vertical-align: top; width: 15em;">
<div>
(Paragraph 1) Line 1...<br /> Line 2...<br /> Line 3...<br /> Line 4...<br />
Line 5...<br /> Line 6...<br /> Line 7...<br /> Line 8...<br />
</div> <br />
<div>
(Paragraph 2) Line 1...<br /> Line 2...<br /> Line 3...<br />
</div>
</td>
<td style="vertical-align: top; width: 15em;">
<div>
Line 4...<br /> Line 5...<br />
</div>
</td>
</tr>
<tr>
<th>Page 1</th>
<th>Page 2</th>
</tr>
</table>
</div>

If you set <span class="cssprop">widows</span> to 3 in this situation, <span class="cssprop">orphans</span> can be satisfied but <span class="cssprop">widows</span> cannot.

<div title="orphans set to 3 and widows set to 3" class="example">
<table class="pages">
<tr>
<td style="vertical-align: top; width: 15em;">
<div>
(Paragraph 1) Line 1...<br /> Line 2...<br /> Line 3...<br /> Line 4...<br />
Line 5...<br /> Line 6...<br /> Line 7...<br /> Line 8...<br />
</div> <br /> <br /> <br /> <br />
</td>
<td style="vertical-align: top; width: 15em;">
<div>
(Paragraph 2) Line 1...<br /> Line 2...<br /> Line 3...<br /> Line 4...<br />
Line 5...<br />
</div>
</td>
</tr>
<tr>
<th>Page 1</th>
<th>Page 2</th>
</tr>
</table>
</div>

However, if the paragraph starts at the top of a page, <span class="cssprop">orphans</span> is ignored and at least one line remains on the previous page.

### Suppressing page breaks

You can suppress page breaks in the following locations.

- Within a block in normal flow
- Immediately before a block in normal flow
- Immediately after a block in normal flow
- Within a floating box
- Within a table
- Immediately before a table
- Immediately after a table
- Immediately before a table row group
- Immediately after a table row group
- Within a table row
- Immediately before a table row
- Immediately after a table row
- Within a table cell
- Immediately before a table cell
- Immediately after a table cell

#### Suppressing page breaks within a box

To suppress page breaks within a box, specify <span class="cssdecl">page-break-inside: avoid;</span>.

If a box with page breaks suppressed extends beyond the page, the entire box moves to the top of the next page. However, if the box is at the top of a page (whether it was moved there or started there) and is taller than the page, the suppression is ignored and the box is split across pages.

#### Suppressing page breaks before and after a box

To suppress page breaks before or after a box, specify <span class="cssdecl">page-break-before: avoid;</span> (before the box) or <span class="cssdecl">page-break-after: avoid;</span> (after the box). Breaks are avoided at the specified location, keeping some lines on either side together on one page. The number of lines kept before the suppressed break point depends on <span class="cssprop">orphans</span>.

When a forced page break conflicts with page break suppression, the forced page break takes precedence. For example, a paragraph with <span class="cssdecl">page-break-after: always;</span> may be followed immediately by a paragraph with <span class="cssdecl">page-break-before: avoid;</span>. Whenever this conflict occurs, the forced page break takes precedence. In this case, <span class="cssdecl">page-break-before: avoid;</span> is ignored and a page break occurs.

HTML h1 through h6 elements have <span class="cssdecl">page-break-after: avoid;</span> by default.

### <a id="style-pagebreak-limits">Limits of page break control</a>

Page break settings are **requests**, not commands.
There are situations where the settings cannot be followed. This section explains
the cases that commonly cause practical problems.

#### Suppression does not mean "never split"

If a box with <span class="cssdecl">page-break-inside: avoid;</span>
does not fit on one page, **the setting is ignored and the box is split**.

Otherwise, the content could not be output.
Content that does not fit even at the top of a page will not fit wherever it is moved.
In this case, splitting and outputting it is more practical than moving it forward forever.

Think of <span class="cssdecl">page-break-inside: avoid;</span> as meaning
**"Please keep this together if it fits on one page."**
To guarantee that content is not split, you must **keep it small enough to fit on one page**.

#### <a id="pagebreak-rescue">Slicing unbreakable content and continuing</a>

If **content that cannot be split**, such as an image or large text, is larger than the page,
it is not scaled down. Instead, **it is sliced geometrically at the bottom of the page, and the remainder continues from the top of the next page**
<span class="since">4.0.0</span>.
This is a purely mechanical cut that does not consider line spacing or block boundaries.
Content remains vector content (and text remains text); it is not rasterized.

No borders or margins are added at the cut
(the same treatment as <span class="cssdecl">box-decoration-break: slice;</span>).
Continuation fragments are output as artifacts in the PDF,
so text extraction and read-aloud output do not duplicate the content.

This applies to all content that cannot be split across pages: replaced elements (images, SVG, MathML, barcodes),
lines in very large fonts, inline blocks, table cells,
and boxes whose writing direction is perpendicular to that of their containing box.
Absolutely positioned boxes are excluded and are output with their overflow intact
(because they are often intended to extend beyond the page, as with watermarks and bleed).

Automatic scaling would make the actual dimensions differ from the specified dimensions
and make the type area unpredictable.
If you want content to fit on the paper, adjust its size in the document.

#### Conflicts between suppression settings and forced breaks

For example, when <span class="cssdecl">page-break-after: always;</span> is immediately followed by
<span class="cssdecl">page-break-before: avoid;</span>,
**the forced page break takes precedence**. Suppression is ignored.

#### Some locations cannot be split

Page breaks never occur in the following locations, regardless of the settings.

- Within a line (a single line is not split)
- Within an image
- Within an absolutely positioned box
- Within a table caption, or between a caption and its table
- Within a table header or footer, or between a header or footer and a row group

The exception is a line, image, or similar content that is **too large to fit on one page**: it is cut mechanically at the
bottom edge of the page and continued on the next page ([Slicing unbreakable content and continuing](#pagebreak-rescue)).
Absolutely positioned boxes are not cut this way.

Also, **forced page breaks have no effect inside floating boxes,
absolutely positioned boxes, or table cells**.

#### Blocks with a different writing direction cannot be split

For example, horizontal writing inside vertical writing (or vice versa)
forms **a block with a different writing direction from its containing box, which cannot be split across pages**.
The entire block is treated as one unit, and if it does not fit, it overflows in the line direction.

Use multi-column layout as well, or explicitly set the size of the block in the page progression direction
so that it fits on the page.

#### Column heights may be approximate

Using <span class="cssdecl">-cssj-column-fill: balance;</span> in multi-column layout
balances column heights, but this is **an approximation based on a single estimate**.
If the amount of content that fits depends on the column height, as when floating boxes are present,
the column heights may be uneven.
For details, see [Multi-column layout](#style-columns).

### Automatic page breaks

Page breaks occur automatically where document content reaches the bottom of a page. Automatic page breaks occur in the following locations.

- Between blocks in normal flow
- Within blocks in normal flow other than images
- Within floating boxes other than images
- Between lines
- Between table row groups
- Between rows within a table row group
- Within rows in a table row group

Conversely, page breaks never occur in the following locations under any circumstances.

- Within a line
- Within an image
- Within an absolutely positioned box
- Within a table caption
- Between a table caption and the table
- Within a table header
- Within a table footer
- Between a table header and a row group
- Between a table footer and a row group

#### Blocks in normal flow

In normal flow, page breaks respect <span class="cssprop">orphans</span> and <span class="cssprop">widows</span>. Breaks are avoided where possible immediately after a block border and within an empty block with a specified height, but a page break occurs if the block is at the top of a page.

#### Floating boxes

When a floating box reaches the bottom of a page, its treatment is determined in the following order.

1. **If the entire box is above the bottom edge**, it is placed on that page
2. **If the entire box is below the bottom edge**, it is moved as a whole to the next page
3. **If it straddles the bottom edge and can be split**, it is split.
	The part moved to the next page is placed again as a floating box on that page
4. **If it cannot be split** and the box is at the top of the page,
	<b>it is mechanically sliced at the bottom of the page, and the remainder moves to the next page</b>;
	if it is not at the top, the entire box moves to the next page

A box cannot be split if it contains unbreakable content such as an image,
if <span class="cssdecl">page-break-inside: avoid;</span> is specified,
or if the floating box has a writing direction perpendicular to that of its containing box.

The mechanical slicing in step 4 is a last resort when **unbreakable content larger than the page**
reaches the top of a page. Moving it to the next page would cause the same problem,
so it is placed without moving it forward, and the part that does not fit is sliced off and continued
(<a href="#pagebreak-rescue" class="pageref">Slicing unbreakable content and continuing</a>).

Splitting floating boxes also respects <span class="cssprop">orphans</span> and
<span class="cssprop">widows</span>, but
if these requirements cannot be met, they are ignored and the box is split.

<div class="note">

For floating boxes nested inside other floating boxes,
<span class="cssdecl">page-break-inside: avoid;</span> has no effect.

</div>
