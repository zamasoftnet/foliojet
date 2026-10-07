## <a id="style-barcode">Barcodes and QR codes</a>

You can draw barcodes and QR codes simply by adding elements to your document.
You do not need to prepare external images. Like text, the lines are drawn as vectors,
so they remain sharp when enlarged and readable when printed.

Barcodes are generated with [OkapiBarcode](https://github.com/woo-j/OkapiBarcode).

<div class="note">
In version 4.0.0, OkapiBarcode replaced Barcode4J and
the "QR Code Class Library for Java" as the generation engine.
The element syntax remains compatible, but <strong>some parameters no longer have any effect</strong>.
See <a href="#style-barcode-ignored" class="pageref">Ignored parameters</a>.
</div>

### <a id="style-barcode-write">Syntax</a>

A `barcode` element in the `http://barcode4j.krysalis.org/ns` namespace
is replaced with a barcode image. Put the string to encode in the `message` attribute.

Place one **element specifying the barcode type** directly inside `barcode`,
and place the parameters directly inside that element.

```xml
<bc:barcode xmlns:bc="http://barcode4j.krysalis.org/ns"
 message="200123456789">
  <bc:ean-13>
    <bc:height>15</bc:height>
    <bc:module-width>0.33</bc:module-width>
  </bc:ean-13>
</bc:barcode>
```

<div class="figure" title="Barcode (rendered result)">
<bc:barcode xmlns:bc="http://barcode4j.krysalis.org/ns"
 message="200123456789">
  <bc:ean-13>
    <bc:height>15</bc:height>
    <bc:module-width>0.33</bc:module-width>
  </bc:ean-13>
</bc:barcode>
</div>

The image is treated as an inline image. You can adjust its size with CSS properties such as `width` / `height`,
or align it within the line with `vertical-align`.

### <a id="style-barcode-types">Supported types</a>

The element names for each barcode type are listed below.
Names on the same row produce the same result.
Case and hyphens are ignored, so `ean-13` and `EAN13` are equivalent.

| Element name | Symbology |
| --- | --- |
| `bc:code128` | Code 128. **Also used when no type is specified** |
| `bc:code39` `bc:code3of9` | Code 39 |
| `bc:codabar` | Codabar(NW-7). If `message` has no start/stop characters (A through D), A is added automatically<span class="since">4.0.0</span> |
| `bc:interleaved2of5` `bc:intl2of5` `bc:int2of5` `bc:itf` | Interleaved 2 of 5(ITF) |
| `bc:ean-13` | EAN-13(JAN-13) for general merchandise<span class="since">4.0.0</span> |
| `bc:isbn` | EAN-13 for Japanese book JAN barcodes. Drawn with bars of equal height and a continuous 13-digit OCR line. **You can supply all 13 digits, including the check digit**; the check digit is then recalculated according to the standard<span class="since">4.0.0</span> |
| `bc:ean-8` | EAN-8(JAN-8)<span class="since">4.0.0</span> |
| `bc:ean` | EAN. Uses EAN-8 if `message` has 8 digits or fewer, and EAN-13 otherwise |
| `bc:upc-a` | UPC-A<span class="since">4.0.0</span> |
| `bc:upc-e` | UPC-E<span class="since">4.0.0</span> |
| `bc:ean-128` `bc:gs1-128` | **Drawn as plain Code 128**. GS1 AI syntax (FNC1) is not supported |
| `bc:postnet` | POSTNET<span class="since">4.0.0</span> |
| `bc:planet` | PLANET<span class="since">4.0.0</span> |
| `bc:royal-mail-cbc` `bc:royalmail` `bc:rm4scc` | Royal Mail 4-State(CBC)<span class="since">4.0.0</span> |
| `bc:usps4cbc` `bc:usps4cb` `bc:uspsonecode` `bc:uspsintelligentmail` | USPS Intelligent Mail. `message` can be a numeric string combining the tracking code and routing code (20, 25, 29, or 31 digits)<span class="since">4.0.0</span> |
| `bc:japanpost` `bc:jp4scc` | Japan Post customer barcode |
| `bc:qrcode` `bc:qr` | QR code |
| `bc:datamatrix` | Data Matrix |
| `bc:pdf417` | PDF417 |
| `bc:aztec` `bc:azteccode` | Aztec Code |

**An unknown name does not cause an error.** The barcode is drawn as Code 128.
Be careful with spelling: a typo silently produces a different symbology than intended.

### <a id="style-barcode-params">Supported parameters</a>

Place parameters directly inside the element specifying the barcode type. Only the following six are read.

| Element name | Meaning |
| --- | --- |
| `bc:module-width` | Width of the smallest unit (one bar or one cell) |
| `bc:height` `bc:bar-height` | Bar height. For one-dimensional barcodes only |
| `bc:quiet-zone` `bc:quiet-zone-horizontal` | Left and right quiet zones. Also applies to the top and bottom for two-dimensional symbologies such as QR codes <span class="since">4.0.0</span> |
| `bc:quiet-zone-vertical` | Top and bottom quiet zones. Takes precedence when specified |
| `bc:human-readable` `bc:human-readable-placement` | Position of the displayed digits. `top` `bottom` `none` (`hidden` is also accepted) |
| `bc:font-name` `bc:font-size` | Font and size used to draw the digits |

#### <a id="style-barcode-unit">Units for numeric values</a>

You can include units in length values<span class="since">4.0.0</span>.
The supported units are `mm` (also used when no unit is specified), `cm`, `in`, `pt`, and `px`.

```xml
<bc:module-width>0.21mm</bc:module-width>
<bc:module-width>0.02in</bc:module-width>
<bc:height>15mm</bc:height>
```

For quiet zones (`bc:quiet-zone` `bc:quiet-zone-vertical`), you can also use
`mw` (multiples of the module width). `10mw` is ten times the module width.

```xml
<bc:quiet-zone>10mw</bc:quiet-zone>
```

<div class="note">
For QR codes, Data Matrix, Aztec Code, and PDF417, <code>bc:quiet-zone</code>
<strong>applies to all four sides</strong><span class="since">4.0.0</span>. These standards
require equal quiet zones on all four sides. To change only the top and bottom,
specify <code>bc:quiet-zone-vertical</code>.
</div>

A `bc:font-size` value without a unit is interpreted as points (pt).

### <a id="style-barcode-book-jan">Book JAN barcodes</a>

For the two-tier barcodes on books distributed in Japan, use `bc:isbn`
instead of `bc:ean-13`, which is for general merchandise. `bc:isbn` uses EAN-13 encoding, but its book-specific rendering
does not extend the guard bars and places all 13 digits below the bars in one continuous group.

```xml
<bc:barcode xmlns:bc="http://barcode4j.krysalis.org/ns"
 message="9784908348143" style="height:11mm">
  <bc:isbn>
    <bc:height>7.6mm</bc:height>
    <bc:module-width>0.33mm</bc:module-width>
    <bc:quiet-zone>0mm</bc:quiet-zone>
    <bc:human-readable>
      <bc:placement>bottom</bc:placement>
      <bc:font-name>OCRB</bc:font-name>
      <bc:font-size>7.5pt</bc:font-size>
    </bc:human-readable>
  </bc:isbn>
</bc:barcode>
```

At the standard scale of 100%, the bars occupy a width of 95 modules × 0.33 mm = 31.35 mm.
The total height of one tier, including the human-readable text, is 11 mm. These dimensions are specific to Japanese book JAN barcodes
and differ from the standard total height of 25.93 mm for general-merchandise EAN-13.
The 13 digits are distributed evenly across the full bar width of 31.35 mm, based on the actual glyph widths of the specified OCR-B font.
Place the ISBN in the upper tier and the 13 digits for classification and the price excluding tax in the lower tier, using the same format. Follow the distribution rules for the white background, quiet zones,
spacing between tiers, and placement on the back cover as well. Use OCR-B for the digits below the bars,
and a legible font other than OCR-B for the separate `ISBN...` and `C...` text to the right.
For details, see the [Japan ISBN Agency, "Guide to Using ISBN Codes, Japanese Book Codes, and Book JAN Barcodes, 2025 Edition"](https://isbn.jpo.or.jp/doc/ISBN_jp2025.pdf).

In version 4.0.0 and later, `bc:height` specifies only the height of the bars. In version 3,
it specified the total height including human-readable text, so the same value produces a different vertical size.
When migrating settings from version 3.2, replace them with the 11 mm example above.

### <a id="style-barcode-ignored">Ignored parameters</a>

The following elements **have no effect**, even if specified. No warning is issued.
They appear in documents and samples from the Barcode4J era; remove them if they remain.

| Element name | Former meaning | Current behavior |
| --- | --- | --- |
| `bc:checksum` | Method of adding a check digit | Calculated automatically according to the standard for each symbology |
| `bc:ecc` | QR code error correction level | Determined automatically by the amount of content |
| `bc:encmode` | QR code encoding mode | Determined automatically from the content |
| `bc:version` | QR code version | Determined automatically by the amount of content |

#### Nested syntax is also supported

The nested syntax from the Barcode4J era is also read as intended without modification
<span class="since">4.0.0</span>.

```xml
<bc:human-readable>
  <bc:placement>bottom</bc:placement>
  <bc:font-size>8pt</bc:font-size>
</bc:human-readable>
```

Nested elements (such as `bc:placement` and `bc:font-size`)
are treated as the same parameters as in the flat syntax.

### <a id="style-barcode-qr">QR codes</a>

The QR code version, error correction level, and encoding mode are determined automatically from the content.
You can specify only the cell size and quiet zones.

```xml
<bc:barcode xmlns:bc="http://barcode4j.krysalis.org/ns"
 message="https://copper-pdf.com/">
  <bc:qrcode>
    <bc:module-width>1</bc:module-width>
    <bc:quiet-zone>2</bc:quiet-zone>
  </bc:qrcode>
</bc:barcode>
```

<div class="figure" title="QR code (rendered result)">
<bc:barcode xmlns:bc="http://barcode4j.krysalis.org/ns"
 message="https://copper-pdf.com/">
  <bc:qrcode>
    <bc:module-width>1</bc:module-width>
    <bc:quiet-zone>2</bc:quiet-zone>
  </bc:qrcode>
</bc:barcode>
</div>

For details on QR codes, see the DENSO WAVE INCORPORATED website
[https://www.qrcode.com/](https://www.qrcode.com/).

### <a id="style-barcode-post">Japan Post customer barcodes</a>

Draws a barcode for sorting mail from the postal code and address number.

```xml
<bc:barcode xmlns:bc="http://barcode4j.krysalis.org/ns"
 message="1008798 1-3-2">
  <bc:japanpost>
    <bc:module-width>0.6</bc:module-width>
  </bc:japanpost>
</bc:barcode>
```

<div class="figure" title="Japan Post customer barcode (rendered result)">
<bc:barcode xmlns:bc="http://barcode4j.krysalis.org/ns"
 message="1008798 1-3-2">
  <bc:japanpost>
    <bc:module-width>0.6</bc:module-width>
  </bc:japanpost>
</bc:barcode>
</div>

Characters other than digits, letters, and hyphens are removed from `message`.
You can insert spaces for readability, as in the example above.

The default bar width is 0.6, and the recommended range is 0.48 to 0.69.
See Japan Post's barcode manual at
[https://www.post.japanpost.jp/zipcode/zipmanual/](https://www.post.japanpost.jp/zipcode/zipmanual/)
for the specification.

### <a id="style-barcode-error">When a barcode cannot be read</a>

An unreadable printed barcode is usually caused by one of the following.

<dl>

<dt>The module width is too small</dt>
<dd>
	Increase `bc:module-width`. As a guide, use at least 0.33 for one-dimensional barcodes
	and at least 0.5 for QR codes. Bars finer than the printer resolution
	will not print correctly.
</dd>

<dt>The quiet zone is too small</dt>
<dd>
	A clear blank area is required around the symbol. Increase `bc:quiet-zone`,
	or add space around it with CSS `margin`. A background color or rule touching the symbol
	can make it unreadable.
</dd>

<dt>The barcode has been scaled</dt>
<dd>
	Transforming the image with CSS `width` / `height` distorts the bar proportions.
	Set the size with `bc:module-width` and `bc:height`.
</dd>

<dt>The `message` content does not conform to the symbology standard</dt>
<dd>
	Symbologies have restrictions, such as 13 or 8 digits for EAN, or uppercase letters and digits for Code 39.
	If you supply content that does not conform to the standard,
	an error message is drawn in the image instead of a barcode.
</dd>

</dl>
