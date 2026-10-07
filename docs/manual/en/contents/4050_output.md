## <a id="style-output">Output file formats</a>

For a long time, PDF was the only output format.
Image output has been supported since 2.0.3.

You can switch the output format by setting a MIME type in <span class="ioprop">output.type</span>.
The default is PDF ("application/pdf").

### <a id="style-output-pdf">PDF output</a>

PDF is the default output format. For details on PDF output features, see <a class="pageref"
	href="#style-pdf-version">PDF features</a>.

#### <a id="style-pdf-version">PDF versions and features</a>

Set the output PDF version with <span class="ioprop">output.pdf.version</span>.
The default is 1.5.

This setting has **two roles**.

<dl>

<dt>Determine which features are available</dt>
<dd>PDF has gained features with each version. If you specify an older version,
	you cannot use features that version does not have. If you try to use a feature
	unavailable in the specified version, <b>a warning is issued and the feature is omitted from the PDF</b>
	(the conversion itself succeeds).</dd>
<dt>Select a conformance profile</dt>
<dd>Standards such as PDF/A and PDF/X define rules for specific uses.
	You also select these with this property.</dd>

</dl>

#### Producing a standard PDF

Specify just the version number. You can specify 1.2 through 1.7, or 2.0.

| Value | Features available from this version onward |
| --- | --- |
| 1.3 | 40–128-bit encryption |
| 1.4 | File attachments, PNG translucency, SVG transparency |
| 1.5 | JPEG 2000 images, object streams (smaller files) |
| 1.7 | Unicode attachment filenames, AES-256 encryption |
| 2.0 | PDF 2.0 (ISO 32000-2) |

**Leave the default of 1.5 unless you have a specific reason to change it.**
Lower it only if you need to distribute files to older viewing environments.

#### <a id="style-pdf-profiles">Conformance profiles</a>

PDF has standards that guarantee compliance with specific rules
for long-term preservation or print production<span class="since">4.0.0</span>.
Choose one for your intended use.

<dl>

<dt>PDF/A —— Long-term preservation</dt>
<dd>This standard aims to preserve the same appearance when you open a file 10 or 20 years later.
	Its rules include mandatory font embedding and no dependence on external files.
	Use it for <b>official documents, contracts, and reports that you need to retain for a long time</b>.</dd>
<dt>PDF/X —— Print production</dt>
<dd>This standard is for files you send to a print shop. It guarantees that color specifications
	and bleed information are complete. Use it for <b>commercial printing</b>.</dd>
<dt>PDF/UA —— Accessibility</dt>
<dd>This standard guarantees that screen readers and similar software can read the document correctly.
	The PDF records the structure of headings and tables.
	Use it when <b>everyone needs to be able to read the document, such as documents from public institutions</b>.</dd>

</dl>

Specify a value in the form "**base PDF version + standard name - number**".
For example, `1.7A-2u` means "PDF/A-2 based on PDF 1.7, conformance level u".

| Value | Standard | Notes |
| --- | --- | --- |
| 1.4A-1 | PDF/A-1b | The oldest and most restrictive (for example, no transparency) |
| 1.7A-2 | PDF/A-2b | Supports transparency, AES encryption, and attachments |
| 1.7A-2u | PDF/A-2u | In addition to the above, all characters can be extracted as Unicode |
| 1.7A-2a | PDF/A-2a | In addition to the above, requires logical structure (tags) |
| 1.7A-3 | PDF/A-3b | Allows arbitrary file attachments (such as electronic invoices) |
| 1.7A-3a | PDF/A-3a | In addition to the above, requires logical structure |
| 2.0A-4 | PDF/A-4 | Based on PDF 2.0 |
| 1.4X-1 | PDF/X-1a:2003 | An older print production standard. CMYK only |
| 1.6X-4 | PDF/X-4 | Supports ICC-based RGB and transparency |
| 2.0X-6 | PDF/X-6 | Based on PDF 2.0 |
| 1.7UA-1 | PDF/UA-1 | Accessible tagged PDF |

**If you are unsure which to choose**, start with `1.7A-2` for long-term preservation,
`1.6X-4` for print production, or `1.7UA-1` or `2.0UA-2` (PDF/UA-2)<span class="since">4.0.0</span> for accessibility.

#### Settings that change automatically when you select a profile

Some settings are overridden automatically to meet conformance requirements.

<dl>

<dt>Fonts are always embedded</dt>
<dd>PDF/A and PDF/X require font embedding.
	<b>Provide fonts that permit embedding.</b>
	Conformance is not possible if only fonts that cannot be embedded (core fonts and CID-keyed fonts) are available.
	See **Font configuration** (pdfg2d manual).</dd>
<dt>Encryption is not allowed</dt>
<dd>PDF/A and PDF/X prohibit encryption.
	If you set <span class="ioprop">output.pdf.encryption</span>, a warning is issued and the setting is ignored.</dd>
<dt>Tagging is enabled automatically</dt>
<dd>Level a PDF/A (1.7A-2a, 1.7A-3a) and PDF/UA (1.7UA-1, 2.0UA-2) require logical structure,
	so tagging is enabled automatically even if you do not set <span class="ioprop">output.pdf.tagged</span>.
	For PDF/UA-1, specifying the language with <span class="ioprop">output.pdf.tagged.lang</span>
	is <b>required</b>.</dd>

</dl>

#### <a id="style-pdf-bidi">Bidirectional text extraction</a>

<dl>
<dt>Leave bidirectional processing to the extractor</dt>
<dd>For lines containing right-to-left text (such as Hebrew or Arabic), glyphs are placed in visual order, and the viewing environment's
	bidirectional algorithm restores logical order (the PDF viewers in Chrome and Edge correctly restore logical order this way).
	Mirrored brackets also retain the character of the displayed glyph in ToUnicode, leaving mirroring to the viewing environment. In tagged PDFs,
	the content order of structure elements (<code>/K</code>) is written in logical order.</dd>
<dt><span class="ioprop">output.pdf.bidi.actual-text</span><span class="since">4.0.0</span></dt>
<dd>When set to <code>true</code>, each reordered line receives its logical-order string as <code>ActualText</code>, and mirrored
	brackets use separate CIDs with ToUnicode mappings to the logical characters (the default is <code>false</code>). This is for extractors such as Acrobat that honor <code>ActualText</code>. It is omitted by default because
	it disrupts extraction order in PDFium and MuPDF. This setting is effective only with PDF 1.5 or later.</dd>
