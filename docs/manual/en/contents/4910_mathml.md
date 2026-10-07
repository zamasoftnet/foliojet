## <a id="style-mathml">MathML</a>

MathML is supported.

MathML is rendered with JEuclid ([http://jeuclid.sourceforge.net/](http://jeuclid.sourceforge.net/)).
You can use most MathML 2.0 features.

To use MathML, write elements in the http://www.w3.org/1998/Math/MathML namespace within XHTML, as follows.

```xml
<math xmlns="http://www.w3.org/1998/Math/MathML">
 <mrow>
  <mi>x</mi>
  <mo>=</mo>
  <mfrac>
   <mrow>
    <mrow>
     <mo>-</mo>
     <mi>b</mi>
    </mrow>
    <mo>&#xB1;</mo>
    <msqrt>
     <mrow>
      <msup>
       <mi>b</mi>
       <mn>2</mn>
      </msup>
      <mo>-</mo>
      <mrow>
       <mn>4</mn>
       <mo>&#x2062;</mo>
       <mi>a</mi>
       <mo>&#x2062;</mo>
       <mi>c</mi>
      </mrow>
     </mrow>
    </msqrt>
   </mrow>
   <mrow>
    <mn>2</mn>
    <mo>&#x2062;</mo>
    <mi>a</mi>
   </mrow>
  </mfrac>
 </mrow>
</math>
```

The example above is displayed as follows.

<div title="Solutions of a quadratic equation in MathML (rendered output)" class="figure">
<math xmlns="http://www.w3.org/1998/Math/MathML">
 <mrow>
  <mi>x</mi>
  <mo>=</mo>
  <mfrac>
   <mrow>
    <mrow>
     <mo>-</mo>
     <mi>b</mi>
    </mrow>
    <mo>&#xB1;</mo>
    <msqrt>
     <mrow>
      <msup>
       <mi>b</mi>
       <mn>2</mn>
      </msup>
      <mo>-</mo>
      <mrow>
       <mn>4</mn>
       <mo>&#x2062;</mo>
       <mi>a</mi>
       <mo>&#x2062;</mo>
       <mi>c</mi>
      </mrow>
     </mrow>
    </msqrt>
   </mrow>
   <mrow>
    <mn>2</mn>
    <mo>&#x2062;</mo>
    <mi>a</mi>
   </mrow>
  </mfrac>
 </mrow>
</math>
</div>

Writing MathML by hand is difficult, but an Internet search will turn up various tools for creating MathML (such as tools that convert from LaTeX).

### Formula size, color, and font

Formulas are laid out with the CSS `font-size`, `color`, and `font-family` that apply to the `math` element (giving them the same size and color as the body text).
The `mathsize` and `mathcolor` attributes on `mstyle` within a formula are applied to those values.
For `font-family`, list font names from the font configuration (generic families such as `serif` use the default math font).
Characters in formulas are drawn as glyph outlines in an image (you cannot search for or extract them as text).

```css
math { font-family: "Noto Serif", "Noto Serif JP", serif; }
```

An inline formula is positioned with its baseline aligned to the body text's baseline. Subscripts, parentheses, and descenders such as those in y extend below the body text's baseline.<span class="since">4.0.0</span>

In vertical writing, inline formulas are laid out sideways, rotated 90° clockwise like Latin text. The advance along the line is the formula's width, and the line's width is the formula's height. With `text-orientation: upright`, the formula remains upright, and the line's width expands to the formula's width.<span class="since">4.0.0</span>
