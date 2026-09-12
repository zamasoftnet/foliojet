# 約物の字面間隔上限 G1・G2 実装報告

2026-09-12 14:32 JST / Codex / TECH-20260911-011 / ユーザー直接依頼（座間ソフト製品）

## 状態

> **Claude 追記(2026-09-12 14:34)**: 未適用だった `docs/inkgap-tracker-test.patch` は Claude が `git apply` で適用した。標準 Gradle(Windows)で `net.zamasoft.foliojet.layout.text.spacing.*`・`jp.cssj.test.unit._0510_text_spacing.*`・`DisplayListGoldenTest` を実行し **43 件・失敗 0**。以下の「未適用」「未確認」の記述はこの追記で解消済み。

G1・G2の本体、新規単体試験、6段落のfixture、新規goldenを実装した。代替のjavac/JUnit検証は43件・失敗0、goldenは307文書（既存306＋新規1）が一致した。

**完了条件は未達。** 既存 `AutospaceTrackerTrimTest.java` のAPI追随修正が、対象フォルダーへの書き込み拒否により適用できていない。7呼出を `Direction` から既存の `style(Direction)` に変えるだけの修正を [inkgap-tracker-test.patch](inkgap-tracker-test.patch) に用意した。パッチは `git apply --check` 成功。43件の検証では、これを反映したローカルコピーを明示的にコンパイルしている。**未修正の作業ツリーが標準試験を通ったという意味ではない。** 書き込み可否の確認をユーザーに依頼済み。

また、WSLは `E_ACCESSDENIED` で起動できず、標準wrapperはGradle配布物の取得が拒否された。既存のGradle 8.9本体で起動してもcomposite build側のfoojayプラグインをoffline解決できなかった。このため指定されたGradleコマンドの成功は確認できていない。

対象は `F:\dev\CopperPDF\copper4\foliojet4`、ブランチ `main`、ローカル検証環境。コミット・push・デプロイ・公開・リリースはすべて未実施。

## 変更ファイル

| ファイル | 内容 |
|---|---|
| `src/main/java/net/zamasoft/foliojet/layout/text/spacing/JapaneseSpacingResolver.java` | `inkStart`、`inkEnd`、`inkGap`、`cappedPairTrim`。`applyRunTrims`を接続 |
| `src/main/java/net/zamasoft/foliojet/layout/text/spacing/AutospaceTracker.java` | `trimBefore`の引数をFontStyleへ変更、字面上限の共有関数へ接続 |
| `src/main/java/net/zamasoft/foliojet/layout/builder/impl/TextBuilder.java` | 分割対象runのmetrics/styleで逆適用。追い込み第4・5・6段階の共有予算。trimBefore呼出追随 |
| `src/main/java/net/zamasoft/foliojet/layout/builder/impl/TotalFitSession.java` | trimBefore呼出追随 |
| `src/main/java/net/zamasoft/foliojet/layout/builder/impl/TwoPassBlockBuilder.java` | trimBefore呼出追随。IntrinsicMeasurerへ上限済み詰め量を渡す |
| `src/test/java/jp/cssj/test/unit/_0510_text_spacing/InkGapTestSupport.java` | 実フォントと制御metrics、既存内部API観測用の試験補助 |
| 同ディレクトリ `InkGapResolverTest.java` | 字面計算8試験 |
| 同ディレクトリ `InkGapBuilderTest.java` | 共有予算・逆適用・pretty・固有寸法の8試験 |
| 同ディレクトリ `InkGapLayoutTest.java` | 実際のauto表セルとinline-blockの寸法検証1試験 |
| 同ディレクトリ `InkGapGoldenGeometryTest.java` | 新規goldenと同じfixtureのxadvance・改行位置を観測する1試験 |
| `src/test/java/jp/cssj/test/unit/displaylist/DisplayListGoldenTest.java` | 新規fixtureを一覧へ登録 |
| `files/unittest/0510-text-spacing/jlreq-shrink-vertical-colon.html` | 依頼された6段落。embeddedのPI、数値height、計算コメント |
| `files/unittest/0510-text-spacing/inkgap-intrinsic.html` | 縦・無枠・単一行のauto表セルとinline-block |
| `files/unittest/display-list-golden/0510-text-spacing_jlreq-shrink-vertical-colon/page-0001.txt` | 数値確認後に採用した新規golden1頁 |
| `docs/inkgap-tracker-test.patch` | 書き込み拒否で未適用の既存試験追随修正 |
| `docs/inkgap-stage-report.md` | 本報告 |

