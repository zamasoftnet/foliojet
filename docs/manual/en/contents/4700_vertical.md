## <a id="style-vertical">Vertical writing</a>

Vertical writing is supported through `writing-mode` from CSS Writing Modes Level 3
<span class="since">3.0.0</span>. The implementation covers
horizontal-tb, vertical-rl, vertical-lr, and tate-chu-yoko, as described in this chapter.

### writing-mode

Specify the writing mode with <span class="cssprop">writing-mode</span> from CSS Writing Modes Level 3. The older names (<span class="cssprop">-cssj-writing-mode</span> and <span class="cssprop">-epub-writing-mode</span>) are also available, but **use the standard name in new documents**. The specification is as follows.

<dl>

<dt>Value</dt>
<dd>horizontal-tb | vertical-rl | vertical-lr<span class="since">4.0.0</span> | lr | lr-tb | rl | rl-tb | tb | tb-rl | tb-lr</dd>
<dt>Initial value</dt>
<dd>horizontal-tb</dd>
<dt>Applies to</dt>
<dd>All elements except table row groups, table column groups, table rows, and table columns</dd>
<dt>Inherited</dt>
<dd>Yes</dd>

</dl>

For an element, horizontal-tb applies horizontal writing, and vertical-rl applies vertical writing with lines progressing from right to left
(the usual Japanese vertical writing mode). vertical-lr applies **vertical writing with lines progressing from left to right**
<span class="since">4.0.0</span>.

For compatibility with SVG and Internet Explorer, the values
lr, lr-tb, and rl have the same meaning as horizontal-tb.
Similarly, tb and tb-rl mean vertical-rl, while rl-tb and tb-lr mean vertical-lr.

<div class="note">

Use vertical-lr for **rotated labels**, rather than Japanese vertical writing.
Examples include rotated table column headings to save width, chart axis labels, book spines, and side tabs.
To arrange text from bottom to top, the usual approach is to combine vertical-lr with
<span class="cssdecl">transform: rotate(180deg);</span>.

`text-orientation` supports mixed (the default), upright, and sideways. mixed keeps
Japanese text upright and turns Latin text and other characters sideways according to character type. upright keeps all characters
in the range upright, and sideways turns them all sideways. To fit a short number or similar content into one character cell,
use `text-combine-upright: all`.

```css
.mixed { text-orientation: mixed; }
.upright { text-orientation: upright; }
.sideways { text-orientation: sideways; }
.tcy { text-combine-upright: all; }
```

`writing-mode: sideways-rl / sideways-lr` is not supported. Typesetting traditional Mongolian script,
which requires positional glyph shaping and joining, is also outside the implementation scope.

</div>

<span class="notice">You can specify <span class="cssprop">writing-mode</span> on table cells (td, th), but
	changing the writing mode of a table cell is not recommended.
	Page breaks are always disabled within a table cell whose writing mode has been changed.
	Instead, for example, nest a div tag with <span class="cssprop">writing-mode</span> set inside the table cell.
</span>

### Document writing mode

The writing mode of the entire document is determined by <span class="cssprop">writing-mode</span> on the document element (the root element in XML, or the BODY element in HTML).

The writing mode of the entire document affects its binding direction. Vertical writing with vertical-rl and horizontal writing with <span class="cssprop">direction</span> rtl use right binding; everything else (horizontal writing with ltr, and vertical-lr) uses left binding (left-side or right-side in <span class="ioprop">output.print-mode</span> can also fix the binding). Forced page breaks with left or right on <span class="cssprop">page-break-before</span>, <span class="cssprop">page-break-after</span>
take the binding direction into account.

In horizontal writing, a page break occurs where content overflows the bottom. In vertical writing, it occurs where content overflows the left edge.

In Japanese vertical writing, especially in multi-column layout, setting <span class="cssprop">orphans</span> and <span class="cssprop">widows</span>, which control isolated paragraph lines, to 1 helps avoid unnatural gaps at the end of a page or column. For details, see <a href="#style-pagebreak-widows-orphans" class="pageref">orphans and widows</a>.

```html
<html>
<head>
  <style type="text/css">
    body {
      writing-mode: vertical-rl;
    }
  </style>
</head>
<body>
<p>
石炭をば早や積み果てつ。
中等室の卓のほとりはいと靜にて、熾熱燈の光の晴れがましきも徒なり。
今宵は夜毎にこゝに集ひ來る骨牌仲間も「ホテル」に宿りて、舟に殘れるは余一人のみなれば。
</p>
</body>
</html>
```

