package net.zamasoft.foliojet.impl.formatter.document;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTIMessageCodes;
import net.zamasoft.foliojet.formatter.Formatter;
import net.zamasoft.foliojet.message.MessageCodeUtils;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.xml.Parser;
import net.zamasoft.foliojet.xml.ParserFactory;
import net.zamasoft.foliojet.xml.XMLHandler;
import net.zamasoft.foliojet.plugin.PluginRegistry;
import net.zamasoft.zstream.resolver.Source;

import org.xml.sax.SAXException;

public class DocumentFormatter implements Formatter {
	private static final Logger LOG = Logger.getLogger(DocumentFormatter.class.getName());

	public boolean match(Source key) {
		return true;
	}

	public int priority() {
		return -1000;
	}

	public void format(Source source, UserAgent ua) throws AbortException, TranscoderException {
		try {
			String mimeType = source.getMimeType();
			ParserFactory pf = (ParserFactory) PluginRegistry.getInstance().search(ParserFactory.class, mimeType);
			Parser parser = pf.createParser();
			XMLHandler entryPoint = new TranscoderHandler(ua);
			parser.parse(ua, source, entryPoint);
		} catch (IOException e) {
			short code = CTIMessageCodes.ERROR_IO;
			String[] args = new String[] { e.getMessage() };
			String mes = MessageCodeUtils.toString(code, args);
			ua.message(code, args);
			LOG.log(Level.WARNING, mes, e);
			throw new TranscoderException(code, args, mes);
		} catch (SAXException e) {
			short code = MessageCodes.ERROR_BAD_XML_SYNTAX;
			String[] args = new String[] { e.getMessage() };
			String mes = MessageCodeUtils.toString(code, args);
			ua.message(code, args);
			LOG.log(Level.WARNING, mes, e);
			throw new TranscoderException(code, args, mes);
		}
	}
}
