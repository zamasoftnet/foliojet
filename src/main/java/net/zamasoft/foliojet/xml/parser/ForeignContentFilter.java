package net.zamasoft.foliojet.xml.parser;

import org.htmlunit.cyberneko.filters.DefaultFilter;
import org.htmlunit.cyberneko.xerces.xni.Augmentations;
import org.htmlunit.cyberneko.xerces.xni.QName;
import org.htmlunit.cyberneko.xerces.xni.XMLAttributes;
import org.htmlunit.cyberneko.xerces.xni.XNIException;

import net.zamasoft.balancer.ElementProps;
import net.zamasoft.balancer.TagBalancer;
import net.zamasoft.foliojet.ua.CompatibleMode;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.vocab.Foreign;

/**
 * HTML(と Markdown の中の HTML)の要素の名前空間を整えるフィルタです。2026-10-04 まで HTMLParser と MarkdownParser に
 * 同じ無名クラスの写しがあった。
 *
 * <ul>
 * <li><b>HTML5の foreign content</b>: {@code <math>}/{@code <svg>}とその子孫に HTML5 の名前空間を与える。
 * HTMLでは{@code xmlns}を書かないのが普通で、HTML5 はこれらを構文解析の段階で正しい名前空間へ入れる(ブラウザは
 * 全部そうする)。NekoHTML はそこまでやらないのでここで補う——やらないと MathML が平らな文字列になり、しかも
 * {@code <annotation>}の中の生の LaTeX まで一緒に出る。arXiv が今 HTML を出している形(ar5iv/LaTeXML)がまさに
 * これで、{@code h_{t}}が「htsubscript … h_{t}」と出ていた(2026-08-05、実地コーパス第11波)。
 * <b>簡略化している点</b>: HTML5 が定める復帰点({@code <foreignObject>}や
 * {@code <annotation-xml encoding="text/html">}の内側は HTML へ戻る)は見ていない。深さだけで数える。印刷用途
 * では、その内側に HTML を書き戻す文書が実地でほぼ無いため。</li>
 * <li>{@code input.html.change-default-namespace} が偽なら、接頭辞の無い要素の(foreign でない)名前空間を外す。</li>
 * <li>最初の{@code <body>}で、標準モードなら要素の性質を{@code html4.xml}へ切り替える。</li>
 * </ul>
 */
class ForeignContentFilter extends DefaultFilter {
	private final UserAgent ua;
	private final TagBalancer balancer;
	private final boolean changeDefaultNamespace;
	private boolean firstElement = true;

	/** foreign content の名前空間と入れ子の深さ(0なら外)。 */
	private String foreignURI = null;
	private int foreignDepth = 0;

	ForeignContentFilter(final UserAgent ua, final TagBalancer balancer, final boolean changeDefaultNamespace) {
		this.ua = ua;
		this.balancer = balancer;
		this.changeDefaultNamespace = changeDefaultNamespace;
	}

	private void applyForeign(final QName element) {
		if (this.foreignDepth == 0) {
			if (element.getUri() == null) {
				final String uri = Foreign.uriOf(element.getLocalpart());
				if (uri == null) {
					return;
				}
				this.foreignURI = uri;
				element.setUri(uri);
			} else if (Foreign.is(element.getUri())) {
				// xmlns が書いてある場合。NekoHTMLが既に付けている
				this.foreignURI = element.getUri();
			} else {
				return;
			}
		} else if (element.getUri() == null) {
			element.setUri(this.foreignURI);
		}
		++this.foreignDepth;
	}

	/** 接頭辞の無い要素から、foreign でない既定の名前空間を外します。 */
	private void stripDefaultNamespace(final QName element) {
		if (!this.changeDefaultNamespace && !Foreign.is(element.getUri()) && element.getUri() != null
				&& (element.getPrefix() == null || element.getPrefix().length() == 0)) {
			element.setUri(null);
		}
	}

	@Override
	public void startElement(final QName element, final XMLAttributes attributes, final Augmentations augs)
			throws XNIException {
		this.applyForeign(element);
		this.stripDefaultNamespace(element);
		super.startElement(element, attributes, augs);
		if (this.firstElement && element.getLocalpart().equalsIgnoreCase("body")) {
			// 標準モードへの切り替え
			if (this.ua.getDocumentContext().getCompatibleMode() == CompatibleMode.STRICT) {
				this.balancer.setElementProps(ElementProps.getElementProps("html4.xml"));
			}
			this.firstElement = false;
		}
	}

	@Override
	public void endElement(final QName element, final Augmentations augs) throws XNIException {
		if (this.foreignDepth > 0) {
			if (element.getUri() == null) {
				element.setUri(this.foreignURI);
			}
			if (--this.foreignDepth == 0) {
				this.foreignURI = null;
			}
		}
		this.stripDefaultNamespace(element);
		super.endElement(element, augs);
	}

	@Override
	public void emptyElement(final QName element, final XMLAttributes attributes, final Augmentations augs)
			throws XNIException {
		// 空要素は開いてすぐ閉じる。foreign の深さは増減させない
		final int depth = this.foreignDepth;
		final String uri = this.foreignURI;
		this.applyForeign(element);
		this.foreignDepth = depth;
		this.foreignURI = uri;
		this.stripDefaultNamespace(element);
		super.emptyElement(element, attributes, augs);
	}
}