<div class="figure" title="Vertical writing for the entire document (rendered output)">
	<img src="images/vertical-1.png" style="width: 60mm;"
		alt="Vertical writing for the entire document (rendered output)" />
</div>

### Mixing writing modes

When an element with vertical writing appears within horizontal writing, or an element with horizontal writing appears within vertical writing,
the content is processed as mixed writing modes. Page breaks cannot occur inside an element whose writing mode differs from the document's.

When a block has a different writing mode and its dimension in the page progression direction (height for a parent with horizontal writing, width for a parent with vertical writing) is auto,
that dimension becomes the page height. A percentage is resolved against the parent's dimension in the inline direction (width for horizontal writing, height for vertical writing).
However, placing a block with a different writing mode and an auto dimension in the page progression direction is not recommended.
Its dimension in the inline direction (width for a parent with horizontal writing, height for a parent with vertical writing) is calculated as usual.
As a result, long content overflows in the parent's inline direction. Alternatively, use <a href="#style-columns" class="pageref">multi-column layout</a>.

<div class="note">

A block with a different writing mode is **treated as a single unit without splitting**.
If its content does not fit on the page, it overflows in the inline direction without a page break within it.
This behavior is by design. To avoid overflow, adjust the amount of content,
specify an explicit dimension in the page progression direction, or use multi-column layout.

</div>

```html
<html>
<head>
  <style type="text/css">
    body {
      border: 1pt dashed Red;
    }
    #a {
      writing-mode: vertical-rl;
      height: 6em;
      border: 1pt solid Red;
    }
  </style>
</head>
<body>
<p>
石炭をば早や積み果てつ。
中等室の卓のほとりはいと靜にて、熾熱燈の光の晴れがましきも徒なり。
今宵は夜毎にこゝに集ひ來る骨牌仲間も「ホテル」に宿りて、舟に殘れるは余一人のみなれば。
</p>
<div id="a">
五年前の事なりしが、平生の望足りて、洋行の官命を蒙り、このセイゴンの港まで來し頃は、目に見るもの、耳に聞くもの、一つとして新ならぬはなく、筆に任せて書き記しつる紀行文日ごとに幾千言をかなしけむ、當時の新聞に載せられて、世の人にもてはやされしかど、今日になりておもへば、穉き思想、身の程知らぬ放言、さらぬも尋常の動植金石、さては風俗などをさへ珍しげにしるしゝを、心ある人はいかにか見けむ。
</div>
</body>
</html>
```

<div class="figure" title="Vertical writing within a horizontal document (rendered output)">
	<img src="images/vertical-2.png" style="width: 60mm;"
		alt="Vertical writing within a horizontal document (rendered output)" />
</div>

An inline element with a different writing mode is placed as an inline block. You can use this for tate-chu-yoko.

```html
<html>
<head>
  <style type="text/css">
    body {
      writing-mode: vertical-rl;
    }
    .tcy {
      writing-mode: horizontal-tb;
    }
  </style>
</head>
<body>
<p>
<span class="tcy">2010</span>年<span class="tcy">7-9</span>月期の実質<span class="tcy">GDP</span>（国内総生産）の成長率は、
民間最終消費支出がプラスに寄与したことなどから、
前期比で<span class="tcy">0.9</span>％増（年率<span class="tcy">3.9</span>％増）となった（４四半期連続のプラス）。
また、名目<span class="tcy">GDP</span>成長率は前期比で <span class="tcy">0.7</span>％増となった（２四半期ぶりのプラス）。 
</p>
</body>
</html>
```

<div class="figure" title="Tate-chu-yoko (rendered output)">
	<img src="images/vertical-3.png" style="width: 60mm;" alt="Tate-chu-yoko (rendered output)" />
</div>

