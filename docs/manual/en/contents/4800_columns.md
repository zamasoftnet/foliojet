## <a id="style-columns">Multi-column layout</a>

Multi-column layout is supported<span class="since">3.0.0</span>.
The implementation follows CSS Multi-column Layout Module Level 1.

**Use the standard property names (such as <span class="cssprop">column-count</span>)
when specifying properties.** Names prefixed with `-cssj-` are also accepted for compatibility,
but use the standard names in new documents.

If the text is small relative to the printed area, lines can become very wide and difficult to read.
Multi-column layout improves readability in this case. It also reduces empty space, saving paper.

In multi-column layout, the width in the line direction is reduced according to the number of columns. Percentage sizes for images and boxes are relative to the column width.

You can place headings and similar content across all columns.

```html
<html>
<head>
  <style type="text/css">
    div {
      column-count: 2;
      column-gap: 2em;
      column-rule: 1pt solid;
    }
    h1 {
      border-bottom: 2pt dashed;
      column-span: all;
    }
    img {
      float: left;
      width: 50%;
    }
    p {
      text-indent: 1em;
      text-align: justify;
      margin: 0;
    }
  </style>
</head>
<body>
<div>
<p>
Yōkai are strange, abnormal phenomena beyond human understanding in Japanese folk beliefs, or extraordinary beings with mysterious powers that cause such phenomena. They are also called ayakashi, mononoke, or mamono.
</p>
<h1>Kappa</h1>
<img src="kappa.png" alt="Kappa" />
<p>
Kappa are Japanese yōkai, legendary animals, or cryptids. The standard Japanese name "kappa" evolved from "kawawappa," a compound of "kawa" (river) and "wappa," a variant of "waraha" (child). They are also called kawatarō. They appear in folklore throughout almost all of Japan, with names and forms that vary by region.
</p>
</div>
</body>
</html>
```

<div class="figure" title="Two-column layout (rendered result)">
<object data="images/style-columns-1.svgz" type="image/svg+xml"
	style="width: 160mm;"></object>
</div>

The properties for multi-column layout are described below.

### column-count

<dl>

<dt>Value</dt>
<dd>auto | an integer of 1 or greater</dd>
<dt>Initial value</dt>
<dd>auto</dd>
<dt>Applies to</dt>
<dd>Non-replaced block-level elements (except tables), table cells, and inline blocks</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

