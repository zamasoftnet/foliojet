## <a id="style-pagebreak">改ページ制御</a>

### 用語の定義

この章では、以下の用語を使って説明します。

<dl>

<dt>絶対配置ボックス</dt>
<dd><span class="cssdecl">position: absolute;</span>が指定された要素です。<span class="cssdecl">position: fixed;</span>が指定された要素やページごとに生成されるコンテンツも広義の絶対配置ボックスです。</dd>
<dt>浮動ボックス</dt>
<dd><span class="cssdecl">float: left;</span>または<span class="cssdecl">float: right;</span>が指定された要素です。</dd>
<dt>通常のフローのブロック</dt>
<dd>何も指定されていない&lt;p&gt;要素や&lt;div&gt;要素や<span class="cssdecl">display: block;</span>が指定された要素で絶対配置ボックスでも浮動ボックスでもないものです。</dd>

</dl>

テーブルは次の部分からなっています。

<dl>

<dt>テーブルキャプション</dt>
<dd>HTMLのcaptionタグ、あるいはdisplayがtable-captionと指定された部分。</dd>
<dt>テーブルヘッダ</dt>
<dd>HTMLのtheadタグ、あるいはdisplayがtable-header-groupと指定された部分。</dd>
<dt>テーブルフッタ</dt>
<dd>HTMLのtfootタグ、あるいはdisplayがtable-footer-groupと指定された部分。</dd>
<dt>テーブル行グループ</dt>
<dd>HTMLのtbodyタグ、あるいはdisplayがtable-row-groupと指定された部分。ただし、tbodyやtable-row-groupを省略して、テーブルの中に直接存在する行も行グループに属すると見なされます。</dd>

</dl>

### 強制改ページ

強制改ページは、指定した場所で強制的に改ページを発生させる機能です。 強制改ページを指定できるのは次の場所です。

- 通常のフローのブロックの直前
- 通常のフローのブロックの直後
- 浮動ボックスの直前
- 浮動ボックスの直後
- テーブルの直前
- テーブルの直後
- テーブル行グループの直前
- テーブル行グループの直後
- テーブル行の直前
- テーブル行の直後
- テーブルセルの直前
- テーブルセルの直後

ただし、上記の場所であっても浮動ボックス内、絶対配置ボックス内、テーブルセル内では強制改ページを発生することはできません。

要素の直前の強制改ページの指定は<span class="cssdecl">page-break-before: always;</span>です。 要素の直後の強制改ページの指定は<span class="cssdecl">page-break-after: always;</span>です。

以下は強制改ページを使って表紙を作る例です。

```html
<html>
  <head>
    <title>ドキュメント</title>
  </head>
  <body>
    <h1 style="page-break-after: always;">表紙</h1>
    <p>本文...</p>
  </body>
</html>
```

また、単純に改ページするためではなく、改ページした直後のページが右になるか、左になるかを指定することができます。 この場合、調整のために空白のページが１つつくられる可能性があります。

強制改ページの後のページが右になるか、左になるかを指定するには、 page-break-beforeおよびpage-break-afterプロパティの値として、alwaysの代わりに left(左ページにする場合)またはright(右ページにする場合)を指定します。

以下の例では、必ず右側になる中表紙を生成しています。

```html
<html>
  <head>
    <title>ドキュメント</title>
  </head>
  <body>
    <h1 style="page-break-after: always;">表紙</h1>
    <p style="page-break-after: always;">本文1...</p>
    <p>本文2...</p>
    <h1 style="page-break-before: right;">中表紙</h1>
  </body>
</html>
```

ただし、強制改ページのleft, rightの指定はテーブル内部では適用されず、いずれもalwaysと解釈されます。

### <a id="style-pagebreak-widows-orphans"></a><span class="cssprop">orphans</span>と<span class="cssprop">widows</span>

<span class="cssprop">orphans</span>と<span class="cssprop">widows</span>プロパティは、段落(ここでは通常のフローのブロックを指し、&lt;br&gt;による空行等は段落の区切りとは認識されません)の途中で改ページが発生する場合、必ず前のページに残す行数と、 後のページに表示される行数を指定するものです。