With the approach above (<span class="cssdecl">writing-mode: horizontal-tb;</span>),
<span class="cssdecl">vertical-align: central;</span> (the central baseline in CSS Inline Layout 3) is also
accepted as the same alignment as <tt>middle</tt><span class="since">4.0.0</span>. This approach
uses the **natural width** of the horizontally arranged text.
A large number of digits overflows the line width. To prevent overflow,
use <span class="cssprop">text-combine-upright</span> with <tt>all</tt><span class="since">4.0.0</span>.
This **fits the laid-out content into the width of one character (1 em)**. For three or more digits (two digits use half-width forms),
it first uses the font's compressed-width glyphs (OpenType <tt>hwid</tt> / <tt>twid</tt> / <tt>qwid</tt>),
and scales the content horizontally only when the font lacks these glyphs<span class="since">4.0.0</span>.
(Even four digits, such as <tt>2016</tt>, do not overflow the line.)
After scaling, the actual glyph bounds (ink) are centered within 1 em, so page numbers arranged vertically
do not shift from side to side because of differences in the digits' left and right side bearings.

```css
.tcy {
	text-combine-upright: all;
}
```

For <span class="cssprop">-cssj-text-combine</span> and <span class="cssprop">-epub-text-combine</span>,
<tt>horizontal</tt> retains the natural width as before (without scaling).
Both work only within vertical writing.

### Dashes

In vertical writing, U+2015 HORIZONTAL BAR (<tt>―</tt>) and U+2014 EM DASH
(<tt>—</tt>) are both oriented vertically. If the font lacks a vertical glyph for U+2014,
the vertical glyph for U+2015 is substituted. For consecutive dashes such as <tt>――</tt>, the space between the glyph bounds
is automatically removed to join them into a single line, so you do not need to specify a negative <span class="cssprop">letter-spacing</span>
value<span class="since">4.0.0</span>.

### <a id="style-bidi">Languages written from right to left<span class="since">4.0.0</span></a>

