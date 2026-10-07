## Page processing features

### <a id="style-page-layout">Page layout</a>

Generated pages consist of the following parts.

<dl>

<dt>Paper</dt>
<dd>The sheet on which the document is printed.</dd>
<dt>Print area</dt>
<dd>The area where the document content is printed.</dd>
<dt>Margins</dt>
<dd>The blank areas of a page into which content normally does not extend.</dd>
<dt>Crop marks</dt>
<dd>Marks that indicate where to trim the paper when binding.</dd>
<dt>Trim allowance</dt>
<dd>The area that is cut off during trimming.</dd>
<dt>Bleed area</dt>
<dd>The area that may be cut off during trimming.</dd>

</dl>

<div title="Page layout" class="figure">
	<object data="images/page-layout.svg" type="image/svg+xml"
		style="width: 120mm;" />
</div>

You configure the page layout with I/O properties. The corresponding I/O properties are as follows.

<dl>

<dt>Paper size</dt>
<dd>
	<span class="ioprop">output.paper-width</span>, <span class="ioprop">output.paper-height</span>
</dd>
<dt>Print area size</dt>
<dd>
	<span class="ioprop">output.page-width</span>, <span class="ioprop">output.page-height</span>
</dd>
<dt>Margins</dt>
<dd>
	<span class="ioprop">output.page-margins</span>
</dd>
<dt>Crop marks</dt>
<dd>
	<span class="ioprop">output.marks</span>
</dd>
<dt>Trim allowance</dt>
<dd>
	<span class="ioprop">output.htrim</span>, <span class="ioprop">output.vtrim</span>
</dd>

</dl>

If you do not specify the paper size, the paper width and height are automatically set to the page width and height plus the corresponding trim allowance.

If you specify a paper size that differs from the print area size,
<span class="ioprop">output.fit-to-paper</span> determines how the print area is placed.

<dl>

<dt>false (default)</dt>
<dd>Places the print area at its original size in the center of the paper. If the print area is larger, the overflow does not fit on the paper.</dd>
<dt>true</dt>
<dd>Fits the print area to fill the paper. <b>The scale factor is "paper size ÷ print area size," so a larger print area is reduced.</b>
	Different horizontal and vertical scale factors change the aspect ratio.</dd>
<dt>preserve-aspect-ratio</dt>
<dd>Fits the print area to the paper while preserving its aspect ratio. This can also <b>enlarge or reduce it.</b></dd>

</dl>

In an `@page` rule, you can specify <span class="cssprop">border</span> and
<span class="cssprop">padding</span> as well as margins <span class="since">4.0.0</span>.
As with element boxes, the border and padding sit inside the margins, and the body text's type area sits inside them.
The <span class="cssprop">background</span> of `@page` is drawn across the entire paper (including the bleed band, if any).

```css
@page {
  margin: 15mm;
  border: 0.5pt solid #888;
  padding: 3mm;
}
```

#### <a id="style-fit-wide-page">Fitting a page wider than the paper onto a sheet</a>

Many web pages use a fixed width, such as "980 pixels," without taking paper width into account.
If you convert such a page as is, the layout follows that width and extends beyond the right edge of the paper.

In this case, <b>lay out the content in a print area large enough to contain it, then reduce it to fit the actual paper.</b>
This is why you can specify the print area and paper separately.

```
output.page-width   = 280mm    # Lay out at a width that fits 980 pixels
output.page-height  = 396mm    # Keep the same aspect ratio as the paper
output.paper-width  = 210mm    # Actual paper (A4)
output.paper-height = 297mm
output.fit-to-paper = preserve-aspect-ratio
```

Choose the print area size from the width of the content you want to fit. For content specified in pixels,
<span class="ioprop">output.resolution</span> (96 by default) determines the number of pixels per inch,
so 980 pixels is 980÷96=10.2 inches=259 mm. Adding room for margins gives 280 mm here.
Matching the print area's aspect ratio to the paper prevents uneven margins when you reduce it.

You can also increase <span class="ioprop">output.resolution</span>
to reduce the length of each pixel. However, this <b>recalculates line wrapping</b>,
so it does not guarantee that the content will fit on the paper. Use the method above to ensure that it fits.
To change only the text size, use <span class="ioprop">output.text-size</span>.

The <span class="ioprop">output.page-margins</span>
setting provides the default page margins. You can override them in an @page rule with the CSS <span class="cssprop">margin-top</span>,
<span class="cssprop">margin-right</span>, <span class="cssprop">margin-bottom</span>,
<span class="cssprop">margin-left</span>
properties.

### <a id="style-page-size">Page size limits and clipping</a>

By default, the page is 297 mm high and 210 mm wide (A4 size).
The top, right, bottom, and left margins are all 12.7 mm (3 pc).

Both the paper width and height must be between 1 pt and 14400 pt (5080 mm).
If the paper size exceeds this range, the excess is clipped. This size limit comes from the PDF specification.

You can also set <span class="ioprop">output.auto-height</span> to true
to let the paper height expand to fit the content instead of keeping it fixed. In this case, no page breaks occur,
but the paper size limit above may cause clipping.

To keep the paper size fixed and prevent page breaks, set <span class="ioprop">output.no-page-break</span> to true
<span class="since">2.0.3</span>. In this case, any content that extends beyond the fixed paper size is clipped.

By default, printed content is clipped at the <b>trim line plus bleed (trim allowance)</b>. The bleed width is set in <tt>@page</tt> with <span class="cssprop">bleed</span>, or by the default (3 mm) when you specify <span class="ioprop">output.marks</span><span class="since">4.0.0</span>. To print content beyond the crop marks, all the way to the paper edges, set <span class="ioprop">output.clip</span> to false <span class="since">2.0.3</span>. Settings for print submission are collected in <a href="#prepress-terms" class="pageref">Preparing files for a print shop</a>.

