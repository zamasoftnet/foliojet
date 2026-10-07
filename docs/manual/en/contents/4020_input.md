## Supported input files

### <a id="style-xml">HTML/XML processing</a>

#### <a id="style-input-filters">Input filters</a>

Use <span class="ioprop">input.filters</span> to specify preprocessing steps
to apply to the loaded document in sequence.
List the filter names separated by spaces, in the order to apply them.

<dl>

<dt>xslt</dt>
<dd>Applies the XSL transformation specified by the `<?xml-stylesheet ...?>` processing instruction.</dd>
<dt>default-to-xhtml</dt>
<dd>Moves XML elements that have no namespace into the XHTML namespace.</dd>
<dt>loose-html</dt>
<dd>Allows ordinary HTML (including unclosed tags and similar issues) to be interpreted.</dd>

</dl>

The default is `xslt default-to-xhtml loose-html`.

#### <a id="style-input-mime">Supported MIME types</a>

When you send a document through a driver, specify a MIME type that matches the content.
The following types are accepted.

| MIME type | Content |
| --- | --- |
| `text/html` | HTML (loose syntax is allowed) |
| `application/xhtml+xml`, `text/xhtml`, `application/xhtml` | XHTML |
| `application/xml`, `text/xml` | XML (XSLT and default namespace conversion are applied) |
| `text/markdown`, `text/x-markdown` | [Markdown](#style-markdown) |
| `application/epub+zip` | [EPUB](#style-epub) |
| `application/epub+directory` | An extracted EPUB directory (for internal and development use) |
| `image/jpeg`, `image/png`, etc. | [Images](#style-image) (converted directly to PDF) |

If you omit the MIME type, it is inferred from the content.

#### Identifying the document type

The following information determines whether a document is HTML or XML.

1. The document MIME type specified by the programmer
2. The HTTP Content-Type header

Information in 1 takes precedence over 2. No other information is used to determine the document type.
For documents identified as HTML, even those beginning with
<tt>&lt;?xml～</tt>
are recognized as HTML. However, namespaces supported by XML and XHTML are recognized even when the document is identified as HTML.

A document identified as HTML is interpreted loosely, so syntax errors are tolerated.
The default namespace (specified with xmlns="～") is always fixed to the XHTML namespace (http://www.w3.org/1999/xhtml).
Set <span class="ioprop">input.html.change-default-namespace</span> to true to allow the default namespace to be changed.<span class="since">3.2.12</span>.

A document identified as XML is interpreted strictly. When you write XHTML, use lowercase for all element and attribute names.
Processing stops if a syntax error occurs.

##### Character encoding

The character encodings that can be recognized depend on the Java runtime. For a list of encoding names, see the [Java runtime's Supported Encodings documentation](https://docs.oracle.com/javase/jp/8/docs/technotes/guides/intl/encoding.doc.html).

The character encoding of an XML document is determined in the following order of precedence.

1. The document character encoding specified by the programmer
2. BOM (Byte Order Mark)
3. The encoding attribute of the XML declaration

By default, UTF-8 or UTF-16 is detected automatically according to the XML specification.

The character encoding of an HTML document is determined in the following order of precedence.

1. The document character encoding specified by the programmer
2. BOM (Byte Order Mark)
3. <tt>&lt;meta http-equiv="Content-Type" content="text/html; charset=encoding-name"&gt;</tt>
4. The encoding attribute of the XML declaration
5. The encoding specified by <span class="ioprop">input.default-encoding</span>

The default for <span class="ioprop">input.default-encoding</span> is the custom encoding name JISUniAutoDetect
(automatic detection of ISO-2022-JP, UTF-8, Windows-31J, and EUC_JP_Solaris),
rather than JISAutoDetect (automatic detection of ISO-2022-JP, Shift_JIS, and EUC_JP).

#### <a id="style-xml-meta">Document information</a>

Information obtained from HTML meta elements is used as PDF document information. You can set document information with the HTML
<tt>&lt;meta name="name" content="value"&gt;</tt>
element. For TITLE, the content of the HTML title element is also used (as with the browser's document.title, leading and trailing whitespace is removed and consecutive whitespace is collapsed to a single space).

**Setting document information with meta elements**

| Name | PDF attribute name | Description |
| --- | --- | --- |
| TITLE | Title/Title | The document title. |
| DESCRIPTION<br />SUBJECT | Subject/Subtitle | A brief description of the document's content. |
| KEYWORDS | Keywords/Keywords | Keywords related to the document's content, separated by spaces or commas. |
| AUTHOR | Author/Author | The author of the document. |
| PRODUCER | Producer/PDF producer | The program that generated the PDF. If omitted, the name and version of the conversion program are used. |
| GENERATOR<br />CREATOR | Creator/Application | The program, editor, or authoring tool that generated the HTML document. |

The name attribute of a meta element is case-insensitive.
Whether you write
<tt>&lt;meta name="AUTHOR" content="author-name"&gt;</tt>,<br />
or
<tt>&lt;meta name="Author" content="author-name"&gt;</tt>,<br /> the author name is set in the PDF document.

You can disable document information settings from HTML meta and title elements by setting <span class="ioprop">output.use-meta-info</span> to "false" <span class="since">3.1.8</span>. In that case, the following method is the only way to set document information.

You can also set document information with I/O properties <span class="since">2.0.3</span>.

- <span class="ioprop">output.meta.<i>n</i>.name</span>
- <span class="ioprop">output.meta.<i>n</i>.value</span>

Use these properties to set the name and value. <i>n</i> is a sequential number starting at 0, so you can specify multiple items of document information.
The meta elements described above override these settings.

#### <a id="style-xml-xslt">Applying XSLT stylesheets</a>

Like CSS, XSLT stylesheets are applied with the xml-stylesheet processing instruction.
For details about the xml-stylesheet processing instruction, see the CSS [section on the xml-stylesheet processing instruction](#style-xml-stylesheet).

As with CSS, you can specify a default XSLT stylesheet. The stylesheet specified by <span class="ioprop">input.xslt.default-stylesheet</span> is applied first.

When multiple XSLT stylesheets are specified through <span class="ioprop">input.xslt.default-stylesheet</span> or xml-stylesheet processing instructions,
only the first stylesheet specified for the document is actually applied.
The other stylesheets are ignored.

### <a id="style-markdown">Markdown<span class="since">4.0.0</span></a>

You can convert documents written in Markdown directly.
Specify `text/markdown` (or `text/x-markdown`) as the MIME type.

```java
CTISessionHelper.transcodeFile(session, new File("readme.md"), "text/markdown", null);
```

Internally, **Markdown is converted to HTML first**, then laid out as ordinary HTML.
Styles, selectors, and page processing therefore work exactly as they do for HTML.
Apply CSS with the [default stylesheet](#style-css-default-stylesheet)
or the `jp.cssj.stylesheet` processing instruction.

#### Default style

Because Markdown has no standard visual presentation,
a default style is provided for **printing as an A4 report**.
Body text is justified in Mincho (serif), and headings indicate hierarchy through bold type and spacing alone.
Tables have only heavy top and bottom rules and a thin rule below the header (no vertical rules).
A page number appears at the bottom center of each page. No colors or enclosing borders are used,
so the style is also suitable for black-and-white printing as it is.

You can override individual parts of this default style with HTML `<style>` elements written directly
in the document. To change the paper size or margins, write an `@page` rule.
The document's `<style>` elements are moved into `<head>` (after the default style)
in the generated HTML, so properties that apply to `body` or `html` itself
(such as `writing-mode: vertical-rl` for vertical writing) are also reliably applied.

If you want to design the entire presentation yourself,
specify your own stylesheet with the
<span class="ioprop">input.default-stylesheet</span> property. In this case, the default style is **not applied at all**.
This is more reliable than overriding parts of the default style while keeping it as a base,
because unintended rules (such as paragraph line pitch or page numbers) cannot carry over.

#### Supported syntax

The syntax conforms to [CommonMark](https://commonmark.org/),
with the addition of **tables** (the GitHub Flavored Markdown tables extension).

CommonMark permits raw HTML, so you can mix HTML into Markdown.
For settings that Markdown cannot express (such as adding a `class` attribute, wrapping content in a `<div>`,
or using extended CSS), write HTML directly.

<div class="note">

CommonMark features, including headings, paragraphs, lists, tables, code blocks, links, images, and quotations,
are available.
Extensions outside CommonMark, such as footnotes, definition lists, and automatic heading IDs, are not supported.

</div>

<div class="note">

Under the CommonMark specification, if a full-width punctuation mark immediately precedes the closing delimiter
of `**強調**` (emphasis ending in a colon, period, or similar mark, as in "精密に**：**"),
it is not interpreted as emphasis, and `**` is output literally (a known issue in Japanese Markdown
that also occurs on GitHub and elsewhere). The engine interprets this pattern
as emphasis. This is an intentional extension to the specification.

</div>

#### <a id="style-markdown-html">Generated HTML<span class="since">4.0.0</span></a>

The following shows the HTML generated for each notation, to help you write selectors in your CSS.
The entire document is wrapped in the following structure (the default style is inserted as a `style` element
inside head, so CSS loaded later can override it).

```html
<html>
  <head><meta charset="UTF-8"/><style>…default style…</style></head>
  <body>…converted Markdown…</body>
</html>
```

| Notation | Generated HTML |
| --- | --- |
| `# Heading` to `###### Heading` | `h1` to `h6` (no `id` attribute is added) |
| Paragraph | `p` |
| `**Emphasis**` | `strong` |
| `*Italic*` | `em` |
| `` `Code` `` | `code` |
| Fenced code block | `code` inside `pre`. A language specification (such as <tt>java</tt> after the opening fence) becomes `class="language-java"` on `code` |
| Indented code block | `code` inside `pre` (no class) |
| `> Quotation` | `blockquote` (containing `p`) |
| Unordered list | `ul` and `li` |
| Ordered list | `ol` and `li`. If the starting number is not 1, `ol` has a `start` attribute |
| `[Link](URL "Title")` | `a href="URL" title="Title"` |
| `![Alternative text](URL "Title")` | `img src="URL" alt="Alternative text" title="Title"` |
| Table (tables extension) | `thead`/`tbody`, `tr`, and `th`/`td` inside `table`. Column alignment such as `:---:` becomes an `align` attribute (`left`/`center`/`right`) on each cell |
| `---` (thematic break) | `hr` |
| Two spaces at the end of a line, or a trailing `\` | `br` |
| `狼狽《ろうばい》` (Aozora Bunko-style ruby)<span class="since">4.0.0</span> | `ruby` and `rt` |
| `｜生前退位《せいぜんたいい》` (explicit ruby base text) | `ruby` and `rt` |

You can write **ruby** (phonetic readings) using the same notation as Aozora Bunko<span class="since">4.0.0</span>.
If a sequence of kanji immediately precedes `《` and `》`, it becomes the ruby base text
(`狼狽《ろうばい》`). For characters other than kanji, or to specify the range explicitly, place `｜` (a full-width vertical bar)
at the beginning of the ruby base text (`｜1970年《いちきゅうななまるねん》`).
If the reading in `《...》` is not kana, it is treated as brackets around a book title or similar text and output as written
— adding `｜` lets you attach readings that are not kana.
Use CSS such as `ruby > rt { font-size: 0.5em; }` to adjust the appearance of ruby.

You can use attribute selectors to override table column alignment
(for example, `td[align="right"] { color: red; }`).
To style code blocks by language, use selectors such as
`pre > code.language-java`.

#### <a id="style-markdown-css">Changing the style<span class="since">4.0.0</span></a>

An easy way to change the default style is to write a `style` element as raw HTML
in the Markdown document. It is read after the default style, so if specificity is equal,
the document's settings take precedence. You can place it anywhere,
but placing it at the beginning makes it easier to read.

```markdown
<style>
@page { size: B5; margin: 15mm; }        /* Paper and margins */
body { font-family: sans-serif; }        /* Use sans-serif for body text */
h1 { border-bottom: 1pt solid #000000; } /* Underline headings */
table { font-size: 0.9em; }
</style>

# Regular Markdown starts here
```

To change the style without editing the document,
you can apply CSS shared by all documents with the
[default stylesheet](#style-css-default-stylesheet)
(the <span class="ioprop">input.default-stylesheet</span> I/O property). However, this stylesheet is
applied **before Markdown's default style**, so the default style takes precedence
when the declarations are otherwise equal. Use `!important` when overriding
the same properties as the default style.

```css
/* Example of overriding Markdown defaults from the default stylesheet */
@page { size: B5 !important; margin: 15mm !important; }
body { font-family: sans-serif !important; }
```

### <a id="style-image">Images</a>

#### Supported images

JPEG, GIF, PNG, WebP, and SVG images are supported by default. As in a typical browser, you can include images in a document with the img,
object, or embed element. You can also display images with the CSS <span class="cssprop">background-image</span> property
or the <span class="cssprop">content</span> property.

You can also read an image directly and convert it to PDF. This produces a one-page PDF whose entire page is the image.

As in a browser, you can control how the content of a replaced element (such as img) fits its box with the CSS
<span class="cssprop">object-fit</span> property<span class="since">4.0.0</span>
(fill / contain / cover / none / scale-down) and the
<span class="cssprop">object-position</span> property<span class="since">4.0.0</span>.
Content extending outside the box is clipped.

#### <a id="style-image-jai">Image formats that can be read and written</a>

The following formats are available without additional components.

| Format | Input | Output |
| --- | --- | --- |
| PNG | ○ | ○ |
| JPEG | ○ | ○ |
| GIF | ○ | ○ |
| WebP<span class="since">4.0.0</span> | ○ | — |
| BMP | ○ | ○ |
| TIFF | ○ | ○ |
| WBMP | ○ | ○ |
| SVG | ○ | — |

For details about output, see
<a href="#style-output-image" class="pageref">Image output</a>.

<div class="note">

<strong>JPEG 2000 support is not bundled.</strong>
To use it for input, add a Java Image I/O JPEG 2000 reader to the classpath.
For <a href="#style-pdf-image" class="pageref">JPEG 2000 compression</a>,
add JDeli to the classpath separately.

</div>

Images are read through Java Image I/O,
so you can handle formats other than those listed above by adding the corresponding reader to the classpath.
Additional formats are handled as described under "Other images" below.

#### <a id="style-image-raster">Raster (bitmap/pixelmap) images</a>

##### GIF / PNG images

GIF and PNG transparency is preserved in the PDF. However, when you output PDF 1.3 or earlier,
PNG semitransparency is not reproduced accurately; pixels are either transparent or opaque.

For animated GIFs, only the first frame is used; there is no animation.

##### <a id="style-image-jpeg">JPEG images</a>

JPEG images can be included directly in the PDF without modification. You can also set the <span class="ioprop">output.pdf.jpeg-image</span>
I/O property to to-flate to decode images before including them in the PDF, but this increases the PDF size.

JPEGs encoded in CMYK or YCCK (common formats for print images) can also be read.

##### Other images

If you add Java Image I/O readers to enable other image formats,
those formats are handled in the same way as GIF / PNG images.
Semitransparency is reproduced in the PDF for formats that support it, and only the first frame is used for animated images.

##### Image resolution

The size of an embedded image in the layout depends on <span class="ioprop">output.resolution</span>.
The default is 96 dpi. For example, with this default, if an img tag has height set to 100,
or if the img tag has no size setting and the original image height is 100, the resulting layout height is 75 pt.

#### <a id="style-image-svg">SVG images</a>

##### Referencing SVG image files

SVG image files (.svg) and GZIP-compressed SVG image files (.svgz) are supported.
However, **PDF output does not support SVG embedded fonts or
gradient fills with transparency
(using stop-opacity in linearGradient or radialGradient).**

Typical browsers support SVG images through the object element, so this method is recommended.

```html
<object data="image.svg" type="image/svg+xml">
Text displayed in browsers that do not support SVG.
</object>
```

You can handle SVG images like other images. You can use the img element, or set them as backgrounds with the <span class="cssprop">background-image</span> property.

##### <a id="style-image-inline-svg">Inline SVG</a>

You can write SVG directly in a document without placing it in a separate file.
This is useful for including dynamically changing images, such as charts, in a document.
To use inline SVG, simply write SVG in the document with a namespace declaration of http://www.w3.org/2000/svg.

```html
<html>
<head>
  <title>Document containing inline SVG</title>
</head>
<body>
<p>A circle appears immediately below.</p>
<svg:svg xmlns:svg="http://www.w3.org/2000/svg"
         xmlns:xlink="http://www.w3.org/1999/xlink"
         preserveAspectRatio="none"
         width="100" height="100"
         viewBox="0 0 100 100"
         xml:space="preserve">
	<svg:g>
		<svg:circle cx="50" cy="50" r="45" stroke="Blue" fill="White" stroke-width="10"/>
	</svg:g>
</svg:svg>
</body>
</html>
```

SVG declarations in an HTML document's stylesheet
(presentation properties such as fill, stroke, stop-color, and opacity)
also apply to elements inside inline SVG<span class="since">4.0.0</span>.
The common technique of controlling icon colors with CSS classes is laid out as written,
and var() references (custom properties) in values are resolved at the SVG's position.
The text color (color) used for fill: currentColor also inherits the computed value
determined by the HTML cascade into the SVG root.
However, only combinations of tag names, classes, ids,
descendant selectors, and child selectors (&gt;) are carried into inline SVG.
Selectors that include HTML elements outside the SVG, and pseudo-classes, do not apply.
Alpha components in rgba() and similar values for fill, stroke, and stop-color are converted to corresponding properties such as fill-opacity.

#### <a id="style-image-broken">When an image cannot be read</a>

If an image cannot be read because the file does not exist, the data is corrupt, or the format is unsupported,
the alternative text specified by the alt attribute is normally displayed. With the <span class="ioprop">output.broken-image</span>
I/O property, you can instead show an × mark in the image area (cross) or leave a blank space (hidden).

### <a id="style-epub">EPUB e-books</a>

You can convert EPUB 2.0 and EPUB 3.0 files<span class="since">3.1.0</span>.

An EPUB file is identified by the content MIME type application/epub+zip or the .epub extension.
