package jp.cssj.print.epub;

import java.util.ArrayList;
import java.util.List;

import jp.cssj.print.epub.Container.Rootfile;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

//container.xmlの読み込み
class ContainerHandler extends DefaultHandler {
	final List<Rootfile> list = new ArrayList<Rootfile>();

	public void startElement(String uri, String lName, String qName, Attributes atts) throws SAXException {
		if (!lName.equals("rootfile")) {
			return;
		}
		Rootfile rootfile = new Rootfile();
		rootfile.mediaType = atts.getValue("media-type");
		rootfile.fullPath = atts.getValue("full-path");
		this.list.add(rootfile);
	}

	public Container getContainer() {
		Container container = new Container();
		container.rootfiles = (Rootfile[]) this.list.toArray(new Rootfile[this.list.size()]);
		return container;
	}
}