#### <a id="style-auto-rotate">When portrait and landscape orientations do not match</a>

If the paper and content orientations do not match (for example, a landscape table on portrait A4 paper),
you can rotate them automatically with <span class="ioprop">output.auto-rotate</span>.

<dl>

<dt>none</dt>
<dd>Does not rotate (default).</dd>
<dt>content</dt>
<dd><b>Rotates the content by 90 degrees</b> to fit the paper. The paper orientation stays the same.
	Use this when you cannot change the paper size (when printing on a specified sheet).</dd>
<dt>paper</dt>
<dd><b>Swaps the paper orientation</b> to match the content. The content is not rotated.</dd>

</dl>

#### <a id="style-expand-with-content">Expanding the paper to fit the content</a>

When you set <span class="ioprop">output.expand-with-content</span> to true,
**the page expands by the amount needed** if the content does not fit.
Use this when you want the paper size to match the content,
for example, when the number of detail rows in a report is unknown.

The difference from <span class="ioprop">output.auto-height</span> is that
<span class="ioprop">output.auto-height</span>
**leaves the height unfixed from the start**,
whereas <span class="ioprop">output.expand-with-content</span>
**uses the specified size as a minimum and expands only by the amount of overflow**.

### <a id="style-imposition">Imposition<span class="since">4.0.0</span></a>

**Imposition** arranges multiple logical pages on one sheet of paper.
Use it to save paper or to trim the printed sheets into booklets or slips.

Specify the number of logical pages per sheet with <span class="ioprop">output.n-up</span>.
A value of 1 (the default) disables imposition.

```
session.property("output.n-up", "4");
```

#### Paper size and reduction ratio

If you do not specify the paper size, it is set to "logical page + trim allowance,"
and each page is reduced to 1/N and arranged on the sheet. This works like N-up printing on a typical printer.

If you specify the paper size, the grid is placed inside its trim allowance.

**The numbers of grid rows and columns are determined automatically.**
The aspect ratio of the first page on the sheet determines which combination produces the largest pages.
You cannot specify the numbers of rows and columns directly.

#### Page order

Specify the page order with <span class="ioprop">output.n-up.order</span>.

<dl>

<dt>horizontal (default)</dt>
<dd>Arranges pages from left to right, then moves to the left end of the next row.</dd>
<dt>horizontal-reverse</dt>
<dd>Arranges pages from right to left. Use this for documents bound on the right.</dd>
<dt>vertical</dt>
<dd>Arranges pages from top to bottom, then moves to the top of the next column.</dd>
<dt>vertical-reverse</dt>
<dd>Arranges pages from top to bottom, with columns progressing from right to left.</dd>

</dl>

The following shows the page numbering when six pages are arranged in three columns and two rows.

<div title="Imposition page order" class="figure">
<object data="images/nup-order.svg" type="image/svg+xml"
	width="480" height="264">Imposition page order</object>
</div>

#### Crop marks and trim allowance

You can add crop marks to imposed sheets.
Specify the type with <span class="ioprop">output.marks</span>.

| Value | Description |
| --- | --- |
| none | No marks (default) |
| crop | Corner crop marks (trim positions at the corners) |
| cross | Center crop marks (at the center of each edge) |
| both | Both types |
| hidden | Reserves space for marks without drawing them |

Specify the width to cut off with <span class="ioprop">output.htrim</span> (left and right) and
<span class="ioprop">output.vtrim</span> (top and bottom).
To use different values for each edge, use <span class="ioprop">output.trims</span>.