両プロパティの初期値はCSS仕様に従い2です。これは、改ページまたは改段の前後に段落の1行だけが孤立することを防ぎます。

日本語組版では孤立した1行を許容することが多く、特に縦書きや段組みでは、初期値の2によって本来入るはずの1行が次のページや段へ送られ、ページ末や段末に不自然な空きが生じることがあります。そのため、日本語の本文、特に縦書きや段組みでは、次のように1を明示することを推奨します。1を指定すると、改ページまたは改段の前後に最低1行あればよいことになります。

```css
body {
  orphans: 1;
  widows: 1;
}
```

欧文組版や、孤立した1行を避けたい文書では、初期値の2をそのまま使用するか、必要に応じてさらに大きな値を指定してください。

なお、実際の行数ではなく、行から行までの長さを標準的な行の高さ (段落に適用された<span class="cssprop">line-height</span>による高さ) で割った値を整数に丸めた数値を基準に計算します。 そのため、行内に大きな画像が存在したり、インラインに対する<span class="cssprop">font-size</span>の指定により、例えば通常の2倍の高さに拡張されている行が存在すれば、2行として計算します。 これはCSS 2.1の仕様にはありませんが、より直感的な改ページとするための仕様です。

#### <span class="cssprop">orphans</span>

ある段落がページの下端にかかっている場合、段落を途中で分割して改ページする必要があります。 <span class="cssprop">orphans</span>はそのような場合に、改ページされる前のページに最低限残さなければならない行数です。 例えば、以下の例では<span class="cssprop">orphans</span>が3に対して改ページ前のページに3行があるので、条件を満たしています。

<div title="orphansが3の場合" class="example">
<table class="pages">
<tr>
<td style="vertical-align: top; width: 15em;">
<div>
(段落1)1行目...<br /> 2行目...<br /> 3行目...<br /> 4行目...<br />
5行目...<br /> 6行目...<br /> 7行目...<br /> 8行目...<br />
</div> <br />
<div>
(段落2)1行目...<br /> 2行目...<br /> 3行目...<br />
</div>
</td>
<td style="vertical-align: top; width: 15em;">
<div>
4行目...<br /> 5行目...<br />
</div>
</td>
</tr>
<tr>
<th>1ページ目</th>
<th>2ページ目</th>
</tr>
</table>
</div>

同じ文書で<span class="cssprop">orphans</span>を4に指定すると、 そのままでは<span class="cssprop">orphans</span>を満たすことができないため、 段落をまるごと次ページに移動してしまいます。

<div title="orphansが4の場合" class="example">
<table class="pages">
<tr>
<td style="vertical-align: top; width: 15em;">
<div>
(段落1)1行目...<br /> 2行目...<br /> 3行目...<br /> 4行目...<br />
5行目...<br /> 6行目...<br /> 7行目...<br /> 8行目...<br />
</div> <br /> <br /> <br /> <br />
</td>
<td style="vertical-align: top; width: 15em;">
<div>
(段落2)1行目...<br /> 2行目...<br /> 3行目...<br /> 4行目...<br />
5行目...<br />
</div>
</td>
</tr>
<tr>
<th>1ページ目</th>
<th>2ページ目</th>
</tr>
</table>
</div>

#### <span class="cssprop">widows</span>

文書の内容の高さがページの高さよりわずかに高い場合、 次のページに文書の内容のうち何行かを先送りしなければなりません。 <span class="cssprop">widows</span>は改ページされた後のページに最低限表示されなければならない行数で、 <span class="cssprop">widows</span>を満たすように先送りする行数を調整します。 例えば、<span class="cssprop">widows</span>が2の場合、以下の例では2ページ目に2行存在するので条件を満たしています。

