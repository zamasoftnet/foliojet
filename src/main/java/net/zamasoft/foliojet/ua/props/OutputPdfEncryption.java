package net.zamasoft.foliojet.ua.props;

public interface OutputPdfEncryption {
	public static final short NONE = 1;
	public static final short V1 = 2;
	public static final short V2 = 3;
	public static final short V4 = 4;
	/** AES-256 (V5/R6, PDF 2.0). */
	public static final short V5 = 5;
}