作業開始時に存在した `fonts.xml` の変更、`gothic-inkgap-test.ttf`、`footnote-columns-handoff.md`、`footnote-fixed-height-product.patch`、`test-endurance512.sh`、`tmp/` は変更していない。pdfg2d・copperpdf4・zstreamは変更していない。pdfg2dとcopperpdf4のgit statusは開始時・終了時とも変更なし。

検証補助はgit管理外の `local/inkgap-verification/` に置いた。既存の `tmp/` は使用していない。既存依存クラスとjarを参照し、変更クラスだけを同ディレクトリの `classes/` にコンパイルした。フォント資産は複製せず、元の登録順と設定を保つ作業用XMLから、ソースの資産と `build/testconf` の取得済みNotoを参照する。元の試験用書体・fonts.xmlには書いていない。

## 各項目の対応

| 項目 | 対応・検証 |
|---|---|
| G1 字面の座標 | FontMetricsImplが保持する同じShapedFontを使用。sourceがTBなら縦原点＋Y、それ以外はplacement＋X。font-size換算済み。小数精度を保持 |
| 合成書体 | W500〜900のstroke幅をFontUtilsと同じ表で計算し、両端を半幅ずつ拡張。合成斜体は直交軸最大絶対値×0.25で保守側に拡張 |
| 測定不能 | AJ17・空字形でNaN。pairTrimは名目値、追い込みの残予算は無限大へ戻す |
| 符号付き間隔 | はみ出しを片側ごとに切らず、2字を合算後に0で切る。YOz相当の負の後ろ空き、負のgapの試験あり |
| 純関数 | pairTrimのペン間距離は前advanceのみ。letter-spacing・kerning・xadvanceを含めない。追加・独自run・分割時再計算が一致 |
| G2 3呼出 | AutospaceTracker、boundaryAdjustment、applyRunTrimsを接続。GPOS非0の同一runを除く既存規則を保持 |
| 追い込み | 既存の名目容量控除を保持。`advance(prev)+letterSpacing(prev)-kerning(同一runのみ)+existing` でRを算出し、第4→5→6の順で消費。名目容量すべて0ならboundsを読まない |
| 共有予算 | 10ptでC4=2.5pt、C6=1.25pt、R=3ptからS4=2.5pt、S6=0.5pt。apply=falseでxadvance・lineAxis・pendingEndHang不変。容量不足でも不変。実適用の詰めは合計3pt |
| 第4・5段階 | C4=2.5pt、C5=5pt、R=3ptからS4=2.5pt、S5=0.5pt。既存1pt詰め後はR=2ptをそのまま使用し、二重控除しない。字面間隔消費後は追加容量0 |
| 送りの式 | 前runのletter-spacingを使い、同一runだけkerningを引く。異なるrunに大きな後runの字間を設定しても上限が変わらない試験あり |
| 行末 | endTrim・addJlreqLineEndShrinkPoints・ぶら下げは変更なし |
| 分割 | builderの現在metricsに別のGPOS値を与えても、分割対象のrunから−3.5ptを再計算しtail先頭が0へ戻る |
| IntrinsicMeasurer | 実試験書体、10pt、字間0.5ptでmax-content=17.5pt。実配置のauto表セル・inline-blockの内側高さも17.5pt |
| TotalFitSession | 横組み・prettyのtryBeginが非null。制御字面で上限3.5ptが発動し、候補幅17.5ptとTextBuilderへの実再生後advance17.5ptが一致 |
| 新規golden | 6段落、1頁。後述の実測値を確認して採用 |
| 既存試験追随 | AutospaceTrackerTrimTestの7呼出だけ未適用。ローカルコピーでは6試験すべて成功 |

容量不足試験は既存 `LayoutUtils.compare` の0.5pt未満の許容差も超える量を用いる。この許容差と行採否の仕組みは変更していない。実適用量は登録された字面予算を超えない。

## golden初回生成で確認した数値

