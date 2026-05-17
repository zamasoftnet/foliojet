package jp.cssj.homare.css;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import net.zamasoft.pdfg2d.sac.css.Selector;
import net.zamasoft.pdfg2d.sac.css.SelectorList;

/**
 * スタイルシートは、与えられた要素に対して適用される宣言を返します。
 * <p>
 * このクラスは、SAXイベントとして送られた文書に対して段階的にスタイルを適用することを意図しています。
 * 要素は必ずしもSAXイベントの要素とは一致せず、CSSの一部の擬似クラスにあるような文書の構成要素にも対応させることができます。
 * startElementに対応するendElementは必ず矛盾なく呼ばれる必要があります。
 * </p>
 * <p>
 * このクラスは要素に対応するスタイルを返すだけであり、スタイルの段階化は呼び出し側の方に(おそらくは表示処理に)委ねられます。
 * </p>
 * <p>
 * スタイルシートはスレッドセーフではありません。 そのため、複数のスレッドで使う場合には、スレッドごとにインスタンスを作成する必要があります。
 * 1回のパースで複数のスタイルシートのインスタンスを得る場合には、Templatesを利用します。
 * </p>
 * 
 * @see javax.xml.transform.Templates
 * @author MIYABE Tatsuhiko
 * @version $Id: CSSStyleSheet.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public class CSSStyleSheet implements Serializable, Cloneable {
	private static final long serialVersionUID = 0;

	final LinkedHashMap<String, Rule> selectorToRule = new LinkedHashMap<String, Rule>();

	/** ページの宣言。 */
	Declaration page, firstPage, leftPage, rightPage;

	/** ページごとに生成される内容 */
	final List<PageContent> pageContents = new ArrayList<PageContent>();

	public static class PageContent {
		public final String name, pseudoPage;
		public final Declaration decleration;

		PageContent(String name, String pseudoPage, Declaration decleration) {
			this.name = name;
			this.pseudoPage = pseudoPage;
			this.decleration = decleration;
		}
	}

	public Object clone() {
		CSSStyleSheet styleSheet = new CSSStyleSheet();
		for (Iterator<Entry<String, Rule>> i = this.selectorToRule.entrySet().iterator(); i.hasNext();) {
			Entry<String, Rule> entry = i.next();
			styleSheet.selectorToRule.put(entry.getKey(), (Rule) (entry.getValue()).clone());
		}
		if (this.page != null) {
			styleSheet.page = (Declaration) this.page.clone();
		}
		if (this.firstPage != null) {
			styleSheet.firstPage = (Declaration) this.firstPage.clone();
		}
		if (this.leftPage != null) {
			styleSheet.leftPage = (Declaration) this.leftPage.clone();
		}
		if (this.rightPage != null) {
			styleSheet.rightPage = (Declaration) this.rightPage.clone();
		}

		return styleSheet;
	}

	/**
	 * ルールを追加します。
	 * 
	 * @param selectors
	 * @param declaration
	 */
	public void addRule(SelectorList selectors, Declaration declaration) {
		if (declaration == null) {
			return;
		}
		for (int i = 0; i < selectors.getLength(); ++i) {// ループすることに注意！
			Selector selector = selectors.item(i);
			String selectorString = selector.toString();

			Rule rule = (Rule) this.selectorToRule.get(selectorString);
			if (rule == null) {
				rule = new Rule(selector, new Declaration(declaration));
				this.selectorToRule.put(selectorString, rule);
			} else {
				Declaration d = rule.getDeclaration();
				d.merge(declaration);
			}
		}
	}

	/**
	 * ページ宣言を追加します。
	 * 
	 * @param pseudoPage
	 * @param declaration
	 */
	public void addPage(String pseudoPage, Declaration declaration) {
		if (declaration == null) {
			return;
		}
		if (pseudoPage == null) {
			if (this.page == null) {
				this.page = new Declaration();
			}
			this.page.merge(declaration);
		} else if (pseudoPage.equals("first")) {
			if (this.firstPage == null) {
				this.firstPage = new Declaration();
			}
			this.firstPage.merge(declaration);
		} else if (pseudoPage.equals("left")) {
			if (this.leftPage == null) {
				this.leftPage = new Declaration();
			}
			this.leftPage.merge(declaration);
		} else if (pseudoPage.equals("right")) {
			if (this.rightPage == null) {
				this.rightPage = new Declaration();
			}
			this.rightPage.merge(declaration);
		}
	}

	/**
	 * ページ宣言を追加します。
	 * 
	 * @param pseudoPage
	 * @param declaration
	 */
	public void addPageContent(String name, String pseudoPage, Declaration declaration) {
		this.pageContents.add(new PageContent(name, pseudoPage, declaration));
	}
}
