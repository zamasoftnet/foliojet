## <a id="style-mathml">MathML</a>

MathMLをサポートしています。

MathMLの描画はJEuclid([http://jeuclid.sourceforge.net/](http://jeuclid.sourceforge.net/))を使用しています。
MathML 2.0の機能のほとんどを利用することができます。

MathMLは、以下のとおり http://www.w3.org/1998/Math/MathML 名前空間の要素をXHTML内に記述します。

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

上記の記述は以下のとおりに表示されます。

<div title="MathMLによる二次方程式の解(表示結果)" class="figure">
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

MathMLを手書きするのは大変ですが、インターネットで検索すると、MathMLを作成するための様々なツールがあります(LaTeXから変換するものなど)。