通常のdisplay-list goldenはTextの文字列と配置座標だけを出し、run内のxadvanceを出さない。このため、goldenそのものを読み、同一fixtureの `DisplayListDumper.observePages` でTextを観測する `InkGapGoldenGeometryTest` も実行した。goldenの書式は変更していない。以下は用紙マージンの変換前のdisplay-list座標、単位pt。

| 段落 | golden／同一表示リストの実測 | 判断 |
|---|---|---|
| (1) IPAP 体；め仮、高さ42 | `体；め`: (290,0)、advance34.680、xadvance=[0,0,0]。`仮`: (272,0)、advance12 | 新容量4.08では不足4.68を救えず、18pt隣の行へ送られた。失敗した追い込みによる部分変更なし |
| (2) Noto 体・め仮、高さ42 | (242,0)、advance42、xadvance=[0,−3,−3,0] | 前後四分を各3pt使い、全4字が同じ行に収まった |
| (3) 試験書体 ；（、高さ24 | (194,0)、advance19.800、xadvance=[0,−4.200] | pairTrimが0.35emで上限。括弧のペンは7.8pt、字面前端は7.8+4.2=12pt |
| (4) 試験書体 体；（め、高さ24 | `体；`: (146,0)、advance24、xadvance=[0,0]。`（め`: (128,0)、advance24、xadvance=[0,0] | 同一runの折返し後、tail先頭xadvanceは0。字面前端は行頭から4.2pt（0.35em）。分割前の−4.2ptは(3)と単体の追加／split試験でも確認 |
| (5) 前IPAP12pt、後試験書体10pt | `；`: (98,0)、advance12。`（`: (102,12)、advance5.936、xadvance=[−4.064] | gap=12+3.5−11.436=4.064pt。合計advance17.936pt |
| (6) IPAP 体；め仮、高さ40 | `体；め`: (50,0)、advance34.680、xadvance=[0,0,0]。`仮`: (32,0) | 旧容量6ptでも不足6.68を救えない対照。部分変更なし |

## 実行コマンドと結果

### 標準実行の制約

```powershell
wsl --exec true
./gradlew.bat test --tests 'net.zamasoft.foliojet.layout.text.spacing.*' --offline
./gradlew.bat test --tests 'net.zamasoft.foliojet.layout.text.spacing.*' --tests 'jp.cssj.test.unit._0510_text_spacing.*' --tests jp.cssj.test.unit.displaylist.DisplayListGoldenTest --offline
```

- WSL: 起動失敗、`Wsl/Service/CreateInstance/E_ACCESSDENIED`。
- wrapperの2実行: Gradle 8.9配布取得時の `SocketException: Permission denied: getsockopt`。試験0件（試験開始前の環境エラー）。offlineはwrapper自体の配布取得を抑止しない。
- 配布済みGradle 8.9の `bin/gradle.bat test --tests 'net.zamasoft.foliojet.layout.text.spacing.*' --offline --no-daemon --project-cache-dir build/inkgap-gradle-cache` も実行した。pdfg2d/settings.gradleの `org.gradle.toolchains.foojay-resolver-convention:0.9.0` がoffline解決できず試験0件。pdfg2dの設定は変更していない。

### javac/JUnitによる代替確認

Java 21.0.7、JUnit 4.13.2。`verify.ps1` は変更ソースのjavacコンパイル後、指定したJUnitクラスを実行する。既存の依存プロジェクトのbuild/classes・resources・foliojet4の依存jarを利用するため、標準composite buildの代用である。

```powershell
./local/inkgap-verification/verify.ps1 -UsePendingTrackerPatch -Tests @(
  'net.zamasoft.foliojet.layout.text.spacing.JapaneseSpacingResolverTest',
  'net.zamasoft.foliojet.layout.text.spacing.TextAutospaceClassesTest',
  'net.zamasoft.foliojet.layout.text.spacing.EndAllowanceTest',
  'net.zamasoft.foliojet.layout.text.spacing.AutospaceTrackerTrimTest',
  'jp.cssj.test.unit._0510_text_spacing.InkGapResolverTest',
  'jp.cssj.test.unit._0510_text_spacing.InkGapBuilderTest',
  'jp.cssj.test.unit._0510_text_spacing.InkGapLayoutTest',
  'jp.cssj.test.unit._0510_text_spacing.InkGapGoldenGeometryTest',
  'jp.cssj.test.unit.displaylist.DisplayListGoldenTest'
)
```

