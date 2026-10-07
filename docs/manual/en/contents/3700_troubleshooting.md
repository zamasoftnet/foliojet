## <a id="prog-troubleshooting">Troubleshooting</a>

Use this section to find the cause by symptom when the PDF is not what you expected.

**Check the messages first.** Events during conversion
are passed to the message handler. Many symptoms have a corresponding message.
For how to receive them, see <b>Configuring the message handler</b> (server product manual).
For a list of codes, see the <a href="#appx-messages" class="pageref">Message list</a>
section.

### <a id="prog-trouble-text">Text is missing or appears as squares (□)</a>

**A font is missing.** Message `281F` (`Missing proper font for:`)
confirms this.

<dl>

<dt>The font is not installed</dt>
<dd>
	Check that the font file exists at the location specified in `fonts.xml`.
	Linux systems often have no Japanese fonts installed.
</dd>

<dt>The font family named in CSS cannot be found</dt>
<dd>
	Does the name in `font-family` match the name in the font?
	If you put `serif` / `sans-serif` last,
	a fallback is used when the font cannot be found.
</dd>

<dt>The font does not contain the character</dt>
<dd>
	Even if the font exists, it may not contain a glyph for that character.
	This occurs with emoji, traditional character forms, small kana, and similar characters.
	Add another font containing the character to `font-family`.
</dd>

</dl>

### <a id="prog-trouble-image">Images do not appear</a>

If a box containing alternative text appears where an image should be, the image either
could not be retrieved or could not be read.

<dl>

<dt>Retrieval was denied (`2814`)</dt>
<dd>
	<b>This is the most common cause.</b>
	The settings for <span class="ioprop">input.include</span> /
	<span class="ioprop">input.exclude</span>
	do not allow the image URI.
	→ <a href="#prog-input-restriction" class="pageref">Restricting resource access</a>
</dd>

<dt>The image could not be read (`2811`)</dt>
<dd>
	The file is missing, corrupt, or in an unsupported format.
	Check the supported formats in <a href="#style-image" class="pageref">Supported input files</a>.
	<b>JPEG 2000 support is not bundled.</b>
</dd>

<dt>The base for relative URIs is incorrect</dt>
<dd>
	When you pass a document as a stream, the base for relative URIs is not determined.
	Tell the driver the document URI, or
	specify the base with the `<?jp.cssj.base-uri ?>` processing instruction.
</dd>

</dl>

### <a id="prog-trouble-style">Styles do not take effect</a>

<dl>

<dt>The stylesheet could not be read (`2803`)</dt>
<dd>
	As with images, retrieval restrictions may be blocking it.
</dd>

<dt>The property is unsupported (`2802`)</dt>
<dd>
	Check <a href="#appx-css" class="pageref">CSS property support</a>.
	Not everything that works in a browser is available.
</dd>

<dt>The style cannot be determined in one pass</dt>
<dd>
	Selectors such as `:last-child` and `:has()`, which require reading
	later content, do not work in one pass.
	→ <a href="#style-multipass-required" class="pageref">Features that require two passes</a>
</dd>

<dt>The stylesheet is inside body</dt>
<dd>
	In one pass, a &lt;style&gt; inside body applies only to elements that follow it.
	This is common in output from SSR frameworks.
	Set <span class="ioprop">processing.pass-count</span> to 2 or more
	to apply it to the entire document.
	→ <a href="#style-multipass-required" class="pageref">Features that require two passes</a>
</dd>

</dl>

### <a id="prog-trouble-page-number">Page numbers or the table of contents are incorrect</a>

**There are almost certainly not enough passes.**

The total page count, page numbers in the table of contents, and displays that reference later content
cannot be determined until the entire document has been read.
Set <span class="ioprop">processing.pass-count</span> to 2 or more.

```
processing.pass-count = 2
```

If they are still incorrect after two passes,
<b>the layout may be changing between the first and second passes, causing page numbers to fluctuate</b>.
For example, a page number may gain a digit, causing a line to wrap and adding another page.
Increasing the pass count to 3 or more may stabilize the result.

For details, see
<a href="#style-multipass" class="pageref">Conversion with two or more passes</a>.

### <a id="prog-trouble-break">Page breaks do not occur where expected</a>

<dl>

<dt>Blank space remains at the specified position, and content moves to the next page</dt>
<dd>
	`page-break-inside: avoid` or `orphans` / `widows` is taking effect.
	To keep content together, an element that does not fit is moved to the next page.
</dd>

<dt>You do not want a break within a table</dt>
<dd>
	→ <a href="#style-page-break-in-table" class="pageref">Page breaks within tables</a>
</dd>