Specifies the number of columns. As long as the block is wide enough, the computed value of this property determines the actual column count. If the combination of <span class="cssprop">column-width</span> and <span class="cssprop">column-gap</span>
prevents the specified number of columns from fitting, or if you specify auto, as many columns as possible are used. For the detailed specification, see [CSS3 Multi-column layout 3.4 Pseudo-algorithm](https://www.w3.org/TR/css3-multicol/#pseudo-algorithm).

### column-width

<dl>

<dt>Value</dt>
<dd>auto | length</dd>
<dt>Initial value</dt>
<dd>auto</dd>
<dt>Applies to</dt>
<dd>Non-replaced block-level elements (except tables), table cells, and inline blocks</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

Sets the column width. If the block width is fixed, column widths are adjusted to fill the block,
so the actual column width may exceed the computed value. For the detailed specification, see [CSS3 Multi-column layout 3.4 Pseudo-algorithm](https://www.w3.org/TR/css3-multicol/#pseudo-algorithm).

### columns

This property sets <span class="cssprop">column-count</span> and <span class="cssprop">column-width</span>
at the same time. For example, <span class="cssdecl">columns:
2 10em;</span> is equivalent to <span class="cssdecl">column-count: 2;
column-width: 10em;</span>.

### column-gap

<dl>

<dt>Value</dt>
<dd>normal | length</dd>
<dt>Initial value</dt>
<dd>normal</dd>
<dt>Applies to</dt>
<dd>Multi-column elements</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

Sets the gap between columns. normal is equivalent to 1em.

### column-rule-color

<dl>

<dt>Value</dt>
<dd>color</dd>
<dt>Initial value</dt>
<dd>

The same value as <span class="cssprop">color</span>

</dd>
<dt>Applies to</dt>
<dd>Multi-column elements</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

The color of the rule between columns.

### column-rule-style

<dl>

<dt>Value</dt>
<dd>

Border style (as with <span class="cssprop">border-top-style</span> and similar properties)

</dd>
<dt>Initial value</dt>
<dd>none</dd>
<dt>Applies to</dt>
<dd>Multi-column elements</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

The style of the rule between columns. The value is one of none, dotted, dashed, solid, double, groove,
ridge, inset, or outset. none hides the rule and sets its width to zero.
inset is displayed as ridge, and outset as groove.

### column-rule-width

<dl>

<dt>Value</dt>
<dd>

Border width (as with <span class="cssprop">border-top-width</span> and similar properties)

</dd>
<dt>Initial value</dt>
<dd>medium</dd>
<dt>Applies to</dt>
<dd>Multi-column elements</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

The width of the rule between columns.

### column-rule

This property sets <span class="cssprop">column-rule-color</span>, <span class="cssprop">column-rule-style</span>, and <span class="cssprop">column-rule-width</span>
together. Its syntax is the same as <span class="cssprop">border-top</span> and similar properties.
For example, <span class="cssdecl">column-rule: 2pt dashed Red;</span> and
<span class="cssdecl">column-rule-color: Red;
column-rule-style: dashed; column-rule-width: 2pt;</span>
have the same meaning.

### column-fill

<dl>

<dt>Value</dt>
<dd>balance | auto</dd>
<dt>Initial value</dt>
<dd>balance</dd>
<dt>Applies to</dt>
<dd>Multi-column elements</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

Controls how column ends are aligned. balance makes the extent of each column in the page progression direction as equal as possible.
auto does not balance the columns. Typically, balance is used for horizontal writing and auto for vertical writing.
auto also processes faster.

<div class="note">

balance **determines the column height with a single estimate**. It examines the available break positions
and calculates the minimum height that fits all columns just once.
Simple layouts are balanced precisely. However, **when the amount of content that fits depends on the column height,
as with floating boxes, the result is approximate**, and column heights may be uneven.

If the layout is badly disrupted, adjust the amount of content or the placement of floating boxes.

</div>

### column-span

<dl>

<dt>Value</dt>
<dd>1 | all</dd>
<dt>Initial value</dt>
<dd>1</dd>
<dt>Applies to</dt>
<dd>Statically positioned, non-floating elements</dd>
<dt>Inherited</dt>
<dd>No</dd>

</dl>

Specifies column spanning. An element can span either one column or all columns.
For example, you can make only a heading span all columns.

Spanning all columns interrupts the multi-column layout at that point. If an ancestor element has a multi-column layout, it is effectively closed before the spanning element and resumed afterward.
However, the columns immediately before the spanning element are balanced regardless of the
<span class="cssprop">column-fill</span> setting.

### Column breaks and page breaks

#### Automatic column breaks and page breaks

When multi-column content extends beyond the end of the page, a column break occurs if possible; otherwise, a page break occurs.
For example, within a three-column element, overflow in the first or second column causes a column break, while overflow in the third causes a page break.
Column breaks split content in the same way as page breaks.

Page breaks occur within floating boxes and table cells,
but not within floating boxes or table cells that have a multi-column layout. They also do not occur within boxes with a specified height.
In a box with a specified height, even when the last column fills up,
a column break occurs instead of a page break, so the actual number of columns may exceed the specified count.

#### Forced column breaks and page breaks

The left, right, and always values of <span class="cssprop">page-break-before</span> and <span class="cssprop">page-break-after</span>
cause page breaks even in multi-column layout.
You can use the column keyword to force a column break. The page keyword is also available and has the same meaning as always.

For example, specifying <span class="cssdecl">page-break-after: column;</span>
causes a column break if the current column is not the last. It causes a page break if the current column is the last and a page break is possible, or if there is no multi-column layout.
