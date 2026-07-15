package net.zamasoft.foliojet.xml;

import java.io.IOException;

import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.zstream.resolver.Source;

import org.xml.sax.SAXException;

/**
 * パーサーのインターフェースです。
 *
 * @author MIYABE Tatsuhiko
 */
public interface Parser {
	/**
	 * ドキュメントを解析してSAXイベントを生成します。
	 * 位置情報は setDocumentLocator に {@link SourceLocator} を渡すことで通知します。
	 *
	 * @param ua
	 * @param source
	 * @param xmlHandler
	 * @throws SAXException
	 * @throws IOException
	 */
	public void parse(UserAgent ua, Source source, XMLHandler xmlHandler) throws SAXException, IOException;
}
