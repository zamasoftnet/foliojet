## <a id="appx-messages"></a>Information available from the message handler

<a id="appx-ctip2-messages"></a>Information is passed to the message handler through <b>message codes</b> (see the server product manual)
(CTIP 1.0 processing information was removed in 4.0).

**Information**

| Code | Value | Description |
| --- | --- | --- |
| 1001 |  | Processing was interrupted normally by abort or a similar operation. |
| 1801 | Page number(int) | The page whose processing has just started. |
| 1802 | Heading(string) | The heading just output. Reported only when <span class="ioprop">output.pdf.bookmarks</span> or <span class="ioprop">processing.page-references</span> is enabled (because headings are scanned only in that case). |
| 1803 | Pass number(int) | The processing pass that has just started. |
| <a id="appx-messages-annot"></a>1804 | Annotation(string) | The annotation just output (an annotation specified on any element with the cssj:annot attribute). |
| 1805 | Title(string) | The document title. |
| 18FF | Plugin name(string)<br />Message(string) | An informational message from a plugin. |
| 1806<span class="since">2.1.2</span> | Page height(double) | The page height in pt. Reported only when <span class="ioprop">output.auto-height</span> is true. |

**Warnings**

| Code | Value | Description |
| --- | --- | --- |
| 2001 | Resource URI(string) | A resource URI referenced by the document is malformed. |
| 2002 | Base URI(string) | The document base URI is malformed. |
| 2801 | CSS file URI(string) Error message(string) | Malformed CSS was found. |
| 2802 | CSS property name(string) | An unsupported CSS property was found. |
| 2803 | CSS file URI(string) | The CSS file does not exist. |
| 2804 | Property name(string) Property value(string) | The property value is malformed. |
| 2805 | Processing instruction name(string) Processing instruction value(string) | A malformed processing instruction was found. |
| 2806 | CSS file URI(string) Depth limit(string) | CSS @import nesting is too deep. |
| 2807 | Importing CSS URI(string) Imported CSS URI(string) | A CSS @import loop was found. |
| 2808 | HTML element name(string) Attribute name(string) Attribute value(string) | An HTML attribute value is malformed. |
| 280A | cssj:header attribute value(string) | The cssj:header attribute value is malformed. |
| 280B | Resource URI(string) | The resource URI is malformed. |
| 280C | Link URI(string) | The link URI is malformed. |
| 280D | SVG file URI(string) Error message(string) | The SVG is malformed. |
| 280E | XSLT file URI(string) | The XSLT file does not exist. |
| 280F |  | Overriding I/O properties through a PI is prohibited. |
| 2810 | Attachment file URI(string) | The file to attach to the PDF does not exist. |
| 2811 | Image file URI(string)<br />Stage(string)<span class="since">4.0.0</span> | The image cannot be read. The stage is one of <tt>resolve</tt> (the reference cannot be resolved), <tt>fetch</tt> (retrieval failed; if the HTTP status is known, it is appended, as in <tt>fetch: HTTP 404</tt>), <tt>decode</tt> (unsupported format or corrupt image), or <span class="since">4.0.0</span><tt>too-large widthxheight &gt; limit</tt> (the image was not decoded because its pixel count exceeds <span class="ioprop">input.image-pixel-limit</span>). Credentials are removed from the reported URI. |
| 2812 | PDF version(string) Setting name(string) Setting value(string) | An attempt was made to use a feature unavailable in the current PDF version. |
| 2813 | Error message(string) | An inline object is malformed. |
| 2814 | Resource URI(string) | Access to the resource is not permitted. |
| 2816 | CSS property name(string) Value(string) Error message(string) | A CSS property value is malformed. |
| 2817 | Inline style(string) Error message(string) | Inline CSS is malformed. |
| 2818 | Property name(string) | An unsupported I/O property was found. |
| 281C | Property configuration file URI(string) | The property configuration file cannot be read. |
| 281D | Character encoding name(string) | An attempt was made to use an unsupported character encoding name. |
| 281E | Font file URI(string) | The font file cannot be read. |
| 281F | Affected text(string) | No usable font is available. |
| 28FF | Plugin name(string)<br />Message(string) | A warning from a plugin. |
| 2820<span class="since">3.2.16</span> | Affected text(string) | CID-keyed fonts and emoji cannot be used with <span class="cssdecl">background-clip: text;</span>. |
| 2821<span class="since">4.0.0</span> | Property name(string) | A CSS property is intentionally ignored because it has no meaning in static layout (such as <span class="cssprop">cursor</span>, <span class="cssprop">transition</span>, or <span class="cssprop">animation</span>). Distinguished from unsupported properties (2802). |
| 2822<span class="since">4.0.0</span> | Property name(string)<br />Output format(string)<br />Approximation details(string) | A supported feature was rendered approximately because the output format cannot render it precisely (such as <span class="cssprop">box-shadow</span>/<span class="cssprop">text-shadow</span> blur in PDF/A-1, PDF/X-1a, and PDF/X-3 (and PDF 1.3 or earlier), where transparency is unavailable—ordinary PDF renders shadows precisely by rasterizing them<span class="since">4.0.0</span>—or <span class="cssdecl">conic-gradient()</span> in SVG (PDF renders it precisely with mesh shading<span class="since">4.0.0</span>), or <span class="cssprop">mix-blend-mode</span>). In PDF, <span class="cssprop">filter</span> is rendered precisely by rasterizing the element, but its text can no longer be selected or searched; this warning reports that fact (details: filter-rasterized)<span class="since">4.0.0</span>. Many of these features can be rendered precisely in image output (PNG/JPEG) or SVG-based output, in which case no warning is issued. Reported once per document per property. |
| 2823<span class="since">4.0.0</span> | Property name(string)<br />Reason it has no effect(string) | The declaration was parsed but has no effect in this combination. Floated or absolutely positioned elements with <span class="cssdecl">display: flex</span>/<span class="cssdecl">display: grid</span> fall back to normal blocks (items are stacked vertically). Unlike unsupported properties (2802) or intentional ignoring (2821), this reports features that <b>work on their own but fall back because of the context</b>. Reported once per type. |
| 2824<span class="since">4.0.0</span> | Output format(string) | <span class="ioprop">output.image.transparent</span> was specified, but the output format cannot preserve transparency, so the background remains white. PNG, GIF, and TIFF can preserve transparency; JPEG, BMP, and WBMP cannot. Reported once per document. |
| 2825<span class="since">4.0.0</span> | Property name(string)<br />Limit(string)<br />Requested value(string) | A requested setting was less restrictive than the value in the operator's limits file (<tt>jp.cssj.driver.limits</tt>). Conversion uses the limit value instead of the requested value. |
| 2826<span class="since">4.0.0</span> | Item path(string) | A fixed-layout (pre-paginated) EPUB item has no <tt>&lt;meta name="viewport"&gt;</tt>. It is laid out on the default page size, so a page image may be cut across several pages. Reported once per conversion. |

