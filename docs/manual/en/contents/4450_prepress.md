## Preparing files for a print shop

This section brings together the settings needed to produce printed material as one procedure
from start to finish. All values given here have been verified through actual conversions.

### <a id="prepress-terms">Trim size, bleed, crop marks, and paper</a>

First, the terminology used by print shops corresponds to the terminology here as follows.

| Print shop term | Term used here | Setting |
| --- | --- | --- |
| Trim size (dimensions after trimming) | Print area | <span class="cssprop">size</span> (<tt>@page</tt>), <span class="ioprop">output.page-width</span> and <span class="ioprop">output.page-height</span> |
| Bleed (color extended beyond the trim position) | Content extended into the bleed area | <span class="cssprop">bleed</span> (<tt>@page</tt>), <span class="ioprop">output.htrim</span> and <span class="ioprop">output.vtrim</span> |
| Crop marks | Crop marks | <span class="cssprop">marks</span> (<tt>@page</tt>), <span class="ioprop">output.marks</span> |
| Paper | Paper | Automatically set to <b>print area + trim allowance</b> if not specified |

<div title="Page layout" class="figure">
	<object data="images/page-layout.svg" type="image/svg+xml"
		style="width: 120mm;" />
</div>

<div class="note">
<p>
<b>The most common mistake.</b>
Do not put dimensions that include bleed (216 mm × 303 mm for A4) in <span class="cssprop">size</span>.
<span class="cssprop">size</span> is treated as the <b>trim size</b>,
so crop marks would be placed at the bleed boundary, where the print shop would cut.
The correct approach is to <b>specify the trim size and add bleed with <span class="cssprop">bleed</span></b>.
</p>
</div>

### <a id="prepress-recipe">A4 with 3 mm bleed, crop marks, and CMYK</a>

This is the most commonly used setup. The following CSS is all you need.

```css
@page {
	size: 210mm 297mm;   /* Trim size. Do not add bleed. */
	bleed: 3mm;          /* Bleed. The paper becomes 216 mm × 303 mm. */
	marks: crop cross;   /* Corner crop marks and center registration marks */
	margin: 15mm;        /* Margins around the type area */
}
```

There are three I/O properties.

