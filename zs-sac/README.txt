SAC 1.3 実装
バージョン @version@

Apache Batik 1.7のSAC 1.2 実装
org.apache.batik.css.parser
を改良し、名前空間やMicrosoft Internet Explorerとの互換性を追加したものです。
既存のBatikライブラリとの衝突を防ぐため、パッケージ名を変えています。

■ ライセンス

このソフトウェアはApache Software FoundationのApache Batik
http://xmlgraphics.apache.org/batik/
から派生したものであり、Batikの再配布条件に従います。

オリジナルのBatikのライセンスは、付属のLICENSE.batikをご参照ください。

■ 変更履歴
2009-06-09 v1.0.4
BOM(U+FEFF)を空白として認識するように修正
2013-02-21 v1.2.1
*をnmcharに含める（ブラウザとの互換性）