<dt>A specified break does not occur</dt>
<dd>
	`page-break-before` / `page-break-after`
	<b>apply only to block-level elements</b>.
	They are ignored on inline elements and table cells.
</dd>

<dt>A blank page appears</dt>
<dd>
	Specifying `left` / `right` inserts one blank page
	to align with even- or odd-numbered pages. This is the intended behavior.
</dd>

</dl>

### <a id="prog-trouble-overflow">Content extends beyond the paper or is clipped</a>

<dl>

<dt>Wide tables or code overflow</dt>
<dd>
	Content wider than the type area is laid out without shrinking, and overflows.
	For an automatically laid-out table, if the width of unbreakable content
	(the sum of the minimum column widths) only slightly exceeds the available width,
	the columns shrink to fit. If it greatly exceeds the available width, the column widths are preserved and the entire table overflows
	(because shrinking would make content overlap adjacent columns; browsers such as Chrome behave the same way).
	Make it fit by specifying `table-layout: fixed`, allowing wrapping with `word-break`,
	or reducing the font size.
</dd>

<dt>The paper size differs from the specified size</dt>
<dd>
	Printer or output destination restrictions may make the paper smaller than specified (`3802`).
	→ <a href="#style-page-size" class="pageref">Page size restrictions and clipping</a>
</dd>

<dt>Portrait and landscape are reversed</dt>
<dd>
	→ <a href="#style-auto-rotate" class="pageref">When portrait and landscape do not match</a>
</dd>

</dl>

### <a id="prog-trouble-output">Output is empty or ends prematurely</a>

<dl>

<dt>There is no content (`380D`)</dt>
<dd>
	Document parsing has failed, or all content has
	`display: none`.
	Check the <a href="#style-input-mime" class="pageref">MIME type</a> setting
	to make sure the input can be interpreted as HTML.
</dd>

<dt>The page count limit was reached (`3805`)</dt>
<dd>
	→ <a href="#prog-page-limit" class="pageref">Limiting the page count</a>
</dd>

<dt>The data size limit was reached (`3804`)</dt>
<dd>
	→ <a href="#prog-size-limit" class="pageref">Limiting data size</a>
</dd>

<dt>The connection was closed</dt>
<dd>
	When you use a server, the connection is closed if no data is exchanged
	for longer than `jp.cssj.cssjd.timeout` (180 seconds by default).
	Increase the value for long documents.
</dd>

</dl>

### <a id="prog-trouble-pdf">The generated PDF cannot be opened or shows warnings</a>

<dl>

<dt>The PDF version does not support a feature (`2812`)</dt>
<dd>
	You have specified a feature that is unavailable in an older PDF version.
	Increase <span class="ioprop">output.pdf.version</span>,
	or stop using the feature.
	→ <a href="#style-pdf-version" class="pageref">PDF versions</a>
</dd>

<dt>The output is discarded before completion</dt>
<dd>
	If you close the stream before conversion finishes,
	the PDF index is not written, and the file cannot be opened.
	Check that you complete all of the driver's finalization calls.
</dd>

<dt>The PDF does not meet a print submission standard</dt>
<dd>
	PDF/X and PDF/A impose restrictions on font embedding and color spaces.
	→ <a href="#style-pdf-profiles" class="pageref">PDF profiles</a>
</dd>

</dl>

### <a id="prog-trouble-slow">Conversion is slow</a>

→ <b>Speed and memory</b> (server product manual)

These two checks are especially effective.

- Check whether <span class="ioprop">processing.pass-count</span> is unnecessarily set to 2 or more
- Check whether the document references images or stylesheets on an external site that does not respond

### <a id="prog-trouble-network">Resources on the server's internal network cannot be retrieved</a>

When conversion runs on a remote server, retrieval of images or stylesheets at addresses
such as `http://192.168.…` or `http://127.0.0.1:…` may be **denied** (warning `2814`).

From 4.0.0, calls that **are not permitted to retrieve resources from internal networks**
do not retrieve resources from the server itself or its neighboring networks. This prevents
the server from being used to reach the internal network. The decision uses the result
of name resolution and covers loopback, link-local, and private addresses. **Public
addresses can still be retrieved as before.**

If an HTTP redirect leads outside the range permitted by
<a href="#appx-ioprop-input.include" class="pageref">input.include</a>, retrieval also stops at the redirect destination.

If you intend to use internal resources, allow internal retrieval for that call,
or send the resources from the client. The permissions are defined in the server product configuration
(`local-access.txt` in <b>conf</b> (server product manual)).

### <a id="prog-trouble-report">If the problem persists</a>

When you contact support, the following information helps identify the cause.

1. **The full messages** (including codes)
2. **The smallest input that reproduces the problem**. A document reduced to one page and its stylesheet
3. **The I/O property settings**
4. **The software version and Java version**
