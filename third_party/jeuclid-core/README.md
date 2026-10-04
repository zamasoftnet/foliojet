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
  - ⑬ `moDictionary.xml` に U+2212(−、中置・前置・後置は `-` と同じ)と U+00D7(×、中置 mediummathspace)
  - ⑭ `Mo.detectFormParameter`: math・mstyle・msqrt・mtd など暗黙の mrow を持つ親でも先頭を前置形・末尾を
    後置形に。子が 1 つだけの行の演算子は中置形(MathML 3 §3.2.5.7.2)
  - 上流の jar にある `moDictionary.ser`・`charmap.ser` は作らない(XML と UnicodeData.txt を読むのは
    JVM ごとに 1 回で、測って 0.2 秒と 0.1 秒)。`appendixc.ser`(MathML 3 の辞書、読むと 5 秒)は
    使われていない