**43件、失敗0、19.76秒。** 既存spacing24件（うち未適用パッチのローカルコピー6件）、新規18件、golden一覧1件。golden一覧は307文書を検査し、範囲／セルリースの収支検査も成功した。ログ: `local/inkgap-verification/final-tests.log`。

未修正の既存 `AutospaceTrackerTrimTest.java` だけをjavacで新APIに対してコンパイルすると、7か所で `Direction cannot be converted to FontStyle` となることも確認した（`local/inkgap-verification/unpatched-tracker-compile.log`）。このコンパイルエラーは未適用パッチの残件そのものである。

途中確認は、Resolver7件中1失敗（試験の負gap入力の誤りを訂正）、Resolver＋Builder13件中1失敗（既存0.5pt許容差を超えない不足量を試験が指定していたため訂正）、実配置を含む14件中1失敗（作業用フォント設定2回、および表のフォントサイズ継承1回。設定の参照先とfixtureを訂正）を経た。標準のfonts.xml・試験用書体は変更していない。

golden初回を含む実行は15件中1失敗で、失敗内容は新規goldenの初回生成通知だけ。既存goldenの不一致は0。数値観測を追加した16件は失敗0。共有予算の追加2試験を加え、最終43件を実行した。

## 既存goldenの差分一覧と判断

**差分0件（既存306文書）。更新なし。** 新規fixtureだけ1頁を追加した。フォント登録・Noto資産を揃えた代替実行での結果であり、WSL標準試験の結果ではない。今回の一覧で既存出力が変わる文書は見つからなかった。Claudeが採否を判断すべき既存goldenの変更はない。

## 設計との差・未実施と理由

- 実装の計算式は第5版に従った。依頼文と設計書の計算上の矛盾はない。
- 新規のG1単体も、書き込み可能な `jp/cssj/test/unit/_0510_text_spacing/` に配置した。指定された複合Gradleフィルターに含まれる。
- **既存AutospaceTrackerTrimTestの7呼出追随が未適用。** フォルダーへの書き込み拒否。新規ファイル作成も同じ場所では拒否された。不要な互換オーバーロードを本体に追加して回避することはしていない。パッチ適用後、標準試験を実行する必要がある。
- **指定Gradleコマンドの成功確認は未実施。** 上記のWSL・wrapper・offlineプラグイン解決の環境制約。成功と報告できるのはローカル補助環境での43件だけ。
- D7のmanifest/digests再生成・採用、TwoPassDigestParityTest実行、fuzz、実物のPDF／画像目視、3層ゲート、性能試験はG3担当Claudeへ引き継ぐ。`TwoPassDigestParityTest.corpusDocuments()` はgolden一覧とunittest HTMLツリーを列挙するので、新規2fixtureは次回のmanifest候補に入る。台帳は変更していない。
- 開発部門の `F:\AGENTS\座間ソフト\開発\TASKS.md`／`HANDOFF.md` は書き込み許可範囲外のため更新していない。本報告をTECH-20260911-011の引継ぎ記録として置いた。
- 記録確認: `git diff --check` と `git apply --check docs/inkgap-tracker-test.patch` は成功。`python F:\AGENTS\tools\check_text.py docs/inkgap-stage-report.md` と `py -3` の両方を試したが、Store版Pythonの起動がログオンセッションエラーで拒否された。同ツールのC1制御文字検査をPowerShellで行い、報告書に該当文字がないことを確認した。
- ロールバックする場合は、この報告に列挙した自分の差分／新規ファイルのみを対象にする。開始時からのfonts.xml等の変更を戻さない。

## 要約

字面間隔によるpairTrim上限と追い込みの共有予算を実装し、6段落の改行・xadvance、固有寸法17.5pt、prettyの候補／再生一致を確認した。新規goldenは数値検証済み、既存goldenの差分はない。残るG1・G2の作業は、書き込み拒否で適用できなかった既存試験の7呼出修正と、利用可能な環境での標準Gradle試験である。