<div title="widowsが2の場合" class="example">
<table class="pages">
<tr>
<td style="vertical-align: top; width: 15em;">
<div>
(段落1)1行目...<br /> 2行目...<br /> 3行目...<br /> 4行目...<br />
5行目...<br /> 6行目...<br /> 7行目...<br /> 8行目...<br />
</div> <br />
<div>
(段落2)1行目...<br /> 2行目...<br /> 3行目...<br />
</div>
</td>
<td style="vertical-align: top; width: 15em;">
<div>
4行目...<br /> 5行目...<br />
</div>
</td>
</tr>
<tr>
<th>1ページ目</th>
<th>2ページ目</th>
</tr>
</table>
</div>

同じ文書で<span class="cssprop">widows</span>を3に指定すると、 以下のように前のページから次のページへ行を移動して、<span class="cssprop">widows</span>を満たすようにします。

<div title="widowsが3の場合" class="example">
<table class="pages">
<tr>
<td style="vertical-align: top; width: 15em;">
<div>
(段落1)1行目...<br /> 2行目...<br /> 3行目...<br /> 4行目...<br />
5行目...<br /> 6行目...<br /> 7行目...<br /> 8行目...<br />
</div> <br />
<div>
(段落2)1行目...<br /> 2行目...<br />
</div> <br />
</td>
<td style="vertical-align: top; width: 15em;">
<div>
3行目...<br /> 4行目...<br /> 5行目...<br />
</div>
</td>
</tr>
<tr>
<th>1ページ目</th>
<th>2ページ目</th>
</tr>
</table>
</div>

#### <span class="cssprop">orphans</span>と<span class="cssprop">widows</span>の競合

<span class="cssprop">orphans</span>と<span class="cssprop">widows</span>の両方の条件を同時に満たすことができない場合も、<span class="cssprop">orphans</span>を満たせなかった場合と同様に段落を丸ごと次ページに移動します。

例えば、以下の状況では<span class="cssprop">orphans</span>と<span class="cssprop">widows</span>の両方が満たされています。

<div title="orphansが3でwidowsが2の場合" class="example">
<table class="pages">
<tr>
<td style="vertical-align: top; width: 15em;">
<div>
(段落1)1行目...<br /> 2行目...<br /> 3行目...<br /> 4行目...<br />
5行目...<br /> 6行目...<br /> 7行目...<br /> 8行目...<br />
</div> <br />
<div>
(段落2)1行目...<br /> 2行目...<br /> 3行目...<br />
</div>
</td>
<td style="vertical-align: top; width: 15em;">
<div>
4行目...<br /> 5行目...<br />
</div>
</td>
</tr>
<tr>
<th>1ページ目</th>
<th>2ページ目</th>
</tr>
</table>
</div>

この状態で<span class="cssprop">widows</span>を3に設定すると、 <span class="cssprop">orphans</span>は満たせるが<span class="cssprop">widows</span>は満たせない状態になります。

<div title="orphansが3でwidowsが3の場合" class="example">
<table class="pages">
<tr>
<td style="vertical-align: top; width: 15em;">
<div>
(段落1)1行目...<br /> 2行目...<br /> 3行目...<br /> 4行目...<br />
5行目...<br /> 6行目...<br /> 7行目...<br /> 8行目...<br />
</div> <br /> <br /> <br /> <br />
</td>
<td style="vertical-align: top; width: 15em;">
<div>
(段落2)1行目...<br /> 2行目...<br /> 3行目...<br /> 4行目...<br />
5行目...<br />
</div>
</td>
</tr>
<tr>
<th>1ページ目</th>
<th>2ページ目</th>
</tr>
</table>
</div>

ただし、段落がページの先頭にある場合は、<span class="cssprop">orphans</span>が無視され、 少なくとも１行が前ページに残されます。

### 改ページの抑制

次の場所には、改ページの抑制を指定することができます。

- 通常のフローのブロックの内部
- 通常のフローのブロックの直前
- 通常のフローのブロックの直後
- 浮動ボックスの内部
- テーブルの内部
- テーブルの直前
- テーブルの直後
- テーブル行グループの直前
- テーブル行グループの直後
- テーブル行の内部
- テーブル行の直前
- テーブル行の直後
- テーブルセルの内部
- テーブルセルの直前
- テーブルセルの直後

