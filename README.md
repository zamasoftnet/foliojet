# FolioJet

**A streaming HTML/CSS-to-PDF layout engine for print, with CSS Paged Media and
JLREQ Japanese typesetting.** Apache-2.0, Java 21, no browser required.

FolioJet is the layout engine behind Copper PDF, a server-side document
converter that Japanese companies and publishers have used in production to
print invoices, statements, tickets, catalogs and books. This fourth-generation
engine was rewritten and open-sourced in 2026.

## What it does

- **Paged layout.** CSS Paged Media: page boxes and margin boxes, page-break
  control, running headers and footers, footnotes, page floats and multi-column.
- **Japanese typesetting to JLREQ.** Vertical writing, kinsoku line-breaking
  driven by the JLREQ character-class tables, ruby (mono, group and jukugo),
  warichu, tate-chu-yoko, line-adjustment priority, and font fallback with
  kerning across CID-keyed and OpenType fonts.
- **Streaming.** Pages are laid out and emitted while the document is still
  arriving, so a long report generated from a slow cursor starts printing after
  the first page and runs in bounded memory.
- **Several output formats** from one layout: PDF, PNG/JPEG, a single SVG, and
  page-split SVG.

## What it is not

- **Not a browser.** JavaScript is not executed, by design. `<noscript>` content
  is rendered.
- **Not a general-purpose renderer.** The target is print. CSS that only affects
  interactive presentation is accepted and ignored rather than emulated.

## How it is tested

- 1,693 unit tests, plus display-list golden comparisons.
- A randomized document sweep whose generator, oracle and automatic shrinker all
  live in this repository (`RandomDocumentFuzzTest`, `FuzzOraclePredicateTest`,
  `FuzzShrinker`). Every defect it has found is committed as a minimal
  fixed-seed regression under [`files/fuzz-repro/`](./files/fuzz-repro).
- Hard invariants the suite exists to protect: never hang, never drop content,
  never emit an unintended blank page.

## Build

Java 21 and Gradle. FolioJet uses a Gradle composite build and expects four
sibling repositories to be checked out next to it; see the Japanese section
below for the exact layout, or run:

```sh
git clone https://github.com/zamasoftnet/cti.java.git
git clone https://github.com/zamasoftnet/html-balancer.git
git clone https://github.com/zamasoftnet/pdfg2d.git
git clone https://github.com/zamasoftnet/zstream.git
git clone https://github.com/zamasoftnet/foliojet.git
cd foliojet
./gradlew build
```

## License

[Apache License 2.0](./LICENSE)

---

以下は日本語の説明です。

# FolioJet

HTML/CSS などのページング処理を担う公開ライブラリです。

Gradle ベースの単一プロジェクト構成です。ビルドは `./gradlew build` を使います。

## ビルドに必要なリポジトリ

このリポジトリは Gradle の composite build で次の兄弟ディレクトリを参照します。

- `../../../zamasoftnet-public/cti.java` - https://github.com/zamasoftnet/cti.java
- `../../../zamasoftnet-public/html-balancer` - https://github.com/zamasoftnet/html-balancer
- `../../../zamasoftnet-public/pdfg2d` - https://github.com/zamasoftnet/pdfg2d
- `../../../zamasoftnet-public/zstream` - https://github.com/zamasoftnet/zstream

例:

```sh
git clone https://github.com/zamasoftnet/cti.java.git
git clone https://github.com/zamasoftnet/html-balancer.git
git clone https://github.com/zamasoftnet/pdfg2d.git
git clone https://github.com/zamasoftnet/zstream.git
git clone https://github.com/zamasoftnet/foliojet.git
cd foliojet
./gradlew build
```

`pdfg2d` の Maven group は現時点では `io.github.mimidesunya` のため、`build.gradle` の `io.github.mimidesunya:pdfg2d-*` 依存はそのままにしています。ローカル開発時は `settings.gradle` の composite build により `../../../zamasoftnet-public/pdfg2d` が使われます。

## 主な依存ライブラリ

依存バージョンは `build.gradle` の `dependencyVersions` にまとめています。主な依存は次のとおりです。

- CTI Java (`cti-if`, `cti-driver-rest`, `cti-server-rest`) 2.2.3
- pdfg2d (`pdfg2d-core`, `pdfg2d-font`, `pdfg2d-pdf`, `pdfg2d-svg`) 1.2.0
- zstream (`zstream-io`, `zstream-resolver`) 1.0.0-SNAPSHOT
- Apache Batik (`batik-svggen`, `batik-script`, `batik-ext`, `batik-bridge`, `batik-gvt`, `batik-anim`) 1.14
- Apache HttpComponents (`httpclient`, `httpcore`, `httpcore-nio`, `httpmime`)
- Apache Commons (`commons-collections`, `commons-io`, `commons-primitives`)
- XML/CSS (`xml-apis`, `xercesImpl`, `htmlunit-cssparser`, `html-balancer`)
- 画像・メタデータ (`metadata-extractor`, `imageio-jpeg`)
- その他 (`barcode4j`, `jeuclid-core`, `avalon-framework-impl`, `lib/Qrcode.jar`)

Maven の `pom.xml` と Eclipse のプロジェクトメタデータには依存しません。

## ライセンス

[Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)
（全文はリポジトリの [LICENSE](./LICENSE)）