| Property | Value | Meaning |
| --- | --- | --- |
| <span class="ioprop">output.color</span> | <tt>cmyk</tt> | Converts all colors to CMYK |
| <span class="ioprop">output.pdf.version</span> | <tt>1.4</tt>, for example | Match the print shop's requirements |
| <span class="ioprop">output.pdf.output-intent.icc-profile</span> | URI of an ICC profile | The profile the print shop specifies (see [Color conversion and output intent](#prepress-color) below) |

This setup produces paper measuring <b>230 mm × 317 mm</b>. Outside the trim size (210 × 297 mm),
there is a 1 cm trim allowance. The inner 3 mm is the bleed area, and crop marks are drawn in the remainder.
If you need bleed but no crop marks (omit <span class="cssprop">marks</span>),
the paper is <b>216 mm × 303 mm</b> (trim size + bleed only).

For existing content whose CSS you cannot modify, I/O properties can achieve the same result instead of <tt>@page</tt>
(<span class="ioprop">output.marks</span>=<tt>both</tt>,
<span class="ioprop">output.htrim</span> and <span class="ioprop">output.vtrim</span>=<tt>3mm</tt>).
When <span class="cssprop">bleed</span> is <tt>auto</tt> (the default), these I/O properties are used.

### <a id="prepress-color">Color conversion and output intent<span class="since">4.0.0</span></a>

With <span class="ioprop">output.color</span>=<tt>cmyk</tt> and PDF/X-1a (<tt>1.4X-1</tt>),
RGB colors are converted to CMYK **using the output intent's ICC profile** (a change from the formula used in 3.x).
Provide the profile specified by your print shop (Japan Color 2001 Coated is common in Japan)
with <span class="ioprop">output.pdf.output-intent.icc-profile</span>.
If you do not specify one, the bundled ISO Coated v2 300% (ECI) profile is used. For details, see
[Output intent](#style-pdf-output-intent) and [RGB to CMYK conversion](#style-pdf-cmyk-conversion).

Keep the following in mind:

- Black in text and rules (R=G=B) uses K only. **Black in blurs (<span class="cssprop">box-shadow</span> and
  <span class="cssprop">filter</span>) or SVG effects uses all four colors (rich black)**.
  Avoid blurs if your print shop does not want this.
- Photos supplied in CMYK (four-component JPEGs) are embedded as they are, without conversion.
- If your print shop accepts RGB, use <tt>1.6X-4</tt> without <span class="ioprop">output.color</span>.
  RGB is preserved with an ICC profile (sRGB), and the print shop performs the conversion.

### <a id="prepress-bleed">Extending backgrounds into the bleed area</a>

**Bleed means drawing content beyond the trim line, not simply making the paper larger.**
Specifying <span class="cssprop">bleed</span> allows drawing beyond the trim line by
that width<span class="since">4.0.0</span>. You then need to <b>extend the content beyond the trim line yourself</b>.

Absolute positioning is relative to the <b>type area</b> (inside the <tt>@page</tt> margins),
so you must also offset the margins. For 15 mm margins and 3 mm bleed, the offset is <tt>-18mm</tt>.

```css
@page { size: 210mm 297mm; bleed: 3mm; marks: crop cross; margin: 15mm; }
body { margin: 0 }

/* A band across the top. Extend the left, right, and top into the bleed area. */
.fullbleed-top {
	position: absolute;
	left: -18mm;         /* 15 mm margin + 3 mm bleed */
	top: -18mm;
	width: 216mm;        /* 210mm + 3mm + 3mm */
	height: 53mm;        /* Desired visible height + 3 mm */
	background: #003366;
}
```

Measurements (A4 with 3 mm bleed and crop marks, paper size 230 mm × 317 mm; measured from pixels):

| Position | Measurement |
| --- | --- |
| Trim line | 10.0 mm from the paper edge |
| Left edge of the band | <b>7.15 mm</b> (2.85 mm outside the trim line = bleed) |
| Right edge of the band | <b>222.85 mm</b> (also 2.85 mm outside) |
| Crop marks | Drawn in the white band beyond the bleed area |

A background specified on <tt>@page</tt> fills the <b>entire trim size</b>, extending to the edge of the paper
rather than just inside the margins, but <b>does not reach the bleed area</b>.
Backgrounds on <tt>body</tt> or <tt>html</tt> extend <b>only to the type area</b> (as in browser printing).
To make an element reach the bleed area, you must <b>extend it beyond the trim line yourself</b>, as shown above.

<div class="note">
<p>
Set <span class="ioprop">output.clip</span> to <tt>false</tt> only if you want to draw content
beyond the crop marks, all the way to the paper edge.
The default (<tt>true</tt>) clips at the <b>trim line + bleed</b>.
</p>
</div>

### <a id="prepress-existing">Existing content that already includes bleed</a>

Content created earlier or with other tools may have been laid out
<b>at dimensions that include bleed</b>. For example, a page intended for an A4 trim size may measure 216 mm × 303 mm,
with the outer 3 mm serving as bleed.

You do not need to rewrite the CSS in this case.
Set <span class="ioprop">output.trim-inset</span> to the <b>width of the outer band</b>
<span class="since">4.0.0</span>. The trim line is treated as being that far inside the outer edge
of the print area, and crop marks are drawn there.

```
output.trim-inset: 3mm
output.marks: crop cross
output.color: cmyk
```

| | Not specified | <tt>output.trim-inset: 3mm</tt> |
| --- | --- | --- |
| Trim size | 216 mm × 303 mm (still includes bleed) | <b>210 mm × 297 mm</b> |
| Crop mark position | Outer edge of the print area | 3 mm inside the print area |
| Bleed | None (the outer edge is the trim line) | The outer 3 mm |
| Paper (without crop marks) | 216 mm × 303 mm | <b>210 mm × 297 mm</b> (bleed is trimmed off) |

When you omit crop marks (<span class="ioprop">output.marks</span> is <tt>none</tt>),
the paper matches the trim size exactly, and the bleed is clipped off.
This shows <b>the appearance after trimming</b> directly and can be used for proofing.

<div class="note">
<p>
When you specify <span class="ioprop">output.trim-inset</span>,
the CSS <span class="cssprop">bleed</span> property is ignored. The bleed content is
already inside the print area, so applying both would count it twice.
</p>
</div>

### <a id="prepress-check">Checks before submission</a>

You can check the following three points mechanically in the completed PDF.

<dl>

<dt>Paper dimensions</dt>
<dd>
	Check that the PDF's <tt>MediaBox</tt> is "trim size + trim allowance × 2."
	For A4 with 3 mm bleed and crop marks, it is <tt>[0 0 651.97 898.58]</tt> (230 mm × 317 mm);
	without crop marks, it is <tt>[0 0 612.28 858.90]</tt> (216 mm × 303 mm).
</dd>

<dt>Color</dt>
<dd>
	In the content stream of a PDF output with <span class="ioprop">output.color</span>=<tt>cmyk</tt>,
	the RGB <tt>rg</tt> operator does not appear;
	only the CMYK <tt>k</tt> operator appears. Images also use <tt>/DeviceCMYK</tt>.
	Use an external checker, such as Acrobat Pro Preflight, for the final check of
	PDF/X (<tt>1.4X-1</tt> and <tt>1.6X-4</tt>) conformance.
</dd>

<dt>Bleed</dt>
<dd>
	Output the same document as an image (<span class="ioprop">output.type</span>=<tt>image/png</tt>)
	and check that the pixels <b>immediately outside the trim line</b> are not white.
	If they are white, the bleed content does not extend far enough.
</dd>

</dl>

### <a id="prepress-imposition">Imposition</a>

For imposition, which arranges multiple pages on one sheet of paper,
see <a href="#style-imposition" class="pageref">Imposition</a>.
You can also add crop marks to imposed sheets.
