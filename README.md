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