</dl>

#### <a id="style-pdf-encryption">Encryption</a>

You can output encrypted PDFs.
A PDF encrypted with a password cannot be viewed without that password.
Encryption is also required to restrict operations such as copying PDF content.
For PDFs that you distribute widely, you can use encryption without setting a password.

Enable encryption with <span class="ioprop">output.pdf.encryption</span>.
You can choose from the following methods.

| Value | Method | Required PDF version |
| --- | --- | --- |
| v1 | Arcfour 40-bit | No restriction |
| v2 | Arcfour 40–128-bit | 1.3 or later |
| v4 | Arcfour 128-bit / AES-128 | 1.5 or later (1.6 or later for AES-128) |
| v5 | AES-256 | 1.7 or later |

**Choose `v5` (AES-256) for new documents**<span class="since">4.0.0</span>.
Use `v2` if you need to distribute files to older viewing environments.
For `v4`, choose between Arcfour (`v2`) and AES-128 (`aesv2`)
with <span class="ioprop">output.pdf.encryption.v4.cfm</span>.

You can set the encryption strength (the number of bits in the encryption key) with <span class="ioprop">output.pdf.encryption.length</span>.
This applies only to v2. v4 is fixed at 128 bits and v5 at 256 bits.
The strongest encryption is used by default, so you normally do not need to set this.

<div class="note">

PDF/A and PDF/X prohibit encryption. If you enable it, a warning is issued and the setting is ignored.

</div>

Set the password for viewing with <span class="ioprop">output.pdf.encryption.user-password</span>.
If you do not set this property, the PDF is encrypted but anyone can open it. You can also use <span
	class="ioprop">output.pdf.encryption.owner-password</span> to set a password for editing the document in Adobe
Acrobat or similar software.

You can set permissions when you encrypt a PDF.
Use the following eight I/O properties,
whose names begin with output.pdf.encryption.permissions, and set each to true (enabled) or false (disabled).
All are enabled by default.

<dl>

<dt class="ioprop">output.pdf.encryption.permissions.print</dt>
<dd>Printing</dd>

</dl>

<dl>

<dt class="ioprop">output.pdf.encryption.permissions.modify</dt>
<dd>Modifying content</dd>

</dl>

<dl>

<dt class="ioprop">output.pdf.encryption.permissions.copy</dt>
<dd>Copying text and images</dd>

</dl>

<dl>

<dt class="ioprop">output.pdf.encryption.permissions.add</dt>
<dd>Adding and modifying annotations, and filling in PDF forms</dd>

</dl>

The following four apply only to v2 encryption.

<dl>

<dt class="ioprop">output.pdf.encryption.permissions.fill</dt>
<dd>Filling in PDF forms</dd>

</dl>

<dl>

<dt class="ioprop">output.pdf.encryption.permissions.extract</dt>
<dd>Extracting text and images from the document for users with disabilities</dd>

</dl>

<dl>

<dt class="ioprop">output.pdf.encryption.permissions.assemble</dt>
<dd>Adding new pages, bookmarks, and thumbnail images to the document</dd>

</dl>

<dl>

<dt class="ioprop">output.pdf.encryption.permissions.print-high</dt>
<dd>Printing the document at high quality</dd>

</dl>

Form permissions apply when you set <span class="ioprop">output.pdf.forms</span>=true to output HTML forms
as fillable PDF forms (AcroForm). These are not output by default.

<p class="note">PDF viewers such as Adobe
	Reader honor the permissions you set,
	but this does not guarantee that other tools cannot perform those operations on the PDF.</p>

#### <a id="style-pdf-forms">Fillable PDF forms (AcroForm)<span class="since">4.0.0</span></a>

Set <span class="ioprop">output.pdf.forms</span> to true
to output HTML form controls as **form fields you can fill in within the PDF**.
The default is false, which renders only the appearance of the form controls.

```
session.property("output.pdf.forms", "true");
```

The following HTML controls are supported.

| HTML | PDF field |
| --- | --- |
| `input type="text"` (and `password`, plus HTML5 types such as `search` and `email`) | Text field. `maxlength` sets the maximum number of characters |
| `input type="checkbox"` | Checkbox. `value` is the on value, and `checked` sets the initial state |
| `input type="radio"` | Radio button. **Controls with the same name attribute are grouped into one field** |
| `input type="submit" / "reset" / "button"` | Push button |
| `textarea` | Multiline text field |
| `select` | Choice field. The option elements provide the choices |

The `title` attribute provides the field's description (tooltip).
The `disabled` attribute makes the field non-editable.

The following do not become fields: `input type="hidden"`,
`input type="file"`, and `input type="image"`.

<div class="note">

**PDF/X does not allow interactive forms.**
In this case, no fields are created regardless of the setting; only their appearance is rendered.

Changing this setting does not affect the output of documents without forms.

</div>

#### <a id="style-pdf-attachments">File attachments</a>

PDF 1.4 and later allow you to attach files to a PDF. Use the following

- <span class="ioprop">output.pdf.attachments.<i>n</i>.name</span>
- <span class="ioprop">output.pdf.attachments.<i>n</i>.description</span>
- <span class="ioprop">output.pdf.attachments.<i>n</i>.mime-type</span>
- <span class="ioprop">output.pdf.attachments.<i>n</i>.uri</span>

as a set of four properties. <i>n</i> is a sequential number starting at 0, allowing you to attach multiple files.

Of these, <span class="ioprop">output.pdf.attachments.<i>n</i>.uri</span> is required. Set it to the URI of a file previously sent through a driver (client library), or the URL of an accessible file.

<span class="ioprop">output.pdf.attachments.<i>n</i>.name</span> is the filename. You can use non-ASCII characters, but they may appear garbled.
For Japanese filenames, use ASCII characters in <span class="ioprop">output.pdf.attachments.<i>n</i>.name</span> whenever possible, and put the actual filename in <span class="ioprop">output.pdf.attachments.<i>n</i>.description</span>
instead.

<span class="ioprop">output.pdf.attachments.<i>n</i>.mime-type</span> is the file's MIME type.

