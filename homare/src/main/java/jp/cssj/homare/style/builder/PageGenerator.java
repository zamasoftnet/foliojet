package jp.cssj.homare.style.builder;

import jp.cssj.homare.style.box.impl.PageBox;
import jp.cssj.homare.style.box.params.Types;
import jp.cssj.homare.ua.UserAgent;
import net.zamasoft.pdfg2d.gc.GraphicsException;

public interface PageGenerator {
	public static final byte NONE = Types.PAGE_BREAK_AUTO;
	public static final byte VERSO = Types.PAGE_BREAK_VERSO;
	public static final byte RECTO = Types.PAGE_BREAK_RECTO;
	
	public UserAgent getUserAgent();

	public byte getPageSide();

	public PageBox nextPage();

	public void drawPage(PageBox page) throws GraphicsException;
}
