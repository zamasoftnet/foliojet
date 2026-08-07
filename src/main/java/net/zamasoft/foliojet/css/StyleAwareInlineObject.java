package net.zamasoft.foliojet.css;

/**
 * ホスト文書側のスタイル文脈を受け取れるインラインオブジェクトです
 * (2026-08-07)。CSSProcessorはインラインオブジェクトの開始時に、その
 * 要素(SVGならsvgルート)の解決済み{@link CSSStyle}を渡す。
 * インラインSVGが著者CSSのvar()をそのSVGの位置のカスタムプロパティで
 * 解決するために使う({@link SVGAuthorCss#toCssText})。
 */
public interface StyleAwareInlineObject extends InlineObject {
	public void setHostStyle(CSSStyle style);
}