<span class="ioprop">output.pdf.attachments.<i>n</i>.relationship</span><span class="since">4.0.0</span> specifies
the relationship between the attachment and the body text. Choose `Data` (data on which the body text is based),
`Source` (source manuscript), `Alternative` (another representation of the same content as the body text),
`Supplement` (supplementary material), or `Unspecified`.
Use `Alternative` when **the attachment represents the same content as the body text**,
as with electronic invoices.

#### <a id="style-pdf-facturx">Electronic invoices (Factur-X / ZUGFeRD)<span class="since">4.0.0</span></a>

This standard attaches machine-processable XML to an invoice PDF. It will
become mandatory in stages for business-to-business invoices starting in
September 2026 in France and January 2027 in Germany.

Output PDF/A-3 (`1.7A-3`), attach the XML as `Alternative`, and
set <span class="ioprop">output.pdf.facturx.conformance-level</span>
to embed the XMP metadata required for Factur-X.

```
output.pdf.version=1.7A-3
output.pdf.attachments.0.uri=file:///path/to/factur-x.xml
output.pdf.attachments.0.name=factur-x.xml
output.pdf.attachments.0.mime-type=text/xml
output.pdf.attachments.0.relationship=Alternative
output.pdf.facturx.conformance-level=EN 16931
```

You can change the attachment filename (default: `factur-x.xml`), document type (default: `INVOICE`), and
version (default: `1.0`) with
<span class="ioprop">output.pdf.facturx.document-file-name</span>,
<span class="ioprop">output.pdf.facturx.document-type</span>, and
<span class="ioprop">output.pdf.facturx.version</span>, respectively.

<div class="note">

The XML content is not validated. Provide XML that conforms to the standard.

</div>

#### <a id="style-pdf-output-intent">Output intent (color reference)<span class="since">4.0.0</span></a>

**PDF/X requires the PDF to record the printing conditions
for which its colors are intended**. This is called the output intent.

**Your first choice should be the profile specified by the print shop.** Print shops in Japan commonly use
Japan Color 2001 Coated. The Japan Color
ICC profile is not bundled because redistribution is not permitted.
Obtain it from your print shop or the Japan Printing Machinery Association, and specify it as follows.

```
output.pdf.version=1.6X-4
output.pdf.output-intent.identifier=JC200103
output.pdf.output-intent.condition=Japan Color 2001 Coated
output.pdf.output-intent.icc-profile=file:///path/to/JapanColor2001Coated.icc
```

Set <span class="ioprop">output.pdf.output-intent.identifier</span> to
the characterization identifier in the ICC registry (such as `JC200103` or `FOGRA39`), and
<span class="ioprop">output.pdf.output-intent.icc-profile</span> to
the URI of the ICC profile.

**If you do not specify one**, PDF/X (all versions) and <span class="ioprop">output.color</span>=<tt>cmyk</tt> use
the bundled ISO Coated v2 300% (ECI) (identifier `FOGRA39`, for coated paper in Europe) as the output intent.
This is a compatibility fallback when the print shop has not specified a profile, not the standard for Japan.
Standard PDFs and PDF/A continue to use sRGB.

<div class="note">

**For PDF/X, the specified ICC profile is fully validated.** If it cannot be read,
is not an output profile (class=prtr), is not CMYK, or has an empty identifier,
conversion fails with error 380E. For formats other than PDF/X, a warning is issued,
and output continues with the default profile.

</div>

##### <a id="style-pdf-cmyk-conversion">RGB-to-CMYK conversion<span class="since">4.0.0</span></a>

When converting to CMYK (<tt>1.4X-1</tt>, or <span class="ioprop">output.color</span>=<tt>cmyk</tt>),
RGB colors are **converted using the output intent's ICC profile** (since 4.0.0).
Because this differs from the simple formula used in 3.x, color values change even for the same document.
The conversion intent is fixed at perceptual. <span class="ioprop">output.pdf.rendering-intent</span> affects
only the `ri` operator recorded in the PDF, not this conversion.

- **Achromatic (R=G=B) solid colors** and **gradients whose color stops are all achromatic**
  use only K (not rich black).
- **Every pixel in mixed-color gradients, meshes, and images undergoes ICC conversion**.
  Gray and black in images use all four colors. <span class="cssprop">box-shadow</span>,
  <span class="cssprop">filter</span> blurs, and SVG effects are rasterized before conversion,
  so they also produce rich black.
- **Gray** (<span class="ioprop">output.color</span>=<tt>gray</tt>, DeviceGray),
  **spot colors**, and **colors already in CMYK or four-component (CMYK) JPEGs** are not converted.
  They are output as they are, on the assumption that they were created for the same printing conditions.
  CMYK JPEGs are expected to use the Adobe format (with an APP14 marker) written by Photoshop and similar software.
  Avoid four-component JPEGs without this marker, because viewers interpret their colors differently.

| Setting | Colors originating in RGB | Suitable print production destination |
| --- | --- | --- |
| <tt>1.4X-1</tt> | Converts everything to CMYK (including images) | Print shops that accept only CMYK |
| <tt>1.6X-4</tt> | Preserves RGB with an ICC profile (ICCBased sRGB). Also supports transparency | Print shops that accept a mix including RGB |
| <tt>1.6X-4</tt> + <span class="ioprop">output.color</span>=<tt>cmyk</tt> | Converts everything to CMYK | When you want to supply PDF/X-4 after completing color conversion yourself |

#### <a id="style-pdf-compression">PDF compression formats</a>

By default, the output PDF is compressed to keep its size as small as possible.
As a result, the output PDF is binary data.

With <span class="ioprop">output.pdf.compression</span>,
you can generate a text-format or uncompressed PDF.
The ascii setting compresses the PDF, but produces ASCII text.
The file is slightly larger, but you can copy and paste it directly as text.
The none setting does not compress the PDF at all, and drawing commands and similar content appear as plain text.
Images use HEX format, making the file very large, but this is useful for examining the output PDF in a text editor.

##### <a id="style-pdf-image">Compression formats for images in PDFs</a>

Images often account for most of the size of a large PDF.
Choosing an appropriate image compression format can therefore reduce PDF size.

By default, JPEG images are used in the PDF without modification, and other images use FlateDecode (lossless compression).

