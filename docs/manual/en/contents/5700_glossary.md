## <a id="appx-glossary">Glossary</a>

These are the terms used in this manual. They include printing and DTP terminology
as well as terms specific to the software, so this section explains them together.

### <a id="appx-glossary-page">Page terminology</a>

<dl>

<dt><a id="glossary-nombre">Page number</a></dt>
<dd>
	The page number printed on a page. The Japanese term comes from the French <i>nombre</i> (number).
	In CSS, you retrieve it with `counter(page)` and
	place it in a <a href="#style-page-margin-boxes" class="pageref">margin box</a>.
	→ <a href="#style-nombre" class="pageref">Adding page numbers</a>
</dd>

<dt><a id="glossary-running-head">Running header (hashira)</a></dt>
<dd>
	A chapter or section heading repeated at the top or bottom of each page.
	Also called a running head in English.
	Because each page needs to pick up the heading currently in effect,
	you use `string-set` together with `string()` in CSS.
	→ <a href="#style-running-heading" class="pageref">Running headers</a>
</dd>

<dt><a id="glossary-margin-box">Margin box</a></dt>
<dd>
	One of 16 small boxes you can place in the paper margins, outside the type area.
	You specify them inside `@page`, with rules such as `@top-center`.
	Place page numbers and running headers here.
	→ <a href="#style-page-margin-boxes" class="pageref">Page margin boxes</a>
</dd>

<dt><a id="glossary-hanmen">Type area (hanzura)</a></dt>
<dd>
	The rectangular area of the paper that contains the body text.
	It is the paper area remaining after you subtract the margins (`margin`).
</dd>

<dt><a id="glossary-recto-verso">Gutter and fore edge / spread / recto and verso</a></dt>
<dd>
	The binding side is the <b>gutter</b>, and the opposite side is the <b>fore edge</b>.
	Double-sided printing requires different margins on left and right pages,
	so you specify them separately with `@page :left` / `@page :right`.
	Recto is an odd-numbered page (right), and verso is an even-numbered page (left).
</dd>

</dl>

### <a id="appx-glossary-print">Printing and binding terminology</a>

<dl>

<dt><a id="glossary-imposition">Imposition (mentsuke)</a></dt>
<dd>
	Arranging multiple pages on one large sheet of paper.
	The pages are arranged so that folding and trimming produce a booklet in the correct order.
	→ <a href="#style-imposition" class="pageref">Imposition</a>
</dd>

<dt><a id="glossary-tombo">Crop marks (trim marks)</a></dt>
<dd>
	Marks at the four corners and the center of each edge of the paper that indicate where to trim it.
	The print shop cuts the paper along these marks.
</dd>

<dt><a id="glossary-bleed">Bleed (tachishiro) / bleed area</a></dt>
<dd>
	An area where colors or images extend slightly beyond the trim position.
	This prevents white gaps at the paper edges even if trimming is off by a few millimeters.
	You specify it with `bleed` in CSS.
</dd>

<dt><a id="glossary-cut-stack">Cut &amp; stack</a></dt>
<dd>
	An imposition method that stacks sequentially numbered pages at the same position.
	After cutting, you simply stack the piles to put the pages in order. It is used for high-volume sequential printing.
</dd>

</dl>

### <a id="appx-glossary-process">How conversion works</a>

<dl>

<dt><a id="glossary-pass">Pass</a></dt>
<dd>
	One complete reading of a document from beginning to end.
	The total page count and page numbers in the table of contents cannot be determined in a single reading,
	so the document needs to be read at least twice.
	→ <a href="#style-multipass" class="pageref">Conversion with two or more passes</a>
</dd>

<dt><a id="glossary-stream">Streaming</a></dt>
<dd>
	A method that lays out and outputs content as it arrives, without loading the entire document into memory.
	This keeps the memory requirement from growing with document size.
	→ <a href="#admin-perf-memory" class="pageref">Memory use does not depend on document size</a>
</dd>

<dt><a id="glossary-ioprop">I/O property</a></dt>
<dd>
	A name and value pair that specifies how to perform conversion.
	The names begin with `input.` / `output.` / `processing.`, such as `output.pdf.version`.
	While CSS determines <b>the appearance of the page</b>,
	I/O properties control <b>the conversion itself</b>.
	→ <a href="#appx-ioprops" class="pageref">I/O property list</a>
</dd>

<dt><a id="glossary-driver">Driver (client library)</a></dt>
<dd>
	A library for making calls from a programming language.
	Drivers are available for Java / Perl / PHP / .NET / Ruby / Python / Node.js.
	→ <b>Programming interfaces</b> (server product manual)
</dd>

<dt><a id="glossary-ctip">CTIP</a></dt>
<dd>
	The communication protocol used between a driver and the server.
	The version currently in use is CTIP 2.0.
	→ <b>CTIP 2.0 interface</b> (server product manual)
</dd>

<dt><a id="glossary-profile">Profile</a></dt>
<dd>
	A set of configuration settings that includes font settings and default I/O properties.
	Profiles are stored in `conf/profiles/`. You can switch between them for different purposes.
	<b>These are separate from PDF profiles (such as PDF/X).</b>
</dd>

<dt><a id="glossary-user-agent">User agent</a></dt>
<dd>
	The component that renders laid-out pages in the actual output format (PDF, images, or SVG).
	It is selected by <span class="ioprop">output.type</span>.
</dd>

</dl>

### <a id="appx-glossary-typography">Typesetting terminology</a>

<dl>

<dt><a id="glossary-kinsoku">Kinsoku (line-breaking rules; kinsoku shori)</a></dt>
<dd>
	Japanese typesetting rules that prevent punctuation and closing brackets at the start of a line,
	opening brackets at the end of a line, and similar arrangements.
	→ <a href="#style-line-breaking" class="pageref">Line breaking</a>
</dd>

<dt><a id="glossary-ruby">Ruby</a></dt>
<dd>
	Phonetic readings attached to kanji and other characters. You write them with the HTML `ruby` element.
	→ <a href="#style-xml-ruby" class="pageref">Ruby</a>
</dd>

<dt><a id="glossary-kenten">Emphasis marks (kenten)</a></dt>
<dd>
	Dots placed beside characters for emphasis. Also called side dots.
	You specify them with `text-emphasis` in CSS.
</dd>

<dt><a id="glossary-tatechuyoko">Tate-chu-yoko</a></dt>
<dd>
	Setting digits or other characters side by side within vertical writing, without rotating them sideways.
	You specify it with `text-combine-upright` in CSS.
</dd>

<dt><a id="glossary-orphans-widows">Orphans / widows</a></dt>
<dd>
	Single lines left at the bottom or top of a page when a paragraph spans pages.
	You specify the minimum number of lines to keep with `orphans` / `widows` in CSS.
</dd>

<dt><a id="glossary-kumimoji">Combined characters</a></dt>
<dd>
	Setting multiple characters within a single character cell.
	For example, you can fit the Japanese word "株式会社" into one cell.
</dd>

</dl>
