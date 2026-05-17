package net.zamasoft.pdfg2d.sac.parser;

/**
 * @author MIYABE Tatsuhiko
 * @version $Id: ChangeCharacterEncodingException.java,v 1.1 2005/05/17 04:18:23
 *          harumanx Exp $
 */
public class ChangeCharacterEncodingException extends Exception {
	private static final long serialVersionUID = 0;

	private final String encoding;

	public ChangeCharacterEncodingException(String encoding) {
		this.encoding = encoding;
	}

	public String getCharacterEncoding() {
		return this.encoding;
	}
}