You can specify the image compression format with <span class="ioprop">output.pdf.image.compression</span>
<span class="since">2.0.3</span>.
The default is flate; jpeg uses JPEG compression.
You can also specify jpeg2000, but <b>a JPEG 2000 encoder is not bundled</b>.
If JDeli has not been added to the classpath, specifying this format causes conversion to fail.
Image compression does not apply to JPEG / JPEG2000 by default, but setting <span class="ioprop">output.pdf.jpeg-image</span> to recompress
recompresses JPEG / JPEG2000 images<span class="since">2.0.3</span>
(however, JPEG is not recompressed as JPEG, nor JPEG2000 as JPEG2000; conversion between JPEG and JPEG2000 is performed).

JPEG / JPEG2000 recompression is lossy and degrades images, so you may want to avoid it for small images such as icons.
You can therefore specify that images below a certain size always use FlateDecode compression <span
	class="since">2.0.3</span>. Set the threshold in <span class="ioprop">output.pdf.image.compression.lossless</span>
as the sum of the image's height and width in pixels. The default is 200.
For example, an image 90 pixels high and 110 pixels wide is recompressed as JPEG / JPEG2000,
while an image 80 pixels high and 100 pixels wide uses lossless FlateDecode compression. However, if the original image is JPEG / JPEG2000 and
matches the format specified in <span class="ioprop">output.pdf.image.compression</span>,
it is embedded in the PDF in its original format.

#### <a id="style-pdf-encoding">Character encoding of the PDF viewing environment</a>

Font names in PDF 1.2 and earlier, and attachment filenames in PDF 1.6 and earlier,
depended on the character encoding of the PDF viewing environment.
Later PDF versions use Unicode, so you do not need to pay particular attention to the viewing environment's character encoding.

When generating an older PDF version, set <span class="ioprop">output.pdf.platform-encoding</span>
to the expected viewing environment's encoding to prevent garbled characters.
The default is MS932 (the Windows version of Shift_JIS), which generally causes no problems in Japanese environments.
However, characters may appear garbled if the relevant names contain characters unsupported by the specified encoding,
or if the viewing environment uses a different encoding.

#### <a id="style-pdf-meta">Setting creation and modification times and the file ID</a>

By default, the PDF creation and modification times (CreationDate, ModDate) use the current time from the clock in the environment where Copper
PDF runs. The file ID uses a random number.

You can also set the creation and modification times and file ID explicitly<span class="since">2.0.9</span>.

To set the creation and modification times, use

- <span class="ioprop">output.pdf.meta.creation-date</span>
- <span class="ioprop">output.pdf.meta.mod-date</span>

respectively. Use a date format such as "2009-05-22 21:10:14" or "2009-06-04 15:53:02
+09:00" (to specify the time zone explicitly).

Set the file ID with <span class="ioprop">output.pdf.file-id</span>.
It must be a hexadecimal number of exactly 32 digits, such as "000067A36902BF8D2A0617B9CD02BCFA".

#### <a id="style-pdf-watermark">Watermarks</a>

You can output a watermark image in front of or behind PDF content <span class="since">2.1.8</span>.
Watermarks are available in PDF 1.4 and later.

You can achieve a similar effect with CSS properties such as <span class="cssprop">background-image</span>,
but PDF also lets you show watermarks only when printing, while hiding them on screen.

Set <span class="ioprop">output.pdf.watermark.uri</span> to the absolute address of the image to use as a watermark.
The image appears as a repeating pattern on every page of the PDF.

By default, the background appears behind the content. Set <span class="ioprop">output.pdf.watermark.mode</span> to "front" to place it in front.

You can set watermark opacity with <span class="ioprop">output.pdf.watermark.opacity</span>.
For example, "0.5" makes the watermark translucent.

The following output example specifies an SVG image in <span class="ioprop">output.pdf.watermark.uri</span>
and "back" in <span class="ioprop">output.pdf.watermark.mode</span>.

<div title="Watermark behind the content" class="figure">
	<img src="images/watermark-back.jpg" style="width: 120mm;" />
</div>

The following output example specifies an SVG image in <span class="ioprop">output.pdf.watermark.uri</span>,
"front" in <span class="ioprop">output.pdf.watermark.mode</span>, and 0.5 in <span
	class="ioprop">output.pdf.watermark.opacity</span>.

<div title="Watermark in front of the content" class="figure">
	<img src="images/watermark-front.jpg" style="width: 120mm;" />
</div>

#### Showing watermarks only when printing or only on screen

<span class="ioprop">output.pdf.watermark.view</span> and <span
	class="ioprop">output.pdf.watermark.print</span> control whether the watermark appears on screen and when printing, respectively.
Both are enabled by default. For example, setting <span class="ioprop">output.pdf.watermark.view</span> to "false" shows the watermark only when printing.

When the watermark is behind the content, these settings have no effect in PDF 1.4 or earlier.

#### <a id="style-pdf-a1">Outputting PDF/A-1b-conformant files</a>

PDF/A-1b is the oldest PDF/A conformance level and has the strictest restrictions.
For new documents, consider
<a href="#style-pdf-profiles" class="pageref">PDF/A-2 or later</a>, which supports transparency and attachments.

Set <span class="ioprop">output.pdf.version</span> to "1.4A-1" to generate PDF/A-1b-conformant files<span
	class="since">2.1.0</span>. This mode generates PDF 1.4, but the following features of standard PDF
1.4 are unavailable.

- Encryption
- Attachments
- Translucent rendering (translucent watermarks, translucent PNGs, SVG opacity settings, etc.)

Only embedded fonts are used. CID-keyed fonts, external fonts, and the core 14 fonts are also unavailable.

#### <a id="style-pdf-vp">PDF viewer display settings<span class="since">3.0.2/2.1.11</span></a>

CopperPDF lets you configure how a PDF appears when opened in a viewer (ViewerPreferences).
The effect of ViewerPreferences depends on the viewer (such as Adobe Reader) that opens the PDF.
The user's environment may not display it exactly as configured, but these settings are very useful for office processing and printing within an organization.

Use I/O properties whose names begin with "output.pdf.viewer-preferences." to set ViewerPreferences.
For example, to preset the number of copies to print to 3, set <span class="ioprop">output.pdf.viewer-preferences.num-copies</span>
to 3.

