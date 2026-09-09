## <a id="style-columns">多段組</a>

多段組に対応しています<span class="since">3.0.0</span>。
CSS Multi-column Layout Module Level 1 に沿った実装です。

**プロパティは標準の名前(<span class="cssprop">column-count</span>など)で
指定してください。** `-cssj-`を付けた名前も互換のために受け付けますが、
新しく書く文書では標準の名前を使ってください。

印刷面の大きさに比べて文字が小さい場合、1行の幅が大きくなってしまい非常に読みにくいことがあります。
そんな時は、多段組をすると読みやすくなります。 また、空白が少なくなるため、より紙面を節約できます。

多段組では、段数に応じて行方向の幅が圧縮されます。 ％指定で配置された画像やボックスの大きさは、段の幅に対する比率となります。

見出しなどは、ブチ抜きで配置することができます。

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
妖怪（ようかい）は、日本で伝承される民間信仰において、人間の理解を超える奇怪で異常な現象や、あるいはそれらを起こす、不可思議な力を持つ非日常的な存在のこと。妖（あやかし）または物の怪（もののけ）、魔物（まもの）とも呼ばれる。
</p>
<h1>かっぱ</h1>
<img src="kappa.png" alt="かっぱ" />
<p>
河童（かっぱ）は、日本の妖怪・伝説上の動物、または未確認動物。標準和名の「かっぱ」は、「かわ（川）」に「わらは（童）」の変化形「わっぱ」が複合した「かわわっぱ」が変化したもの。河太郎（かわたろう）とも言う。ほぼ日本全国で伝承され、その呼び名や形状も各地方によって異なる。
</p>
</div>
</body>
</html>
```

<div class="figure" title="２段組(表示結果)">
<object data="images/style-columns-1.svgz" type="image/svg+xml"
	style="width: 160mm;"></object>
</div>

段組に関する各プロパティの説明は次のとおりです。

### column-count

<dl>

<dt>値</dt>
<dd>auto | 1以上の整数</dd>
<dt>初期値</dt>
<dd>auto</dd>
<dt>適用対象</dt>
<dd>置換不可能なブロックレベル要素（テーブルを除く）、テーブルセル、インラインブロック</dd>
<dt>値の継承</dt>
<dd>しない</dd>

</dl>

段組の段数です。 ブロックの幅に余裕がある限り、このプロパティの計算値が、実際の段組の段数になります。 <span class="cssprop">column-width</span>, <span class="cssprop">column-gap</span>
の兼ね合いで、指定した段数が確保できない場合、 あるいはautoを指定した場合はできる限りの段数が確保されます。 詳しい仕様は [CSS3 Multi-column layout 3.4 Pseudo-algorithm](https://www.w3.org/TR/css3-multicol/#pseudo-algorithm) のとおりです。

### column-width

<dl>

<dt>値</dt>
<dd>auto | 長さ</dd>
<dt>初期値</dt>
<dd>auto</dd>
<dt>適用対象</dt>
<dd>置換不可能なブロックレベル要素（テーブルを除く）、テーブルセル、インラインブロック</dd>
<dt>値の継承</dt>
<dd>しない</dd>

</dl>

段の幅を設定します。 ブロックの幅が固定されている場合は、 ブロックの幅を満たすように段の幅が調整されるため、
実際の段の幅は計算値より広くなることがあります。 詳しい仕様は [CSS3 Multi-column layout 3.4 Pseudo-algorithm](https://www.w3.org/TR/css3-multicol/#pseudo-algorithm) のとおりです。

### columns

<span class="cssprop">column-count</span>と <span class="cssprop">column-width</span>
を同時に設定することができるプロパティです。 例えば、 <span class="cssdecl">columns:
2 10em;</span> は <span class="cssdecl">column-count: 2;
column-width: 10em;</span> と同じ意味になります。

### column-gap

<dl>

<dt>値</dt>
<dd>normal | 長さ</dd>
<dt>初期値</dt>
<dd>normal</dd>
<dt>適用対象</dt>
<dd>段組された要素</dd>
<dt>値の継承</dt>
<dd>しない</dd>

</dl>

段の間の幅を設定します。 normal は 1em と同じです。

### column-rule-color

<dl>

<dt>値</dt>
<dd>色</dd>
<dt>初期値</dt>
<dd>

<span class="cssprop">color</span>と同じ値

</dd>
<dt>適用対象</dt>
<dd>段組された要素</dd>
<dt>値の継承</dt>
<dd>しない</dd>

</dl>

段の間の境界線の色です。

### column-rule-style

<dl>

<dt>値</dt>
<dd>

境界のスタイル（<span class="cssprop">border-top-style</span>等と同様）

</dd>
<dt>初期値</dt>
<dd>none</dd>
<dt>適用対象</dt>
<dd>段組された要素</dd>
<dt>値の継承</dt>
<dd>しない</dd>

</dl>

段の間の境界線のスタイルです。 none,dotted, dashed, solid, double, groove,
ridge, inset, outsetのいずれかです。 noneは境界線を表示せず、境界線の太さもゼロになります。
insetはridge, outsetはgrooveとそれぞれ同じ表示になります。

### column-rule-width

<dl>

<dt>値</dt>
<dd>

境界の太さ（<span class="cssprop">border-top-width</span>等と同様）

</dd>
<dt>初期値</dt>
<dd>medium</dd>
<dt>適用対象</dt>
<dd>段組された要素</dd>
<dt>値の継承</dt>
<dd>しない</dd>

</dl>

段の間の境界線の太さです。

### column-rule

<span class="cssprop">column-rule-color</span>, <span class="cssprop">column-rule-style</span>, <span class="cssprop">column-rule-width</span>
をまとめて設定するプロパティです。 <span class="cssprop">border-top</span>等と同様の記述方法です。
例えば、 <span class="cssdecl">column-rule: 2pt dashed Red;</span>
は、 <span class="cssdecl">column-rule-color: Red;
column-rule-style: dashed; column-rule-width: 2pt;</span>
と同じ意味になります。

### column-fill

<dl>

<dt>値</dt>
<dd>balance | auto</dd>
<dt>初期値</dt>
<dd>balance</dd>
<dt>適用対象</dt>
<dd>段組された要素</dd>
<dt>値の継承</dt>
<dd>しない</dd>

</dl>

段組の末尾の揃え方です。 balanceは各段のページ進行方向の幅がなるべく同じになるように揃えます。
autoは、揃えることをしません。 横書きではbalance、縦書きではautoにするのが一般的です。
また、autoの方が処理速度は速くなります。

<div class="note">

balanceは**一度の見積りで段の高さを決めます**。実際に切れる位置を調べて、
全段が収まる最小の高さを1回だけ求める方法です。
単純なレイアウトでは正確に揃いますが、**浮動ボックスなど、段の高さによって
入る内容の量が変わる場合は近似**になり、段の高さが不揃いになることがあります。

大きく崩れる場合は、内容の量や浮動ボックスの配置を調整してください。

</div>

### column-span

<dl>

<dt>値</dt>
<dd>1 | all</dd>
<dt>初期値</dt>
<dd>1</dd>
<dt>適用対象</dt>
<dd>静的な、浮動体以外の要素</dd>
<dt>値の継承</dt>
<dd>しない</dd>

</dl>

段組の、いわゆる「ぶち抜き」を指定します。 指定可能なのは、1段または全段抜きのいずれかです。
例えば、見出しだけを全段抜きにすることができます。

全段抜きにした場合、そこで段組が区切られます。 上位の要素に段組があれば、全段抜きの前で一旦閉じられ、 後で再開するのと同じことになります。
ただし、全段抜きの直前では <span class="cssprop">column-fill</span>
の指定に関係なく、段組の末尾が揃えられます。

### 改段と改ページ

#### 自動的な改段と改ページ

段組みされた内容がページ末端をはみ出す場合、 改段できるのであれば改段し、そうでなければ改ページします。
例えば3段組の要素内で、1段目と2段目の内容がはみ出せば改段し、 3段目の内容がはみ出せば改ページします。
改段による内容の分割のされ方は改ページと同じです。

浮動ボックス、テーブルセル内でも改ページをしますが、
段組みされている浮動ボックス、テーブルセル内では改ページしません。 また、高さが指定されているボックス内でも改ページしません。
高さが指定されているボックスでは、最後の段がいっぱいになっても、
改ページの代わりに改段をするため、実際の段数が設定した段数より多くなることがあります。

#### 強制的な改段と改ページ

<span class="cssprop">page-break-before</span>, <span class="cssprop">page-break-after</span>
のleft, right, alwaysは段組中でも改ページとして機能します。
改段のためには、columnというキーワードを使うことができます。 pageというキーワードも使用可能ですが、alwaysと同じ意味です。

例えば、<span class="cssdecl">page-break-after: column;</span>
という指定がされた場合、段組の途中であれば改段します。 最後の段で、かつ改ページが可能な場合、あるいは段組されていない場合は改ページします。