**Errors**

| Code | Value | Description |
| --- | --- | --- |
| 3001 | Document URI(string) | The main document URI is malformed. |
| 3002 | Error message(string) | An I/O error. |
| 3801 | XSLT file URI(string) | The XSLT file is malformed. |
| 3802 | Limit(string) Setting value(double) | The page size setting exceeds the limit. |
| 3803 | Error message(string) | The XML is malformed. |
| 3804 | Byte count(long) | The output file size exceeds the limit. |
| 3805 | Page count limit(int) | The output page count exceeds the limit. |
| 3806 | Document URI(string) | The main document on the server does not exist. |
| 3808 | XSLT file URI(string) Message(string) | A warning message from the XSLT processor. |
| 3809 | XSLT file URI(string) Message(string) | An error message from the XSLT processor. |
| 38FF | Plugin name(string)<br />Message(string) | An error from a plugin. |
| 380D<span class="since">3.0.0</span> | Error message(string) | No pages can be generated because the document content is empty. |
| 380E<span class="since">4.0.0</span> | Property name(string) Value(string) Reason(string) | The PDF/X output intent setting is invalid (the ICC profile cannot be read, is not an output profile, is not CMYK, or the identifier is empty). |
| 380F<span class="since">4.0.0</span> | Element name(string) Limit(int) Reached value(int) | The content of an element that retains its contents until its dimensions are determined (such as a table or float) exceeded <span class="ioprop">processing.retained-text-limit</span>. Conversion fails. |
| 3810<span class="since">4.0.0</span> | Document URI(string) Reason(string) | Retrieval of the main document on the server is not permitted (for example, a remote user specified a destination inside the server's network). Conversion fails. |
| 3811<span class="since">4.0.0</span> | Document URI(string) Reason(string) | The main document on the server cannot be retrieved (for example, the connection was refused or disconnected). Conversion fails. |
| 3812<span class="since">4.0.0</span> | Width(string) Height(string) Limit(string) | The pixel count of the raster image to generate (the type area for image output) exceeds <span class="ioprop">output.image-pixel-limit</span>. Conversion fails. Lower the resolution or page size. |
| 3813<span class="since">4.0.0</span> | Output format(string) | An unsupported format was specified for <span class="ioprop">output.type</span>. |
| 3814<span class="since">4.0.0</span> | PDF version(string) | PDF/UA was selected, but the document language was not specified with <span class="ioprop">output.pdf.tagged.lang</span>. |
| 3815<span class="since">4.0.0</span> | Item path(string)<br />XML parser message(string) | An EPUB item cannot be read as XHTML. The EPUB is encrypted (DRM) or the item is broken. |
| 3816<span class="since">4.0.0</span> | Input URI(string)<br />ZIP reader message(string) | The input cannot be read as an EPUB. It is not a ZIP archive (for example, another kind of file named .epub). |

**Fatal errors**

| Code | Value | Description |
| --- | --- | --- |
| 4001 | Error message(string) | An unexpected error. |
| 4801 | XSLT file URI(string) Message(string) | A fatal error message from the XSLT processor. |
| 48FF | Plugin name(string)<br />Message(string) | A fatal error from a plugin. |

#### Filtering message codes

All messages are sent to the driver.
If you want to handle only the messages you need, **filter them on the receiving side**.
The message handler receives a message code,
so you can route messages by code range (for example, 3000–3FFF are errors).
For implementation details, see the section for each language in the **Developer's manual** (server product manual).
