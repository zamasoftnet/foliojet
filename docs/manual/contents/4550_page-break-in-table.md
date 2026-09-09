## <a id="style-page-break-in-table">テーブル内での改ページ</a>

テーブルの行間、テーブルの行の途中(セルの途中)での改ページができます。
また、テーブルのヘッダとテーブルのフッタは各ページで繰り返し表示されます。

### 改ページされない場所

テーブル内の次の場所では、どのような場合も改ページされることはありません。

- テーブルのキャプション内
- テーブルのキャプションとテーブルの間
- テーブルヘッダの内部
- テーブルフッタの内部
- テーブルのヘッダと行グループの間
- テーブルのフッタと行グループの間

従って、上記の部分に指定された<span class="cssprop">page-break-after</span>, <span class="cssprop">page-break-before</span>, <span class="cssprop">page-break-inside</span>
は無視されます。 また、上記の規則が適用された結果、テーブルの行グループが存在する限り、
どのように改ページが発生する場合も、テーブルの行グループの一部が常に表示され、 ヘッダかフッタだけのテーブルが現れることはありません。

上記の部分がページの高さを超える場合は、テーブルがページの下端をはみ出します。
従って、適切なレイアウトとなるためには、テーブルのキャプションとヘッダとフッタの高さが、ページの高さに対して十分に小さいことが望ましいです。

### page-break-XXXの適用

改ページに関する特性は行の間には通常のフローのブロックと同様に適用されます。
改ページに関する特性の指定は行グループの間にも適用されます。 ただし、強制改ページでleft,
rightの指定は有効ではなく、効果はalwaysと同じになります。

セルに対する<span class="cssprop">page-break-inside</span>は、行に適用されます。
同じ行内でautoとavoidが指定されたセルが競合する場合、avoidが優先されます。
すなわち、行に属するセルのうち１つでもavoidが指定された場合、行全体の中での改ページが禁止されます。
また、セルがrowspanで連結されている場合、セルが属する全ての行の内部で改ページが抑制されるのに加えて、行の間で <span class="cssprop">page-break-after</span>および<span class="cssprop">page-break-before</span>
にavoidが指定されたものと見なされます。

セルに対する<span class="cssprop">page-break-after</span>および <span class="cssprop">page-break-before</span>は、行に適用されます。
この場合の優先順位は次の順になります。

always > avoid > auto

テーブルがページの先頭にあり、かつ改ページ禁止指定のために、ページの下端までの間で改ページできない場合は、行間の改ページ禁止を無視します。
この場合は、改ページが禁止された部分であっても改ページが発生します。

### テーブル行内部(セル内部)での改ページ

テーブル行内に、<span class="cssdecl">page-break-inside: avoid;</span>が指定されたセルがなく、
テーブル行がページの下端に差し掛かっている場合、そのテーブル行の分割が試みられます。 このとき、<span class="cssprop">orphans</span>と<span class="cssprop">widows</span>が尊重され、
行に属する全てのセルが分割不可能な場合は、行全体が次のページに先送りされます。 １つでも分割可能なセルがあった場合は、行が分割されます。
この場合、他のセルに対しては<span class="cssprop">orphans</span>と<span class="cssprop">widows</span> を無視した分割が起こる可能性があります。

<p class="note">
分割されたセルに対しては<span class="cssprop">vertical-align</span>による垂直アラインメントの指定が無効となり、
セルの内容は全てセルの上端につけられます。
</p>

### デフォルトの改ページの扱い

HTMLのtd, th要素は、一般的なブラウザと同様、デフォルトではセル内の改ページを
禁止しません<span class="since">4.0.0</span>。
セル内で改ページさせたくない場合は、明示的に指定してください。

```css
td, th {
  break-inside: avoid; /* page-break-inside: avoid も同義 */
}
```

<p class="note">
バージョン3.2まで(および4.0の開発中のビルド)は、td, th要素のデフォルトが
<span class="cssdecl">page-break-inside: avoid;</span>でした。
このデフォルトは、ページ下端に差し掛かった縦長の表全体が次のページへ
先送りされ、先頭ページがほぼ白紙になる原因となるため廃止されました。
</p>
