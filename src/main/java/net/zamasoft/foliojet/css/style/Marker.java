package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.style.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.style.box.impl.InlineReplacedBox;

/**
 * マーカーの出力情報です。
 * 
 * @author MIYABE Tatsuhiko
 */
class Marker {
	InlineBlockBox box = null;
	char[] text = null;
	InlineReplacedBox imageBox = null;
}
