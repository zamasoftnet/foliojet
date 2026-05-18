package net.zamasoft.foliojet.impl.ua.svg;

import java.awt.geom.Dimension2D;

import org.apache.batik.bridge.NoLoadScriptSecurity;
import org.apache.batik.bridge.ScriptSecurity;
import org.apache.batik.util.ParsedURL;

import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.pdfg2d.svg.SVGUserAgent;

class MyUserAgent extends SVGUserAgent {
	protected final String docURI;
	protected final UserAgent ua;

	public MyUserAgent(String docURI, UserAgent ua, Dimension2D viewport) {
		super(viewport);
		this.docURI = docURI;
		this.ua = ua;
		this.addStdFeatures();
	}

	public Dimension2D getViewportSize() {
		return this.viewport;
	}

	public void displayError(String message) {
		this.ua.message(MessageCodes.WARN_SVG, this.docURI, message);
	}

	public void displayError(Exception e) {
		this.ua.message(MessageCodes.WARN_SVG, this.docURI, e.getMessage());
	}

	public void displayMessage(String message) {
		this.ua.message(MessageCodes.WARN_SVG, this.docURI, message);
	}

	public float getPixelUnitToMillimeter() {
		return (float) (25.4 / this.ua.getPixelsPerInch());
	}

	public String getLanguages() {
		return this.ua.getDefaultLocale().getLanguage();
	}

	public String getMedia() {
		return "print";
	}

	public String getDefaultFontFamily() {
		return this.ua.getDefaultFontFamily().get(0).getName();
	}

	public ScriptSecurity getScriptSecurity(String scriptType, ParsedURL scriptPURL, ParsedURL docPURL) {
		return new NoLoadScriptSecurity(scriptType);
	}
}
