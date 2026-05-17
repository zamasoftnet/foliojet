package jp.cssj.homare.css.style;

import jp.cssj.homare.style.box.impl.InlineBlockBox;
import jp.cssj.homare.style.box.impl.InlineReplacedBox;

/**
 * マーカーの出力情報です。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: Marker.java 1552 2018-04-26 01:43:24Z miyabe $
 */
class Marker {
	InlineBlockBox box = null;
	char[] text = null;
	InlineReplacedBox imageBox = null;
}