If the input already includes bleed,
use <span class="ioprop">output.trim-inset</span> to specify the trim line position
<span class="since">4.0.0</span>. For details,
see [Preparing files for a print shop](#prepress-existing).

If you specify the spine width with <span class="ioprop">output.marks.spine-width</span>,
lines are drawn to indicate the spine position.

#### When orientations do not match

If the paper and logical page orientations do not match,
you can rotate them with <span class="ioprop">output.auto-rotate</span>.
For details, see [Page layout](#style-page-layout).

<div class="note">

**Cut & stack imposition** (an arrangement that puts pages in order when you stack them after trimming)
is not supported. Only the four orders above are available.

</div>

### <a id="style-multipass">Conversion with two or more passes</a>

#### <a id="style-multipass-why">Why multiple passes are necessary</a>

**The engine reads the document once from beginning to end, laying it out as it goes**.
It builds pages from the content it has read and outputs each page as soon as it is complete.
It does not load the entire document into memory before starting layout.

There are reasons for this design.

<dl>

<dt>It can handle large documents</dt>
<dd>The memory requirement depends on <b>the size of the page being laid out</b>,
	not the size of the document. A 100,000-page report can use about as much memory as a one-page report.</dd>
<dt>The first page is available quickly</dt>
<dd>The engine can send out the first page as soon as it is complete.
	You can start reading without waiting for the entire document to be processed.</dd>
<dt>The server can handle many conversions at once</dt>
<dd>Memory use per conversion is small and constant, so you can estimate how many conversions can run at once.</dd>

</dl>

In exchange, there is an **unavoidable limitation**.

> **The engine cannot make a decision on the spot if the answer is in content it has not yet read.**

Here are some examples.

- "How many pages are in this document?" — The engine cannot know until it has laid out the entire document
- "On which page does this heading appear?" — When laying out the table of contents, the engine has not yet
	read the body text
- "Is this element its parent's last child?" (`:last-child`) — More siblings may
	appear later
- "Does this element have a link among its descendants?" (`:has(a)`) — The engine cannot know
	until the element closes

Loading the entire document into memory before layout would solve these problems,
but would lose all three benefits above.
**Large documents would become impossible to handle, you would have to wait for the first page,
and the number of conversions the server could handle at once would become unpredictable.**

Instead of retaining the whole document, the engine
**reads the same document again**.

Some elements, such as tables and floats, retain their contents until their dimensions are determined.
This retention has a per-element limit (<span class="ioprop">processing.retained-text-limit</span>, 8 MB by default),
and conversion fails if it is exceeded. This caps memory use per conversion without changing the output content.

#### <a id="style-multipass-what">What is a pass?</a>

**One complete reading of the document from beginning to end is called a "pass."**

The idea behind multiple passes is simple.

1. **On the first pass**, the engine lays out the entire document without outputting the result.
	During layout, it **records facts** such as "this heading appeared on page 3" and
	"this element had no children"
2. **On the second pass**, it reads the same document again from the beginning.
	This time, the facts recorded during the first pass are available,
	so it can add page numbers to the table of contents and evaluate `:last-child` correctly.
	The result of this pass is output

<div title="Two-pass workflow" class="figure">
<object data="images/two-pass.svg" type="image/svg+xml"
	width="440" height="275">Two-pass workflow</object>
</div>


Each pass keeps the benefits of a single reading (constant memory use and sequential processing),
while carrying over only "information from later in the document" from the previous pass.

Specify the number of passes with <span class="ioprop">processing.pass-count</span> (1 by default).

```
session.property("processing.pass-count", "2");
```

##### A table of contents example

Consider a table of contents at the beginning of a document.

<dl>

<dt>With one pass</dt>
<dd>When laying out the table of contents, the engine has not read a single character of the body text.
	It has no way to know which heading appears on which page,
	so it <b>cannot insert page numbers</b>.</dd>
<dt>With two passes</dt>
<dd>On the first pass, the engine lays out all the body text and records the page number of each heading.
	On the second pass, that table is available when it lays out the table of contents, so it can fill in the numbers.</dd>

</dl>

This introduces **another problem**. Adding page numbers to the table of contents
can change its own line count. If it gains one line, the start of the body text
moves by one line, which may change the heading page numbers again.
In this case, use three or more passes and repeat until the numbers stabilize.
If you place the table of contents at the **end** of the document, the body text before it has already been laid out,
so this problem does not occur.

<div class="note">

**The engine does not switch automatically.**
Even if you use a feature that requires two passes, the engine does not detect it and
increase the pass count automatically. **Without the setting, the feature simply does not work,
and no warning is issued**. This is by design.

The reason is exactly as explained above. Determining whether two passes are needed also requires reading
the entire document. Trying to do this automatically would ultimately mean retaining
the document in memory.

</div>

#### <a id="style-multipass-required">Features that require two passes</a>

The following features do not work unless you set <span class="ioprop">processing.pass-count</span>
to 2 or more.

**Selectors**

| Selector | Reason |
| --- | --- |
| `:last-child` `:only-child` | Requires confirming that there are no later siblings |
| `:last-of-type` `:only-of-type` | Same as above |
| `:nth-last-child()` `:nth-last-of-type()` | Counts from the end |
| `:empty` | Requires waiting until the element closes to confirm that it has no children |
| `:has()` | Requires reading the entire subtree to determine whether any descendant matches |

The same applies when you use these inside `:not()`.
If the pass count is insufficient, these selectors are **silently ignored as
selectors that do not match**. If a style is not applied, check the pass count first.

**Counters and references**

| Feature | Reason |
| --- | --- |
| `counter(pages)` | The total page count is not known until the entire document has been laid out |
| [Generating a table of contents](#style-xml-toc) (cssj:make-toc element) | Requires the page number of each heading |
| [Page references](#style-page-references) (-cssj-page-ref function) | Requires the page number of the reference target |

For tables of contents and page references, also
set <span class="ioprop">processing.page-references</span> to true.
However, decimal `target-counter()` displays numbers in a single pass for PDF and page-split SVG output
([Page references](#style-page-references)).

**Stylesheets that appear later in the body**<span class="since">4.0.0</span>

| Feature | Reason |
| --- | --- |
| Applying a `<style>` element late in the body to the entire document | Elements before the point where it is read have already been laid out, so it cannot be applied retroactively |

In a single pass, a `<style>` element in the body applies only to elements that follow it
(stylesheets in the head always apply to the entire document).
With two or more passes, stylesheets collected on the previous pass carry over to the next,
so stylesheets anywhere in the document apply to the entire document.
HTML generated by SSR frameworks, with `<style>` elements scattered throughout the body,
may require this behavior for correct layout.

#### How many passes should you use?

**Two passes are enough in most cases.**

Three or more passes are needed when "inserting numbers changes the numbers,"
as described in [A table of contents example](#style-multipass-what).
This happens when you place the table of contents at the beginning or in the middle of the document.
Increase the pass count until the numbers stabilize.

Processing time increases roughly in proportion to the number of passes.

#### What changes with two passes?

**The document is saved to a temporary file.** Because the input can only be read once,
the engine copies it to a temporary file during the first reading and reads later passes from that file.
You need available disk space and permission to create temporary files.

**The engine actually reads the document one more time than the specified count.**
When you specify two or more passes, a lightweight preliminary scan runs first without performing any layout.
The selectors in the table above are resolved during this scan. It does not count toward
<span class="ioprop">processing.pass-count</span>.

**It takes longer for the first page to appear.** Because no result is output until the last pass,
the engine cannot start outputting pages from the beginning as it does with a single pass.

#### <a id="style-multipass-middle-pass">Multiple passes across multiple documents</a>

<span class="ioprop">processing.middle-pass</span>
<span class="since">3.0.4</span> is a **separate mechanism** from
<span class="ioprop">processing.pass-count</span> above.

<span class="ioprop">processing.pass-count</span>
processes one document multiple times internally,
whereas <span class="ioprop">processing.middle-pass</span>
lets **the application send documents multiple times**.
Use it when the application needs to control the passes,
such as when combining multiple documents into one PDF.

For passes that do not output results (the first and intermediate passes),
set <span class="ioprop">processing.middle-pass</span> to true.
Set it to false only before sending the document for the final pass.

<div class="note">

The two mechanisms have similar names, but do not confuse them.
If you are handling just one document, use <span class="ioprop">processing.pass-count</span>.
You need <span class="ioprop">processing.middle-pass</span> only when
**the application itself decides how many times to send the documents**.

</div>

### <a id="style-page-references">Page references</a>

You can [generate a table of contents](#style-xml-toc) (cssj:make-toc element) and
[display the page number](#style-cssj-page-ref) on which particular content is printed (-cssj-page-ref function).

To use these features, set <span class="ioprop">processing.page-references</span>
to true to enable collection of page reference information. Also use [conversion with two or more passes](#style-multipass) as needed.

If you only need decimal page numbers from the CSS standard `target-counter()`, **PDF and page-split SVG output
display the numbers in a single pass without any settings**<span class="since">4.0.0</span>.
The engine first reserves a number field with a fixed digit width, then fills in numbers for later pages afterward. For details, see the note under [-cssj-page-ref function](#style-cssj-page-ref).

### <a id="style-gray">Grayscale printing</a>

Output is normally in color, but the engine can convert it to grayscale
to preview how it will look when printed in grayscale.

To obtain grayscale output, set <span class="ioprop">output.color</span> to gray

### <a id="style-print-mode">Single-sided and double-sided printing</a>

By default, pages are generated for horizontal writing and double-sided printing.
In CSS @page rules, the first page is therefore treated as a :first or :right pseudo-element, followed by alternating :left
and :right pseudo-element pages.

Set <span class="ioprop">output.print-mode</span>
to single-side to switch to single-sided printing.
In single-sided printing, the first page is treated as a :first pseudo-element, and subsequent pages do not belong to any pseudo-element.

### <a id="style-page-margin-boxes">Page margin boxes<span class="since">4.0.0</span></a>

You can place content in the paper margins by defining **margin boxes** inside an `@page` rule.
This is the standard way to display page numbers and running headers.

The following 16 boxes are available.

| Position | Boxes |
| --- | --- |
| Top | `@top-left-corner` `@top-left` `@top-center` `@top-right` `@top-right-corner` |
| Bottom | `@bottom-left-corner` `@bottom-left` `@bottom-center` `@bottom-right` `@bottom-right-corner` |
| Left | `@left-top` `@left-middle` `@left-bottom` |
| Right | `@right-top` `@right-middle` `@right-bottom` |

Their positions on the paper are shown below.

<div title="Margin box positions" class="figure">
<object data="images/margin-boxes.svg" type="image/svg+xml"
	width="330" height="390">Margin box positions</object>
</div>

```css
@page {
	margin: 2cm;
	@bottom-center {
		content: counter(page);
	}
}
```

You can use the following inside margin boxes.

- Strings in `content`, `counter()` and `counters()` (page-level counters,
	namely `page`, `pages`, and those created in `@page` with `counter-*`),
	and `string()` (below)
- Fonts, colors, `text-align`, and `vertical-align` (top, middle, bottom)
- `margin`, `border`, `padding`, and `background`
- `writing-mode` and `text-orientation` (vertical running headers; [see below](#style-vertical-side-boxes))

`background` and `border` are drawn across the entire area assigned to the box (the full margin band). They do not fit themselves to the content size.
`vertical-align` and `text-align` move only the content.

The following are not supported: images with `url()`, quotation marks, `attr()`,
`-cssj-page-ref()`, `leader()`, and setting dimensions with `width` or `height`
(the box size is determined by the content and margins).

#### <a id="style-running-heading">Running headers</a>

Use the `string-set` property to capture heading content in a named string,
and the `string()` function to output it in a margin box.

```css
h1 { string-set: chapter content(); }

@page {
	margin: 2cm;
	@top-center {
		content: string(chapter, first);
	}
	@bottom-center {
		content: counter(page) ' / ' counter(pages);
	}
}
```

The second argument to `string()` specifies which value on the page to use.

<dl>

<dt>first</dt>
<dd>The first value set on the page. This is the default if omitted.</dd>
<dt>last</dt>
<dd>The last value set on the page.</dd>
<dt>start</dt>
<dd>The value at the start of the page.</dd>
<dt>first-except</dt>
<dd>The same as first, but empty on the page where the value is set.
	Use this to omit the running header on the first page of a chapter.</dd>

</dl>

If no value is set on the page, all of these use the value carried over from the previous page.

#### <a id="style-vertical-side-boxes">Placing vertical running headers at the fore edge<span class="since">4.0.0</span></a>

In books with vertical writing, running headers are placed vertically in the left and right margins (at the fore edge). Specifying
`writing-mode: vertical-rl` on a left or right margin box makes only that box use vertical writing.
Page numbers can remain horizontal.

```css
html { writing-mode: vertical-rl; }
h2 { string-set: chapter content(); }

@page :left {
	margin: 12mm 15mm 11mm 14mm;   /* Gutter on the right, fore edge on the left */
	@left-top {
		content: string(chapter);
		writing-mode: vertical-rl;
		text-orientation: upright;
		font-size: 7.5pt;
	}
	@left-bottom { content: counter(page); }
}
@page :right {
	margin: 12mm 14mm 11mm 15mm;
	@right-top {
		content: string(chapter);
		writing-mode: vertical-rl;
		text-orientation: upright;
		font-size: 7.5pt;
	}
	@right-bottom { content: counter(page); }
}
```

Content inside a vertical writing box is positioned as follows.

- Lines can use the full height of the margin (shared by the top, middle, and bottom boxes).
	Content wraps to the next line only when it does not fit on one line.
- `vertical-align` controls alignment toward the **top or bottom**. The defaults are top for `@left-top` and `@right-top`,
	bottom for `@left-bottom` and `@right-bottom`, and
	middle for `@left-middle` and `@right-middle`.
- Horizontally, content is centered within the margin width. If you narrow the content box with `padding` and `margin`,
	it moves to the center of that box. To move it toward the paper edge or body text, add `padding` on the opposite side.
- `text-align` has no effect in vertical writing boxes (use `vertical-align` for top-to-bottom alignment).
- Specify vertical (`margin-top` and `margin-bottom`) `margin` values with absolute lengths. Percentages are treated as 0.

<div class="note">

Because `counter(pages)` (the total page count) is not known until the entire document has been laid out,
set <span class="ioprop">processing.pass-count</span> to 2 or more.
See <a href="#style-multipass" class="pageref">Conversion with two or more passes</a>.

The title element is not displayed, so you cannot use it as a target for `string-set`.

</div>

#### <a id="style-running-elements">Repeating elements on each page (running elements)<span class="since">4.0.0</span></a>

To display **an element itself** in a running header (such as a table, image, or multiline heading
that cannot be reduced to a string), use CSS GCPM running elements.
When you specify <span class="cssdecl">position: running(名前)</span> on an element, it is removed from the body text
and becomes a named template. The margin box declaration <span class="cssdecl">content: element(名前)</span>
draws it on each page.

```css
.chapter-head { position: running(chapter); }

@page {
	@top-center { content: element(chapter); }
}
```

The second argument to `element()` accepts the same values as `string()`: `first`/`last`/`start`/`first-except`.
It selects which element on the page to use (`first` by default).
Multiple margin boxes on the same page can reference the same name.

<dl>

<dt>Styles are determined at the original position</dt>
<dd>The template carries the styles cascaded in the body text; they are not recalculated for each page.
	To change the appearance between `@page :left` and `@page :right`, reference different names from the left and right margin boxes,
	or use separate elements for the two sides.</dd>
<dt>Generated content is evaluated using the values of the page where it is displayed</dt>
<dd>`counter(page)`, `string()`, and `target-counter()` inside the template use the values of the page where the template is drawn
	(some other typesetting engines fix these values at capture time; this behavior is a proprietary extension).
	Declarations such as `counter-increment` that change body text counters are not executed inside the template.</dd>
<dt>Content that does not fit overflows</dt>
<dd>A template larger than its margin box is neither clipped nor reduced.</dd>
<dt>There are limits on how much can be captured</dt>
<dd>Each template can contain up to 10,000 elements, 100 KB of text, and 50 image references.
	If a limit is exceeded, the engine issues a warning and does not capture the template.</dd>

</dl>

<div class="note">

You can use `element()` only in the `content` of margin boxes. If you use it on a normal element or in `::before`/`::after`,
the engine issues a warning and ignores it. Links, bookmarks, forms, and id references inside the template are not registered for the repeated copies.

</div>

### <a id="style-nombre">Adding page numbers</a>

The Japanese term "nombre" refers to the page number printed on a page.
This section provides ready-to-use examples of common numbering styles.
For details on how they work, see [Page margin boxes](#style-page-margin-boxes) and
[Page counters](#style-page-counter).

#### The simplest example

Print just the number at the bottom center of the page.

```css
@page {
	margin: 2cm;
	counter-increment: page;
	@bottom-center {
		content: counter(page);
	}
}
```

The declaration `counter-increment: page;` increments the number by one on each page.
`counter(page)` gives the current page number.

#### Including the total page count, as in "1 / 20"

```css
@page {
	counter-increment: page;
	@bottom-center {
		content: counter(page) ' / ' counter(pages);
	}
}
```

<div class="note">

`counter(pages)` (the total page count) **is not known until the entire document has been laid out**.
Set <span class="ioprop">processing.pass-count</span> to 2 or more.
Without this setting, the count is incorrect.
See [Conversion with two or more passes](#style-multipass).

</div>

#### Placing numbers on the outside (different positions on left and right pages)

For a bound booklet, place page numbers on the outside (the fore edge), opposite the binding.
Use the bottom left of left pages and the bottom right of right pages.

```css
@page :left {
	@bottom-left { content: counter(page); }
}
@page :right {
	@bottom-right { content: counter(page); }
}
```

`:left` and `:right` refer to left and right pages in double-sided printing.
They are not used with single-sided printing settings.
See [Single-sided and double-sided printing](#style-print-mode).

#### Omitting the number on the cover

Treat only the first page differently.

```css
@page {
	counter-increment: page;
	@bottom-center { content: counter(page); }
}
@page :first {
	@bottom-center { content: none; }
}
```

#### Using different numbering for the front matter and body text

This example uses Roman numerals for front matter such as the table of contents and Arabic numerals for the body text,
resetting the number to 1 at the start of the body text.

```css
@page {
	counter-increment: page;
	@bottom-center { content: counter(page, lower-roman); }
}
```

```css
/* Restart page numbering at the beginning of the body text */
#body {
	counter-reset: page 1;
}
```

To use Arabic numerals for body text pages, apply a separate @page rule only to those pages.
[Named pages](#style-named-pages)<span class="since">4.0.0</span> let you apply
separate page styles to the front matter and body text within one document.

#### Including running headers (headings)

You can display the heading for the page alongside the page number.
See [Running headers](#style-running-heading).

<div class="note">

You can specify the paper size with the <span class="ioprop">output.page-width</span> and
<span class="ioprop">output.page-height</span> I/O properties, or with
<span class="cssprop">size</span> in `@page`<span class="since">4.0.0</span>.
If both are specified, `size` takes precedence.
Specify margins with <span class="cssprop">margin</span> in `@page`.
Margin boxes are placed inside these margins,
so **zero margins leave no space for page numbers**.

</div>

### <a id="style-named-pages">Named pages<span class="since">4.0.0</span></a>

When you assign a name to an element with the <span class="cssprop">page</span> property,
the `@page` rule with the same name applies starting on the page where that element begins.
A page break occurs automatically at a boundary between pages with different names.

```css
/* Roman page numbers for front matter; Arabic page numbers for body text */
@page front {
  @bottom-center { content: counter(page, lower-roman); }
}
@page main {
  @bottom-center { content: counter(page); }
}
#front { page: front; }
#body  { page: main; counter-reset: page 1; }
```

You can also combine a name with a pseudo-class (:first, :left, :right),
as in `@page front:first`. However, <span class="cssdecl">:first</span> matches
only **the first page of the document** (as specified by CSS Paged Media Level 3).
`@page chapter:first` applies only when the first page of the document is named chapter;
it **cannot select the first page of each chapter**. To hide a running header on a chapter opening page, use
<span class="cssprop">string-set</span> for the header with `string(…, first-except)`, for example.

#### Blank pages (<span class="cssdecl">:blank</span>)<span class="since">4.0.0</span>

<span class="cssdecl">@page :blank</span> matches pages that begin with a forced page break and have nothing drawn on them
(blank pages inserted to align left and right pages by declarations such as
<span class="cssdecl">break-before: right</span>). Use it to omit running headers and page numbers on blank pages.

```css
@page :blank {
  @top-center { content: none }
  @bottom-center { content: none }
}
```

Because the engine can determine whether a page is blank only after its content is settled, this affects
only **margin boxes** (running headers and page numbers). Margins, paper size, and backgrounds
remain the same as without :blank.

Specifying <span class="cssdecl">break-before</span> on the first element in the document
does not create a blank page before it (even if running headers or page numbers are specified).

<div class="note">

The proprietary feature `-cssj-page-content` from earlier versions (content generated
for each page) was removed in 4.0.0. Use `@page`
[margin boxes](#style-page-margin-boxes) and
[running headers](#style-running-heading) for page numbers and running headers.
Also see **Removed features** (server product manual).

</div>

### <a id="style-footnotes">Footnotes<span class="since">4.0.0</span></a>

An element with <span class="cssdecl">float: footnote;</span>
moves to the bottom of its page as a footnote. A footnote number
(the call) remains at its original position.

```css
.fn {
  float: footnote;
}
.fn::footnote-call {
  content: counter(footnote);
  vertical-align: super;
  font-size: smaller;
}
.fn::footnote-marker {
  content: counter(footnote) " ";
}
```

- Footnote numbers advance automatically with the built-in <span class="cssprop">footnote</span>
  counter. Numbering **restarts at 1 on each page**
  (in document order on the page containing the calls). Continuous numbering
  throughout the document is not currently supported.
- The <span class="cssprop">::footnote-call</span> pseudo-element is the number
  at the call position, and the <span class="cssprop">::footnote-marker</span> pseudo-element is
  the number at the start of the footnote body.
- The <span class="cssprop">content</span> of number labels accepts only strings and
  <span class="cssdecl">counter(footnote)</span>. Anything else
  (such as <span class="cssdecl">counter(footnote, lower-roman)</span>)
  produces a warning and is ignored; the label is laid out with only the number and strings.
- A footnote is placed on **the page containing its call**. If the block containing the call
  (such as a figure that avoids page breaks) moves to the next page,
  the footnote moves with it. If there is no room for the footnote on the call's page,
  it is placed at the start of the next page's footnote area (keeping the number
  from the call's page).
- Footnote bodies use **the page's writing mode**, not the writing mode at their
  original position. Even if vertical body text contains a horizontal figure (<span class="cssdecl">writing-mode:
  horizontal-tb</span>) whose description has a footnote, the footnote uses
  vertical writing in the page's footnote area, like the other footnotes. Any different
  <span class="cssprop">writing-mode</span> specified on the footnote element is ignored.
- **Footnotes in multi-column layout are placed at the end of the column containing the call (the block-end side,
  which is the left edge in vertical writing), using that column's line length** (the usual arrangement
  in books with two horizontal columns). Body text in that column is shortened by the space its footnotes need;
  the dimensions of other columns and the multi-column layout as a whole do not change. A footnote that does not fit
  in the column moves to the next column (or the next page at the end of a page), keeping its sequence number on the call's page.
  On a page where multi-column layout ends partway through, footnotes are placed in the page's footnote area
  (after the columns). Footnotes in multi-column layouts with a fixed height (<span class="cssprop">height</span>, or
  <span class="cssprop">width</span> in vertical writing) and in nested multi-column layouts
  are excluded from this behavior and go in the page's footnote area (column heights may become
  uneven, and a warning message is output). To place a horizontal band at the bottom,
  use <span class="cssdecl">float: bottom</span>.
- **Even a footnote larger than the type area does not cause conversion to fail.**
  A footnote that does not fit in the footnote area of an empty page produces a warning
  and is placed with overflow (the layout is disrupted, but you still get output).
- A footnote whose call cannot be found also produces a warning and is placed
  with a number assigned in document order.

#### Footnote area position and writing mode (<span class="cssprop">@footnote</span>)<span class="since">4.0.0</span>

Even with vertical body text, you can place footnotes in **horizontal writing** at the **bottom** of the paper.
Inside <span class="cssprop">@page</span>, specify this with an <span class="cssprop">@footnote</span> rule
(using the syntax from CSS Generated Content for Paged Media Level 3).

```css
@page {
  @footnote {
    float: bottom;               /* Place the footnote area at the bottom of the paper */
    writing-mode: horizontal-tb; /* Use horizontal writing for footnote bodies */
  }
}
```

- <span class="cssprop">float</span> accepts
  <span class="cssdecl">bottom</span> (the bottom of the paper; a band at the bottom ends of lines in vertical writing) and
  <span class="cssdecl">block-end</span> (the block-end side of the body text; a band at the left edge
  in vertical writing, or the right edge for vertical-lr). Both are at the same position in horizontal writing.
  Other values produce a warning and are treated as <span class="cssdecl">block-end</span>.
- The default is <span class="cssdecl">block-end</span> (the specification's default is
  <span class="cssdecl">bottom</span>, but the engine keeps its existing default
  to preserve the output of existing vertical writing documents).
- <span class="cssprop">writing-mode</span> sets the writing mode of the footnote area. If omitted,
  it uses the page's writing mode. Any <span class="cssprop">writing-mode</span> specified
  on the footnote element itself is ignored (as described above).
- <span class="cssprop">height</span> sets a fixed band size. The default,
  <span class="cssdecl">auto</span>, adjusts it to the amount of footnote content.
  If you specify a length such as <span class="cssdecl">height: 40pt</span>,
  **the same band is reserved on every page in advance, including pages without footnotes**.
  The size includes the 6 pt gap from the body text. Footnotes are placed in call order, starting on the side nearest the body text.
  A footnote that does not fit in the band moves as a whole to the next page, keeping the number from the call's page.
  A footnote that cannot fit in the band even by itself produces a warning and overflows on the page after its call is finalized.
  The separator rule is drawn only on pages where footnotes are placed.
- <span class="cssprop">min-height</span> sets the minimum band size (0 by default).
  This size is reserved even on pages with few or no footnotes, and the band grows as footnote content increases.
  If <span class="cssprop">height</span> is also fixed, the larger of the two values is used.
  For <span class="cssdecl">bottom</span> in vertical writing, the dimension is the physical height;
  for <span class="cssdecl">block-end</span>, it is the dimension in the body text's block direction.
  Negative lengths and percentages produce a warning and are ignored. Font-relative lengths use the UA's default font.
  <span class="cssprop">max-height</span> is not supported; specifying it produces a warning and it is ignored.
  Both fixed sizes and minimum sizes are capped at 60% of the line length for a bottom band in vertical writing,
  or at a size that leaves 20 pt for body text for a band in the block direction. Exceeding the limit produces one warning per document.
- In vertical writing with <span class="cssdecl">float: bottom</span>,
  **lines on the page become shorter** by the size of the footnote band. The band height is subtracted from the containing area's
  dimensions only once. The band is inside the type area; paper margins and
  margin boxes (running headers and page numbers) remain unchanged. The band is capped at 60% of the line length.
- Footnotes are placed in the band on **the same page as their calls**. To achieve this,
  only in vertical writing with <span class="cssdecl">float: bottom</span> and
  <span class="cssdecl">height: auto</span>, the engine first performs a preliminary
  layout of the same content to determine the band size before laying out the page (the preliminary layout runs
  about two pages ahead of the final layout, so output of the first page is delayed by that amount.
  This preliminary layout is not performed for the default footnote area or horizontal writing).
  For named pages, the preliminary layout uses the dimensions for each page name, so footnotes normally
  appear in the band on the same page as their calls.
  A footnote may instead go in **the next page's band** if different line wrapping between the preliminary and final layouts
  moves the call to the next page, if the footnote body appears far after the call,
  if the preliminary layout's report does not arrive in time,
  if page dimensions differ between the preliminary and final layouts (for example, different handling of blank pages shifts left and right pages),
  or if the actual height of the footnotes exceeds the reserved band
  (the footnote keeps the number from the call's page).
- **For books that place short footnotes in a band of constant size, a fixed <span class="cssprop">height</span> is recommended.**
  The band size is known from the start, making this reliable and fast, with no preliminary layout or resulting output delay.
  Specifying only <span class="cssdecl">min-height</span> still triggers preliminary layout for a bottom band in vertical writing.

  ```css
  @page {
    @footnote {
      float: bottom;
      writing-mode: horizontal-tb;
      height: 40pt;
    }
  }
  ```
- In vertical writing with <span class="cssdecl">float: bottom</span>, even in multi-column layout,
  there is **one band per page**, running horizontally below the columns. Each column has the same line length (physical height),
  calculated by subtracting the band from the containing area's dimensions, removing column gaps, and dividing the remainder equally.
  With <span class="cssdecl">block-end</span> (the default), footnotes are placed
  at the end of the column containing the call (see the previous section).
- Calls inside floats or absolutely positioned content may have their footnotes placed in the next page's band.
  In particular, calls inside page floats, absolutely positioned content, and parallel notes are not included in the preliminary layout's report.
  Calls inside normal floats and table cells are counted during preliminary layout as well.
- <span class="cssprop">border-top</span> (and
  <span class="cssprop">border-top-width</span>, <span class="cssprop">border-top-style</span>, and
  <span class="cssprop">border-top-color</span>) sets the separator rule between body text and footnotes.
  If specified, the rule spans the full width of the footnote area with the specified thickness and color (it is drawn as a solid line).
  <span class="cssdecl">border-top: none</span> suppresses the rule. If unspecified,
  the default is a black, 0.5 pt rule spanning 1/3 of the type area width. The rule is drawn in the center of the gap (6 pt)
  from the body text, so a rule thicker than the gap overlaps the body text or footnotes.
- The width of a horizontal footnote is the type area width of the page containing its call (including the footnote element's
  <span class="cssprop">padding</span> and <span class="cssprop">border</span>,
  and ignoring its left and right <span class="cssprop">margin</span>). Even if a footnote moves to a named page
  with a different width, it keeps the width from the call's page and is aligned to the left edge.

### <a id="style-page-floats">Moving figures and tables to page edges<span class="since">4.0.0</span></a>

An element with <span class="cssdecl">float: bottom;</span>
moves to the bottom of the page's type area (above any footnotes).
An element with <span class="cssdecl">float: top;</span>
moves to **the top of that page**. Body text already laid out on the page
moves down by the height of the figure or table (line wrapping does not change).
If the body text and the figure or table do not fit on the page together, only the figure or table
moves to **the top of the next page**, and the body text stays on the current page.
The same applies to <span class="cssdecl">float: bottom;</span>: if the body text has already reached
the space needed at the bottom (the height of the figure or table) when the figure or table appears in the input, only the figure or table moves to **the bottom of the next page**,
and the body text stays on the current page. A figure or table never appears on a page earlier than its position in the input.
This feature moves large figures and tables within the body text to positions that make reading easier.

Even in vertical writing, top and bottom refer to the top and bottom of the paper<span class="since">4.0.0</span>. <span class="cssdecl">float: bottom;</span>
places the figure or table at the bottom corner on the block-end side (bottom left for <tt>vertical-rl</tt>, bottom right for <tt>vertical-lr</tt>),
shortening adjacent lines. <span class="cssdecl">float: top;</span> uses the top corner on the block-start side (top right for <tt>vertical-rl</tt>).
To place a figure or table at the top corner on the block-end side (top left for <tt>vertical-rl</tt>) and move the starts of adjacent lines down,
use <span class="cssdecl">float: block-end;</span>. <span class="cssdecl">float: block-start;</span> is
the same as <span class="cssdecl">float: top;</span>. In horizontal writing, <tt>block-end</tt> and <tt>block-start</tt> are the same as
<tt>bottom</tt> and <tt>top</tt>, respectively. In vertical writing, <span class="cssprop">width</span> values in <tt>%</tt> do not take effect because they are percentages
of the dimension in the block direction, so specify a length for figures and tables that span the full type area width.

```css
figure.wide {
  float: bottom;      /* Move to the bottom of this page */
  width: 100%;
}
figure.head {
  float: top;         /* Move to the top of this page (or the next page if it does not fit) */
  width: 100%;
}
```

- If figures and tables do not fit, they move to subsequent pages in sequence (their order in the input
  is preserved).
- If a figure or table is narrower than the type area, only adjacent lines become shorter to avoid it;
  other lines keep their full width (the same wrapping as for normal floats). In vertical writing,
  even a figure or table spanning the full type area width allows body text to flow into the remaining length of adjacent lines (below the figure or table for <tt>top</tt>, above it for <tt>bottom</tt>).
  However, a **figure or table that appears after body text in the input** cannot cause already laid-out lines to reflow beside it.
  Even if it is narrow, it occupies a band of its own height at the top of the page, and the body text
  continues below it (without wrapping beside it).
- Only when a figure or table exceeds the type area in both width and height does it occupy a page by itself,
  with the body text continuing on the next page (they are never drawn on top of each other).
- When placed on the same page as footnotes, page floats appear above the footnotes.
- Grid, flex, and multi-column layouts beside a figure or table, and blocks with <span class="cssdecl">display: flow-root;</span> or an <tt>overflow</tt> value other than <tt>visible</tt>, have their entire boxes narrowed to fit beside it. Tables and images that do not fit beside it move to the next page
  <span class="since">4.0.0</span>. The same narrowing applies to boxes that start immediately before the figure or table (when their frame and one line do not fit before it). Grid, flex, and table layouts that start earlier and extend to the figure or table's position are split before it and continue on the next page.

<div class="note">

Because the engine reads and lays out the document only once from the beginning, a
<span class="cssdecl">float: top;</span> written after body text is placed at the top of the same page by shifting already laid-out content
by the height of the figure or table. The content is not laid out again,
so figures and tables inside tables, multi-column layouts, or flex/grid layouts, or partway through a paragraph
continuing from the previous page, may go to the top of the next page.
To ensure that a figure or table appears at the top of the same page, write it at the start of the page
(immediately after a page break).

Left and right page floats using the `float-reference` property are not supported.
Page floats inside multi-column layouts go to the page edge rather than the column edge<span class="since">4.0.0</span>.
The multi-column layout breaks before the figure or table (column rules also stop before it). A figure or table written in a later column
moves to the next page if an earlier column has already been laid out into the space it needs.

</div>