**Languages written from right to left**, such as Arabic and Hebrew, are supported.
In lines that mix these languages with languages written from left to right,
the content is **reordered into visual order** according to the Unicode Bidirectional Algorithm (UAX #9).

Output for documents containing only horizontal writing from left to right is unchanged.

#### Specifying the direction

The HTML <tt>dir</tt> attribute is the simplest method.

```html
<p dir="rtl">مرحبا بالعالم</p>
```

In CSS, use <span class="cssprop">direction</span> and
<span class="cssprop">unicode-bidi</span>.

<dl>

<dt>direction</dt>
<dd>Specify ltr (left to right, the initial value) or rtl.
	This determines the base direction of a line and which sides are the line start and line end.</dd>
<dt>unicode-bidi</dt>
<dd>Specify normal (the initial value), embed, bidi-override, isolate, isolate-override, or plaintext<span class="since">4.0.0</span>.
	embed treats the range as an embedding with its own direction,
	while bidi-override ignores the properties of the characters within the range
	and forces them into the order specified by <span class="cssprop">direction</span>.
	isolate and isolate-override order the range in the same way as embed and bidi-override respectively,
	but do not affect the order of the characters outside the range.
	plaintext determines the direction from the first strong character in the range.</dd>

</dl>

The HTML bdo element is equivalent to
<span class="cssdecl">unicode-bidi: isolate-override;</span>. The dir attribute is treated as
<span class="cssdecl">unicode-bidi: isolate;</span>, and dir="auto" and the bdi element as
<span class="cssdecl">unicode-bidi: plaintext;</span>.

#### Switching styles by direction

The `:dir()` pseudo-class lets you change styles according to an element's direction.

```css
p:dir(rtl) {
	text-align: right;
}
```

The direction is inherited from the <tt>dir</tt> attribute through the document tree.
<tt>dir="auto"</tt> (which determines the direction from the first character with strong directionality in the content)
is not supported; the inherited value is used.

<div class="note">

Only reordering within a line is performed.
Arabic **glyph joining** (positional glyph shaping) is not supported.

</div>

### <a id="style-logical-props">Logical properties</a>

<span class="cssprop">margin-top</span>, <span class="cssprop">padding-left</span>
and other properties that refer to a direction apply to **physical directions** regardless of the writing mode
(`*-top` always means the top, and `*-left` always means the left).
If you apply a stylesheet written for horizontal writing directly to vertical writing,
heading borders and image margins therefore appear on unintended sides.

To avoid this, you can use **logical properties**, which refer to sides relative to the writing mode.
Logical properties follow the writing mode, so you can use the same declarations for both horizontal and vertical writing.

<dl>

<dt>block (block direction)</dt>
<dd>The direction in which lines stack. It runs from top to bottom in horizontal writing and
	from right to left in vertical writing (vertical-rl). <span class="cssprop">*-block-start</span> is the start,
	and <span class="cssprop">*-block-end</span> is the end.</dd>
<dt>inline (inline direction)</dt>
<dd>The direction in which a line runs. It runs from left to right in horizontal writing and from top to bottom in vertical writing.
	<span class="cssprop">*-inline-start</span> is the line start,
	and <span class="cssprop">*-inline-end</span> is the line end.</dd>

</dl>

The following logical properties are available<span class="since">4.0.0</span>.

| Category | Properties |
| --- | --- |
| Margins | margin-block-start, margin-block-end, margin-inline-start, margin-inline-end |
| Padding | padding-block-start, padding-block-end, padding-inline-start, padding-inline-end |
| Size | inline-size, block-size |
| Minimum and maximum | min-inline-size, max-inline-size, min-block-size, max-block-size |
| Position | inset-block-start, inset-block-end, inset-inline-start, inset-inline-end |

<div class="note">

Logical border properties (such as <span class="cssprop">border-block-start</span>, <span class="cssprop">border-inline</span>, and
<span class="cssprop">border-inline-start-width</span>: the shorthands and the individual width, style, and color properties) are also supported.

</div>

When a physical property and a logical property refer to the same side, **the one that comes later in the cascade
is used** (as CSS Logical Properties 1 specifies<span class="since">4.0.0</span>). They are compared by `!important`,
specificity and order of appearance, the same as any other overlapping declarations. For example, after
`p { margin: 0 }`, common in EPUB style sheets, a more specific `p.x { margin-block-start: 3em }` takes effect.
Written in one rule as `margin-top: 0; margin-block-start: 3em`, the later `margin-block-start` is used. Sizes (such as
<span class="cssprop">width</span> and <span class="cssprop">inline-size</span>) and border widths, styles and colors
work the same way.

<span class="cssprop">float</span>, <span class="cssprop">clear</span>,
<span class="cssprop">text-align</span>, <span class="cssprop">caption-side</span>
already follow the writing mode.
The value left is always treated as the line start, right as the line end,
top as the start of the page progression direction, and bottom as its end.

<span class="cssprop">page-break-before</span>, <span class="cssprop">page-break-after</span>
treat left and right as even-numbered pages (verso) and odd-numbered pages (recto), respectively.
In a right-bound document (such as one that uses vertical-rl throughout), the left and right sides are therefore reversed.

### Changing a horizontal document to vertical writing

Suppose you have the following document with horizontal writing.

```html
<html>
<head>
  <style type="text/css">
    p {
      text-indent: 1em;
      text-align: justify;
    }
  </style>
</head>
<body>
<h1 style="border-bottom: 2pt dashed">かっぱ</h1>
<img src="kappa.png" width="148" height="199" alt="Kappa" align="left"/>
<p>
河童（かっぱ）は、日本の妖怪・伝説上の動物、または未確認動物。標準和名の「かっぱ」は、「かわ（川）」に「わらは（童）」の変化形「わっぱ」が複合した「かわわっぱ」が変化したもの。河太郎（かわたろう）とも言う。ほぼ日本全国で伝承され、その呼び名や形状も各地方によって異なる。
</p>
</body>
</html>
```

<div class="figure" title="Document with horizontal writing (rendered output)">
	<img src="images/vertical-4.png" style="width: 60mm;"
		alt="Document with horizontal writing (rendered output)" />
</div>

Applying <span class="cssdecl">writing-mode: vertical-rl;</span> to the body of this document
changes it to vertical writing, but leaves the directions of the heading border and image margins unchanged.

<div class="figure" title="Changed to vertical writing (rendered output)">
	<img src="images/vertical-5.png" style="width: 60mm;"
		alt="Changed to vertical writing (rendered output)" />
</div>

To make borders and margins follow the writing mode as well, specify them with logical properties.

```css
img {
	/* Right margin in horizontal writing, bottom margin in vertical writing */
	margin-inline-end: 1em;
}
```

<div class="figure" title="Changed to vertical writing with logical properties (rendered output)">
	<img src="images/vertical-6.png" style="width: 60mm;"
		alt="Changed to vertical writing with logical properties (rendered output)" />
</div>