#### 内部の改ページ抑制

内部での改ページを抑制するには、<span class="cssdecl">page-break-inside: avoid;</span>という指定をします。

改ページ抑制されたボックスがページをはみ出す場合は、ボックスが丸ごと次のページの先頭に先送りされます。 ただし、ボックスが(先送りの結果か、元々そこにあるかに関わらず)ページの先頭にある場合、かつボックスの高さがページの高さを超えてしまう場合は、 改ページの抑制を無視してページ分割されます。

#### 前後の改ページ抑制

ボックスの前後での改ページを抑制するには、<span class="cssdecl">page-break-before: avoid;</span>(ボックスの前)あるいは <span class="cssdecl">page-break-after: avoid;</span>(ボックスの後)を指定します。 改ページが抑制された箇所での改ページを避け、前後の何行かを必ず1つのページに含めるようにします。 改ページが抑制されたポイントの前に入れる行数は<span class="cssprop">orphans</span>に依存します。

強制改ページと改ページの抑制が競合する場合は、強制改ページが優先されます。 例えば、<span class="cssdecl">page-break-after: always;</span>と指定された段落の直後に、 <span class="cssdecl">page-break-before: avoid;</span>と指定された段落がある場合です。 このような競合が起こった場合、常に強制改ページが優先されます。 つまり、このケースでは<span class="cssdecl">page-break-before: avoid;</span>は無視されて改ページが発生します。

なお、HTMLのh1〜h6要素にはデフォルトで<span class="cssdecl">page-break-before: avoid;</span> が指定されています。

### <a id="style-pagebreak-limits">改ページ制御の限界</a>

改ページの指定は**お願い**であって、命令ではありません。
指定どおりにできない場面があります。ここでは、実際に困りやすいものを
まとめて説明します。

#### 抑制は「絶対に切らない」ではない

<span class="cssdecl">page-break-inside: avoid;</span>を指定したボックスが
1ページに収まらない場合、**指定は無視されて分割されます**。

そうしないと出力できないからです。
ページの先頭に置いても収まらないものは、どこへ送っても収まりません。
このとき「送り続けて永久に終わらない」より「切って出す」ほうが実用的です。

つまり<span class="cssdecl">page-break-inside: avoid;</span>は
**「1ページに収まるなら切らないでほしい」**という意味だと考えてください。
確実に切りたくない箇所は、**内容が1ページに収まる大きさに抑える**しかありません。

#### <a id="pagebreak-rescue">分割できないものは、切って続ける</a>

画像や大きな文字のように**分割しようのないもの**が紙面より大きい場合は、
縮小せず、**紙面の下端で幾何学的に切断し、残りを次のページの先頭から続けます**
<span class="since">4.0.0</span>。
行間やブロックの継ぎ目は考慮しない、文字通り機械的な切断です。
内容はベクターのまま(文字は文字のまま)描かれ、画像化はしません。

切断面には枠線・マージンを付けません
(<span class="cssdecl">box-decoration-break: slice;</span>と同じ扱い)。
続きの断片はPDF上では装飾(artifact)として出力するので、
テキスト抽出や読み上げで内容が二重になることはありません。

対象は、ページ分割できない内容すべてです。置換要素(画像、SVG、MathML、バーコード)、
巨大なフォントの行、インラインブロック、表のセル、
書字方向が外側と直交しているボックスなどが含まれます。
絶対配置のボックスは対象外で、はみ出したまま出力します
(透かしや裁ち落としのように、意図的にはみ出させる用途が多いためです)。

自動で縮小しないのは、指定した寸法と実際の寸法が食い違い、
版面が予測できなくなるためです。
紙に収めたい場合は、内容の側で大きさを調整してください。

#### 抑制どうし・強制との競合

