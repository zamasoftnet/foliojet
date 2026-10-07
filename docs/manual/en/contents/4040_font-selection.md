## <a id="style-font-selection">Font selection</a>

This section explains how fonts are selected within a document.
The settings on the side that invokes layout determine which fonts are available
(<b>Font configuration</b> (pdfg2d manual)).

### <a id="admin-config-fonts-select">Using fonts in a document</a>

Four CSS properties identify the font to use in a document: <span class="cssprop">font-family</span> (family name),
<span class="cssprop">font-weight</span> (weight), <span class="cssprop">font-size</span> (size),
and <span class="cssprop">font-style</span> (style). You can also specify them together with the <span class="cssprop">font</span> property.
The closest font that can display the specified characters is selected based on the family name, weight, and style.

If no font with the specified weight is found, a bold font is synthesized by expanding the character outlines
(conversely, if no font thinner than the specified weight exists, a thinner font is **not** synthesized.
The thinnest available font is simply used).
You can suppress this synthetic bolding and the synthetic slanting (synthetic italic) described below
with the <span class="cssprop">font-synthesis</span> property<span class="since">4.0.0</span>
(use <span class="cssdecl">font-synthesis: none;</span> to suppress both,
or <span class="cssprop">font-synthesis-weight</span> / <span class="cssprop">font-synthesis-style</span> to control them individually.
When synthesis is suppressed, the available font is rendered as it is).

Also, when <span class="cssprop">font-style</span> is set to italic or oblique,
the font is slanted synthetically if no italic-style font is found. If <span class="cssprop">font-weight</span> is also specified,
the selected font may differ depending on whether <span class="cssprop">font-style</span> is italic or oblique, as shown in the table below.

**Font style selection**

| Condition | When italic is specified | When oblique is specified |
| --- | --- | --- |
| A font matches both weight and style | Use the font that matches both weight and style | Use the font that matches both weight and style |
| A font matches only the weight | Slant the font that matches the weight | Slant the font that matches the weight |
| A font matches only the style | Apply synthetic bolding to the font that matches the style | Apply synthetic bolding to the font that matches the style |
| One font matches only the weight and another matches only the style | Apply synthetic bolding to the font that matches the style | Slant the font that matches the weight |
| No font matches either weight or style | Apply synthetic bolding and slanting to a font that matches the other conditions | Apply synthetic bolding and slanting to a font that matches the other conditions |

### <a id="admin-config-fonts-default-font">Default font</a>

If no font is specified in the document, or the specified font cannot be found, the font set with <span class="ioprop">output.default-font-family</span>
is used. The default is serif (Roman or Mincho).

If a document contains a character that cannot be displayed because
there is no corresponding code in the **CMap** (pdfg2d manual),
no matching character in the core fonts, and no matching character in any installed font,
it is replaced with combined characters showing its Unicode value in hexadecimal (a character such as <img src="images/kumimoji.png" style="width: 10.5pt; height: 10.5pt; vertical-align: -1pt;" />).