For a list of settings, see the [I/O property list in the reference section](#appx-ioprop-output.pdf.viewer-preferences.hide-toolber).

#### <a id="style-pdf-js">JavaScript executed when a PDF is opened<span class="since">3.0.2/2.1.11</span></a>

Use <span class="ioprop">output.pdf.open-action.java-script</span> to specify JavaScript to run when a viewer opens the PDF.

For example, set it to "print();" to display the print dialog when the PDF opens.

For the PDF JavaScript specification, see the documentation published by Adobe ([http://www.adobe.com/content/dam/acom/en/devnet/acrobat/pdfs/js_api_reference.pdf](http://www.adobe.com/content/dam/acom/en/devnet/acrobat/pdfs/js_api_reference.pdf)).

### <a id="style-output-image">Image output</a>

In addition to PDF, you can output raster (pixel map) images such as JPEG. <span
	class="since">2.0.3</span> Set <span class="ioprop">output.type</span> to
an image MIME type, such as "image/jpeg".

You can specify `image/png` `image/jpeg` `image/gif` `image/bmp`
`image/tiff` `image/vnd.wap.wbmp`.
Add Java Image I/O writers to support more output formats.
→ <a href="#style-image-jai" class="pageref">Image formats that can be read and written</a>

#### Image output limitations

<b>Core fonts</b> (pdfg2d manual) and <b>CID-keyed fonts</b> (pdfg2d manual) cannot be rendered.
To display fonts correctly, you must use <b>embedded fonts</b> (pdfg2d manual).
For font configuration, see <b>Font types</b> (pdfg2d manual).

#### <a id="style-output-image-transparent">Making the background transparent</a>

Set <span class="ioprop">output.image.transparent</span> to `true`
to **render without filling the background**<span class="since">4.0.0</span>.
Areas where nothing is drawn remain transparent.
The default is `false`, which fills the background with white before rendering.

If you specify a background color for a page or element, that area is opaque.
This is intended for uses such as extracting part of a document and overlaying it on another image.

```java
session.property("output.type", "image/png");
session.property("output.image.transparent", "true");
```

<div class="note">

**This applies only to formats that preserve transparency.** PNG, GIF, and TIFF can preserve it,
but JPEG, BMP, and WBMP cannot. If you enable this for a format that cannot preserve transparency, the background remains white,
and message <a href="#appx-messages" class="pageref">2824</a> is issued.
The available writers in the runtime environment are queried to determine which formats preserve transparency
(adding Java Image I/O writers can add more).

</div>

#### <a id="style-output-image-resolution">Image output resolution</a>

You can set the output image resolution with <span class="ioprop">output.image.resolution</span>
<span class="since">2.0.4</span>.
The unit is dpi: the number of pixels used to render an object whose CSS length is 1in. The default image resolution is 96 dpi.
Because 1 pt is 1/72 in, 1 pt is slightly larger than one pixel by default.

To make 1px correspond to one pixel in the output image, set <span class="ioprop">output.resolution</span>
and <span class="ioprop">output.image.resolution</span>
to the same value. Both default to 96.

The image's pixel dimensions are the page dimensions multiplied by the resolution, rounded to the nearest integer. For PNG and JPEG, this resolution is also written as image metadata (PNG pHYs and JPEG JFIF)<span class="since">4.0.0</span>. When you place the image in printed material or typesetting software, it is handled at the size corresponding to the specified resolution.

<span class="notice">In 2.0.8 and earlier, <span class="ioprop">output.image.resolution</span> defaulted to 72, and a bug prevented the resolution from being applied correctly.
	In 2.0.9 and later, set the value calculated as (previous setting × <span class="ioprop">output.resolution</span> /
	72).
</span>

### <a id="style-output-svg">SVG output</a>

You can output SVG. Set <span class="ioprop">output.type</span> to "image/svg+xml".

SVG output converts all text to outlines. You can open and edit the output SVG in drawing software such as Adobe Illustrator
CS.

#### Preserving text<span class="since">4.0.0</span>

Set <span class="ioprop">output.svg.text</span> to `keep` to write text
<b>as `<text>`</b> without converting it to outlines. WOFF2 containing only the glyphs used
and images are included in the SVG as `data:`, making it <b>self-contained in a single file</b>.

| | `outline` (default) | `keep` |
| --- | --- | --- |
| Text | All shapes (path) | `<text>` + embedded WOFF2 |
| Screen reading and search | Not available | Available through `aria-label` and `data-copper-text` |
| External files | None | None (both modes are self-contained in a single file) |
| Appearance | The same | The same (verified in a browser to match the actual fonts) |

Glyphs are written using <b>Private Use Area (PUA) code points</b> to output GIDs directly. They display correctly,
but copying produces private-use characters. The original strings are in `aria-label` and `data-copper-text`,
so use those for screen reading, search, and extraction.

Latin text covered by the core 14 fonts retains <b>its actual characters</b>, without subsetting.
To embed everything, set <span class="ioprop">output.pdf.fonts.policy</span> to
`embedded` (the default for SVG is `core-embedded`).

In documents with many pages, the same fonts are duplicated on each page. <b>To share them,
use page-split SVG</b> (the next section).

### <a id="style-output-paged-svg">Page-split SVG output</a>

To deliver individual pages to an e-book viewer or similar application, set <span class="ioprop">output.type</span> to
`application/vnd.copper.paged-svg`. This produces a result set with the following relative URIs,
rather than a single file. The existing single-SVG output behavior of `image/svg+xml` is unchanged.

```text
manifest.json
metrics.json
pages/0001.svg
pages/0001.json
pages/0002.svg
pages/0002.json
assets/fonts/font-0001.woff2
assets/images/<SHA-256>.png
assets/images/<SHA-256>.jpg
```

`manifest.json` contains the total page count, binding direction (`binding`), page progression direction (`pageProgressionDirection`:
`ltr`/`rtl`; if the root `writing-mode` is `vertical-rl`, this is `rtl`. Readers should arrange pages using this value, not the binding direction——
`binding` is `single` if <span class="ioprop">output.print-mode</span> is not set. The same applies to an EPUB's `index.json` and its `binding`: even when `page-progression-direction` is `ltr`, it can be `single`), document metadata, a hierarchical table of contents, document-wide
anchors, page dimensions, and the URIs and SHA-256 hashes of each page and shared resource. Page JSON contains
searchable text and positions, links, anchors within the page, and mappings between original characters and subset glyphs.
Each SVG references shared resources by relative URI, so the receiver must preserve the result URIs' directory structure
when saving them. Identical images are deduplicated by their content's SHA-256 hash, and font subsets are also
shared within the document (EPUBs use an independent bundle for each spine item——
<a href="#style-output-paged-svg-epub" class="pageref">EPUB bundles for each item</a>).

If you convert web content to SVG and display it on the same website, <span class="ioprop">output.paged-svg.resources</span>=`source`
lets you reference web images at their original URLs without copying them (the manifest's `images[].source`).

By default, shared images contain the retrieved images as they are. Even photos displayed at a small size in the type area retain their original dimensions.
To reduce size, use <span class="ioprop">output.paged-svg.image.compression</span>=`jpeg` (recompresses
raster images without transparent areas as JPEG) and <span class="ioprop">output.paged-svg.image.max-width</span>/
<span class="ioprop">output.paged-svg.image.max-height</span> (reduces dimensions to a pixel limit).
These keys have the same meaning as PDF's `output.pdf.image.*`.

If you also distribute the same book as a PDF, <span class="ioprop">output.paged-svg.pdf</span>=`true`
lets you **produce the PDF in the same conversion**. The result set includes `document.pdf` (the `manifest.json` entry `pdf`),
whose pagination always matches the page SVGs. The PDF is returned as one result at the end of conversion.

Page URIs use sequential numbers, so unlike shared resources, their URIs are not SHA-256 hashes.
In `pages[]`, `svgSha256` and `dataSha256` are **for the receiver**; Copper itself does not read them.
You can use them for the following two purposes.

- **Checking integrity.** You can check whether a received or stored page SVG or page JSON
  is corrupted. Like `sha256`, these values apply to **the bytes actually delivered**:
  if you compress the output with <span class="ioprop">output.paged-svg.compression</span>,
  they apply to the bytes after compression.
- **Retrieving only changed pages.** When you lay out the same book again, compare each page's
  SHA-256 hash with the previous `manifest.json` to retrieve
  only pages whose content has changed. Page URIs remain sequential, so comparing URIs cannot detect changes.

If you use neither feature, set <span class="ioprop">output.paged-svg.page-checksums</span>=`false`
to omit the SHA-256 hashes from `pages[]` (the entire `manifest.json` must arrive before the first page is displayed,
so this helps with long books). Shared resources retain their `sha256` values.

<div class="note">
<p>
<b>If you build a reader that inserts multiple page SVGs into one HTML document,
specify <span class="ioprop">output.paged-svg.base-uri</span>.</b>
By default, each page SVG references shared resources as <tt>../assets/…</tt>, relative to
<tt>pages/</tt>. These references resolve if you open each page as a separate document
in an <tt>&lt;object&gt;</tt> or <tt>&lt;iframe&gt;</tt>, but inserting a page SVG into a host document
changes the base URI, causing resolution to fail. <b>Because the body text uses private-use characters,
the entire page appears blank if the font cannot be resolved</b>.
Give <span class="ioprop">output.paged-svg.base-uri</span> an absolute URL prefix
(<tt>https://example.com/book/</tt>) to make references resolve regardless of the host document's location.
The same prefix is added to both font subsets and images.
</p>
</div>

<div class="note">
<p>
<b>When serving <tt>.svgz</tt> and <tt>.json.gz</tt> as static files,
add <tt>Content-Encoding: gzip</tt>.</b> If you omit it, the browser tries to
interpret the gzip data directly and fails. For nginx, use
<tt>location ~ \.svgz$ { add_header Content-Encoding gzip; default_type image/svg+xml; }</tt>
to configure this.
</p>
</div>

<div class="note">
<p>
<b>Use page JSON to implement text selection, search, and links in a reader.</b>
The contents of <tt>&lt;text&gt;</tt> in page SVGs are private-use code points, so
Ctrl+F and copying do not work when you open the plain SVG in a browser. The original text and positions are in
<tt>text</tt> in the page JSON, with <tt>value</tt>, <tt>font</tt>, <tt>size</tt>,
<tt>transform</tt>, and <tt>bounds</tt>, allowing you to overlay a transparent text layer.
</p>
</div>

Images are returned before the first page that references them, and page SVGs and JSON are returned sequentially as pages are finalized.
Shared WOFF2 is returned after the glyphs used throughout the document are determined, and `manifest.json` is returned
as the final result. The receiver can therefore start retrieving page data in advance, but should begin
displaying text correctly on pages that use shared WOFF2 only after receiving the referenced font results.

Body text is displayed with shared WOFF2 and characters that map the GIDs determined during layout
to XML 1.0-safe BMP Private Use Area (PUA) code points. Once a logical subset uses all 6,400 BMP private-use characters,
further characters fall back to outlines that preserve their appearance, instead of producing invalid XML or incorrect glyphs.
Original strings are retained in page JSON, and also in `aria-label` and `data-copper-text` in text SVGs.
Text falls back to outlines to preserve appearance when glyphs cannot be retrieved, when glyphs are in color,
or when the paint is anything other than a solid `Color`. The original strings remain in page JSON in these cases as well.

WOFF2 uses Brotli compression conforming to RFC 7932, and the end of the file is aligned
to the 4-byte boundary required by browser implementations. Brotli quality is fixed at 5——
measurements on a 7.75 MB font showed that quality 11 took 126 times as long as quality 5
for only a 5.2-percentage-point improvement in compression, which is not worthwhile. PNGs are output as shared PNGs named by their content hashes.
For JPEGs that do not require EXIF rotation, the original JPEG bytes are saved as shared resources
without recompressing the pixels. Images that require rotation or mirroring, and other raster formats,
are saved as PNG after their appearance is finalized.

Generating WOFF2 does not change the font license. The original OpenType font's OS/2
`fsType` is read, and fonts marked Restricted License Embedding, No Subsetting, or Bitmap Embedding Only
are not converted to WOFF2; the corresponding characters are converted to outlines.
The generated WOFF2's OS/2 and `manifest.json` retain the original `fsType`.
Even if the machine-readable flags are 0, web delivery, subsetting, and redistribution are not necessarily permitted,
so check the font's license as well.

To generate shared WOFF2, set <span class="ioprop">output.pdf.fonts.policy</span> to
`embedded`. Although the property name still refers to PDF, it also selects
font sources for page-split SVG layout. Core fonts and CID-keyed fonts have no redistributable
glyph programs, so selecting them causes the corresponding characters to fall back to outlines that preserve their appearance.

On the command line, specify `-outdir` (`--output-directory`). `-out` is for a single file
and cannot be used for this result set.

```
copper -in book.epub -if application/epub+zip \
  -p output.type=application/vnd.copper.paged-svg \
  -p output.pdf.fonts.policy=embedded \
  -outdir book-pages
```

The output directory must either not exist or be empty. In the Java API,
pass `ResourceDirectoryResults` to `CTISession.setResults` to save results
with the same relative URI structure. The saving implementation rejects absolute paths, parent-directory references, drive names, query/fragment,
and duplicate result URIs.

#### <a id="style-output-paged-svg-zip">Receiving a single ZIP file</a>

Set <span class="ioprop">output.type</span> to
`application/vnd.copper.paged-svg+zip` to receive <b>the same content as above in a single ZIP file</b>
<span class="since">4.0.0</span>. Extracting it produces the same structure as directory output,
and the references in `manifest.json` resolve as they are.

Because there is only one result, you can also receive it through <b>a single REST request without a session</b>
(`POST /transcode` in <b>HTTP/REST interface</b> (server product manual))
——a result set cannot be received this way.

```
curl -o book.zip   -F rest.user=user -F rest.password=******   -F output.type=application/vnd.copper.paged-svg+zip   -F "rest.main=@book.html;type=text/html"   http://localhost:8097/transcode
```

The files inside the ZIP are not individually compressed (<span class="ioprop">output.paged-svg.compression</span> is
ignored). ZIP itself provides compression, so applying both would compress twice, and the extracted filenames
should end in `.svg` and `.json`.

#### <a id="style-output-paged-svg-epub">EPUB bundles for each item</a>

Converting an EPUB produces an <b>independent</b> bundle for each spine item (the included XHTML),
with a single `index.json` above them<span class="since">4.0.0</span>.

```text
index.json
items/0001/manifest.json
items/0001/metrics.json
items/0001/pages/0001.svg
items/0001/pages/0001.json
items/0001/assets/fonts/font-0001.woff2
items/0001/assets/images/<SHA-256>.png
items/0002/manifest.json
items/0002/pages/0001.svg
...
```

The contents of `items/NNNN/` are <b>exactly the output you get when converting that item as a single document</b>.
Page numbers, font subsets, images, and `metrics.json` are all local to the item,
and `manifest.json` has the structure described above. The number `NNNN` is fixed by the item's position in the spine;
excluded items also consume a number.

`index.json` contains the following.

| Name | Content |
|---|---|
| `composition` | `"epub"` |
| `binding` / `pageProgressionDirection` | Binding direction and the OPF `page-progression-direction` |
| `pageCount` | Total page count of the items laid out |
| `metadata` | Title, author, language, identifier, etc. (OPF metadata) |
| `items[]` | Items in spine order. `index`, `idref`, `uri` (item path), `included`, and, for items laid out, `manifest` (the item's `manifest.json`), `firstPage` (the overall page number), and `pageCount` |
| `toc[]` | Table of contents (nav/ncx). `title`, `uri`, `fragment`, `item` (the target item's `index`), and `children` |

Items are independent so that <b>sequential and parallel layout produce identical output</b>.
This provides three benefits at once.

- <b>Partial re-layout.</b> When the font size changes in an e-book reader,
  <span class="ioprop">input.epub.spine</span> lets you lay out <b>only the chapter currently being read</b> again.
  Because item numbers are fixed, you can overlay partial output directly onto the complete output.
- <b>Parallel processing.</b> The number of items laid out concurrently is set by <span class="ioprop">processing.concurrency</span> (automatic by default),
  and results are released in spine order. The first item streams out as it is laid out,
  while subsequent items are laid out in the meantime and then sent together when their turn comes.
  Measurements (a Japanese book with 30 items and 372 pages): 102 seconds sequentially, 50 seconds with concurrency 2, and 40 seconds with concurrency 4.
  Performance levels off at 4 because the longest chapter becomes the bottleneck.
- <b>Low-cost verification.</b> Tests lay out the same input with different concurrency settings
  and confirm that every result is byte-for-byte identical.

```
copper -in book.epub -if application/epub+zip \
  -p output.type=application/vnd.copper.paged-svg \
  -p input.epub.spine=3 \
  -outdir book-pages-ch3
```

The tradeoff is duplicated font subsets. Items use different sets of characters, so their subsets cannot be shared.
In measurements that split a Japanese book (Natsume Soseki's Kokoro, 350 pages) into 10 items, the total font size
increased from 246 KB to 987 KB, but this added only 6.7% to the total output (11 MB). For Latin text alone, the difference is negligible.

Page numbers start at 1 within each item, and `index.json` provides the overall page number in `firstPage`.
The `page-number` messages (CTIP) use overall page numbers, so clients can continue
tracking progress as before. For links from the table of contents and links across items, look up the item using `items[].uri`,
then use that item's `manifest.json` and its `anchors` to find the position.

EPUB output as PDF or images continues to produce one continuous book, with each item always starting on a new page.

#### Font subset scope

You can choose whether to create <b>one subset for the entire document or a separate subset for each page</b>
with <span class="ioprop">output.paged-svg.font-scope</span>
<span class="since">4.0.0</span>.

| Value | Behavior |
|---|---|
| `document` | Default. One for the entire document. Produces the smallest total size. <b>For EPUB, one per spine item (the included XHTML)</b>, emitted when that item's layout finishes |
| `page` | Creates a subset for each page and emits it <b>before that page's SVG</b> |

The default, `document`, minimizes total size, but <b>the required glyphs are not known
until all pages have been laid out</b>, so the subset can only be emitted at the end. The body text uses private-use characters,
and their glyphs exist only in the font, so <b>the receiver cannot render a single character until conversion finishes</b>.

With `page`, rendering can start as soon as the first page and its fonts arrive. A reader that retrieves
only the visible pages and those immediately before and after them also downloads less. The tradeoff is total size.

Measurements (Natsume Soseki's Kokoro, A5, 350 pages, Japanese text):

| | `document` | `page` |
| --- | ---: | ---: |
| Fonts | 0.25 MB (4 files) | 6.98 MB (1,069 files) |
| Page SVGs | 8.83 MB | 8.82 MB |
| Total output | **9.20 MB** | **16.14 MB** (1.75 times) |
| Conversion time | 15.4 seconds | 17.5 seconds (+14%) |

One page uses 104 distinct characters, about one tenth of the document's total of 1,102, but the subset is
<b>only 1/12 the size</b>, not 1/28——because of WOFF2's fixed overhead and cmap and hmtx.

The amount a reader actually downloads reverses the comparison.

| Reading pattern | `document` | `page` |
| --- | ---: | ---: |
| Only 3 pages | About 340 KB | About 150 KB |
| Reading all 350 pages | 9.2 MB | 16.1 MB |

The crossover point is around 12–13 pages. <b>Use `page` for selective reading and initial rendering, and
`document` for reading the whole book</b>. For documents containing only Latin text, the difference is negligible.

#### Returning compressed output

Page SVGs and page JSON are <b>returned with gzip compression by default</b>
<span class="since">4.0.0</span>.
You can change this with <span class="ioprop">output.paged-svg.compression</span>.

| Value | Behavior |
|---|---|
| `gzip` | Default. Returns compressed page SVGs as `.svgz` and page JSON as `.json.gz` |
| `none` | Returns them as they are |

**When serving `.svgz` and `.json.gz` from a static web server,
add `Content-Encoding: gzip`.** Otherwise, the browser
receives the content as raw gzip bytes.

Measurements on a 314-page book in vertical writing showed a 78% reduction in page SVG size and a 79% reduction in page JSON size,
with **total output reduced by 56%, from 15.0 MB to 6.6 MB**. Conversion time is almost unchanged
(in fact, it becomes slightly faster because less data is written).

**Only text-based results are compressed.** Shared WOFF2 and PNG/JPEG files are already
compressed, so gzip does not reduce their size (measurements showed a 0.1% increase for WOFF2 and a 1.7% reduction for PNG).
`manifest.json` is the entry point, so it is left as it is.

In `manifest.json`, `sha256` applies to **the bytes actually delivered**, after compression.
You can check them directly against the received files.

On fast connections, round-trip time changes very little. This helps with slow connections, metered
connections, and storing results as received.

#### Delivering shared resources

Use <span class="ioprop">output.paged-svg.resources</span> to choose how shared resources
(font subsets and images) reach the receiver.
**This setting applies to both fonts and images together.**

| Value | Behavior |
|---|---|
| `reference` | Default. Outputs separate files and references them with `../assets/…` |
| `embed` | Embeds resources in page SVGs with `data:`. Does not output separate files <span class="since">4.0.0</span> |
| `omit` | Writes only references and does not return the resource data |

These three options are mutually exclusive. **Page SVG appearance is the same** with all of them.
Only resource delivery changes.

##### reference — For directory output

No matter how many pages use the same image, only one copy is needed, and relative URIs work as they are.

##### embed — For delivery that cannot preserve relative URIs

This is for delivery methods that cannot resolve references, such as extracting
a single page SVG and sending it elsewhere. The same image is duplicated on each page, so the total size
increases. Even with `embed`, fonts remain references to shared WOFF2——the subset is not finalized
until the entire document has been laid out, and embedding it in each page would require running
Brotli compression once for every page.

##### omit — For the second and subsequent conversions

When you lay out the same book again with only the font size or screen size changed, the font subsets and
images are exactly the same as before. With `omit`, **only the resource data** is withheld.
Reference URIs in page SVGs and entries in `manifest.json` remain unchanged, so the receiver can
reuse resources previously saved under the same URIs.

For fonts with `omit`, `manifest.json` contains `"omitted":true`, and `sha256` and `bytes`
are omitted——neither a hash nor a byte count can be obtained without building the WOFF2.
Image `sha256` values are the resource URIs themselves, so they remain even with `omit`.

<div class="note">
<strong>This setting is for transfer and storage size, not speed.</strong>
Measurements on a 314-page book in vertical writing (one pass) showed a difference of only 121 ms (6%),
but output <strong>decreased by 27%</strong>, from 15.0 MB to 10.9 MB.
</div>

Use `reference` to receive everything the first time, then use `omit` for subsequent conversions. If you change
anything other than font size or screen size, the required glyphs or images may change, and referenced resources
may not exist in the previous output. The receiver should check that it has all the URIs listed in `manifest.json`.

#### Reusing image dimensions

`metrics.json` contains the image dimensions measured during layout (output units = pt, after EXIF rotation).

```xml
<?xml version="1.0" encoding="UTF-8"?>
<image-metrics version="1" resolution="96">
  <image uri="https://example.com/figure.png" width="900" height="600"/>
</image-metrics>
```

Pass this to <span class="ioprop">input.image-metrics</span> for the next conversion to lay out passes
that need only dimensions (all but the final pass of multi-pass processing) without opening image resources.
For remote resources, this eliminates the retrieval round trips.

**The recorded values use output units, so they depend on <span class="ioprop">output.resolution</span>.**
The resolution used is recorded in the root element's `resolution` attribute. If it differs when read,
the entire dimension table is discarded and the images are measured again. This is safer than silently laying out with incorrect dimensions.

You can use this property with any output format, not just Paged SVG. If the data cannot be read or is malformed,
a warning is issued and layout falls back to measurement without stopping. Actual measurements
are more reliable, so XML values never overwrite them. `data:` images are not recorded because they require
no retrieval round trips and the URI itself contains the data.

URIs are recorded **exactly as requested**. In documents such as EPUBs, where internal resources refer to one another
by relative URI, the URIs remain relative. The dimension table therefore still works if you supply the same EPUB
from a different base (another directory or server).

Dimensions are recorded only in passes that need dimensions alone——all but the final pass of multi-pass processing.
As a result, `metrics.json` is emitted only when <span class="ioprop">processing.pass-count</span>
is 2 or more. Books with a table of contents or cross-references already use two or more passes,
so it is usually output without additional configuration.