<span class="cssdecl">page-break-after: always;</span>の直後に
<span class="cssdecl">page-break-before: avoid;</span>があるような場合は、
**強制改ページが優先**されます。抑制のほうが無視されます。

#### 切れない場所がある

次の場所では、どう指定しても改ページは起きません。

- 行の内部(1行の途中で切ることはしません)
- 画像の内部
- 絶対配置ボックスの内部
- テーブルキャプションの内部、およびキャプションと表の間
- テーブルヘッダ・フッタの内部、およびヘッダ・フッタと行グループの間

また、**浮動ボックスの中・絶対配置ボックスの中・テーブルセルの中では、
強制改ページを指定しても効きません**。

#### 書字方向が変わる部分は切れない

縦書きの中の横書き(またはその逆)のように、
**書字方向が外側と違うブロックは、途中で改ページできません**。
まとまり全体が1つの単位として扱われ、収まらない場合は行方向にはみ出します。

段組を併用するか、そのブロックのページ進行方向の大きさを明示して、
紙面に収まるようにしてください。

#### 段の高さは近似のことがある

段組で<span class="cssdecl">-cssj-column-fill: balance;</span>を使うと
各段の高さを揃えますが、これは**一度の見積りによる近似**です。
浮動ボックスがあるなど、段の高さによって入る内容の量が変わる場合は、
段の高さが揃わないことがあります。
詳細は[段組](#style-columns)を参照してください。

### 自動改ページ

文書の内容がページの下端にさしかかった部分で、自動的に改ページします。 自動的な改ページが発生するのは次の場所です。

- 通常のフローのブロックの間
- 画像以外の通常のフローのブロックの内部
- 画像以外の浮動ボックスの内部
- 行の間
- テーブル行グループの間
- テーブル行グループ内の行の間
- テーブル行グループ内の行の内部

逆に、以下の場所ではどのような場合も改ページされることはありません。

- 行の内部
- 画像の内部
- 絶対配置ボックスの内部
- テーブルキャプションの内部
- テーブルキャプションとテーブルの間
- テーブルヘッダの内部
- テーブルフッタの内部
- テーブルヘッダと行グループの間
- テーブルフッタと行グループの間

#### 通常のフローのブロック

通常のフローでは <span class="cssprop">orphans</span>, <span class="cssprop">widows</span>を尊重して改ページが行われます。 ブロックに境界線がある場合に境界線の直後、あるいは高さが指定されていて内容がないブロックの内部では なるべく改ページを避けますが、ブロックがページの先頭ある場合は改ページが発生します。

#### 浮動ボックス

浮動ボックスがページの下端にかかったときの扱いは、次の順に決まります。

1. **全体が下端より前にあれば**、そのページに置きます
2. **全体が下端より後にあれば**、丸ごと次のページへ送ります
3. **下端にまたがっていて、分割できるなら**分割します。
	次ページに送られた部分は、次のページで浮動ボックスとして置き直されます
4. **分割できない場合**は、そのボックスがページの先頭にあるなら
	<b>紙面の下端で機械的に切断して続きを次のページへ送り</b>、
	先頭になければ丸ごと次のページへ送ります

分割できないのは、画像などの分割しようのない内容、
<span class="cssdecl">page-break-inside: avoid;</span>が指定されている場合、
および浮動ボックスの書字方向が外側と直交している場合です。

4の機械的な切断は、**紙面より大きくて分割もできない**内容が
ページの先頭に来たときの最後の手段です。次のページへ送っても同じことが
起きるので、送らずに置き、収まらない部分を切って続けます
(<a href="#pagebreak-rescue" class="pageref">分割できないものは、切って続ける</a>)。

浮動ボックスの分割では<span class="cssprop">orphans</span>,
<span class="cssprop">widows</span>の指定も尊重しますが、
条件を満たせない場合はこれらを無視して分割します。

<div class="note">

浮動ボックスの中に入れ子になった浮動ボックスでは
<span class="cssdecl">page-break-inside: avoid;</span>は効きません。

</div>
