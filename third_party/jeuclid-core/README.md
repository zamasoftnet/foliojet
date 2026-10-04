# JEuclid core (Copper PDF の手入れ版)

FolioJet の MathML 組版に使う JEuclid core です。上流は更新が止まっているため、
ソースをここへ取り込み、組版の不具合を直して使います。

- 元: `de.rototor.jeuclid:jeuclid-core:3.1.14` の sources jar(Maven Central、
  SHA-1 `5e3b0224d13c9e99de660d66225a1796b62d6f4d`)。許諾は Apache License 2.0(`LICENSE.txt`)。
- 取り込み方: foliojet4 のサブプロジェクト `:jeuclid-core`(`settings.gradle`)。foliojet4 は Maven の
  `de.rototor.jeuclid:jeuclid-core` の代わりにこれへ依存します。版は `3.1.14-copper1`。
- 外したもの: FolioJet が使わない変換器(`ant/`、`converter/Batik*`、`converter/FreeHep*`)。
  任意の依存(Ant・FreeHEP・batik-svggen)を要らなくするため。`META-INF/services` の
  `ConverterDetector` からも外しました。

## 手を入れたところ

上流からの変更は、ここに日付つきで書きます(`git log -- third_party/jeuclid-core` でも辿れます)。

- 2026-10-04(TECH-20261003-004 の⑪〜⑭、時限暗号の本でカンマが前の字に接していた)
  - ⑪ `StringUtil.getTextLayoutInfo`: 字の幅をインクの範囲でなく送り幅で測る(右の余白を落とさない。
    mo の `trim` は左の余白も捨てていたのをやめる)。送り幅の外へ出るインクは含める。幅は offset を含む
    ので `Mo.layoutStage1` で offset を足さない
  - ⑫ `AttributesHelper.EM` を 0.8389 から 1 へ(em は字の大きさ)
  - ⑬ `moDictionary.xml` に U+2212(−、中置・前置は `-` と同じ)と U+00D7(×、中置 mediummathspace)。
    U+2212 の後置形は置かない(MathML Core に無い。末尾の − は中置形の空きへ戻る——当初は `-` に合わせて
    後置形も入れ、行で切った式の末尾の − が詰まった)
  - ⑭ `Mo.detectFormParameter`: math・mstyle・msqrt・mtd など暗黙の mrow を持つ親でも先頭を前置形・末尾を
    後置形に。子が 1 つだけの行の演算子は中置形(MathML 3 §3.2.5.7.2)
  - 上流の jar にある `moDictionary.ser`・`charmap.ser` は作らない(XML と UnicodeData.txt を読むのは
    JVM ごとに 1 回で、測って 0.2 秒と 0.1 秒)。`appendixc.ser`(MathML 3 の辞書、読むと 5 秒)は
    使われていない
- 2026-10-04(⑮⑯、添字の高さが土台のインクで揺れる・イタリック補正が無い)
  - 新しい `font/MathTable`: OpenType の MATH 表(添字の定数・字ごとのイタリック補正)を読む。FolioJet の
    `MathFonts` が書体のファイルを登録するときに一緒に読み、AWT の書体の family 名で引く
  - `ScriptSupport`: MATH 表のある書体では TeX の規則 18 で添字を置く(SubscriptShiftDown・SubscriptTopMax・
    SuperscriptShiftUp・SuperscriptBottomMin・SubSuperscriptGapMin 等。字 1 つの土台は高さ・深さで動かさない)。
    上付きは土台の字のイタリック補正だけ右、添字の後に SpaceAfterScript。MATH 表の無い書体は上流のまま
  - `AbstractTokenWithTextLayout`: 字 1 つの mi の幅にイタリック補正を足す(添字の土台のときは足さない)
- 2026-10-04(tech の 19093 の確かめで分かった 2 点+原因)
  - `StringUtil.mapCpavToCpaf`: MATH 表のある書体では数式用の英数字(斜体の x は U+1D465)を先に使う(MathML Core と
    同じ)。「ふつうの字を斜体で」が先に当たり、斜体の面の無い STIX Two Math では立体の字を AWT が機械的に傾けた字形に
    なって、イタリック補正も 0 だった。MATH 表の無い書体は今までどおり
  - `AbstractTokenWithTextLayout.getItalicCorrection`: BMP の外の字は字形の番号が 2 つ返る(2 つ目は見えない)ので、
    1 つ目を使う(1 つでないと 0 にしていた)
  - `ScriptSupport`: 字の土台の添字は送り幅の終わりから置く(下付きは斜体の字の張り出しの下へ入る)。上付きはそこから
    補正だけ右。1 字の mo(閉じ括弧など)も字の土台として扱う((−x)³ の 3 を x³ と同じ高さに)
