package net.zamasoft.foliojet.css.parser;

import java.util.List;

import net.zamasoft.foliojet.css.selector.Selector;

/**
 * スタイルシートの解析イベントを受け取るハンドラです。
 * 旧SAC DocumentHandlerの置き換えで、Parser(ph-cssブリッジ)から呼び出されます。
 */
public interface StyleSheetHandler {
	public void startDocument(InputSource source);

	public void endDocument(InputSource source);

	public void startSelector(List<Selector> selectors);

	public void endSelector(List<Selector> selectors);

	public void property(String name, LexicalUnit value, boolean important);

	/**
	 * @param href       インポート先
	 * @param mediaTypes 空白区切りのメディアタイプ(無条件の場合は空文字列)
	 */
	public void importStyle(String href, String mediaTypes);

	public void startMedia(List<String> mediaTypes);

	public void endMedia();

	public void startPage(String name, String pseudoPage);

	public void endPage(String name, String pseudoPage);

	public void startFontFace();

	public void endFontFace();
}
