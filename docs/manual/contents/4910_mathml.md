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

### 数式の大きさ・色・書体

数式は、`math` 要素に効いている CSS の `font-size`・`color`・`font-family` で組みます(本文と同じ大きさ・色になります)。
式の中の `mstyle` の `mathsize`・`mathcolor` は、その値に対して効きます。
`font-family` は、書体設定にある書体の名前を並べて指定します(総称ファミリの `serif` などは、数式の既定の書体になります)。
数式の中の文字は、書体を画像の輪郭として描きます(文字として検索・抽出はできません)。

```css
math { font-family: "Noto Serif", "Noto Serif JP", serif; }
```

行の中の数式は、数式の基準線を本文の基準線に揃えて置きます。添字や括弧、y のように基準線より下へ出る部分は、本文の基準線の下へ出ます。<span class="since">4.0.0</span>

縦組みの行の中の数式は、欧文と同じく90°右に回した横倒しで組みます。行の向きには式の幅だけ進み、行の幅は式の高さになります。`text-orientation: upright` のときは正立のまま置き、行の幅を式の幅まで広げます。<span class="since">4.0.0</span>
