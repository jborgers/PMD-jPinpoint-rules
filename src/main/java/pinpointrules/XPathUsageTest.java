package nl.rabobank.perf.pinpointrules;

import java.io.IOException;

import org.apache.xpath.XPathAPI;
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import javax.xml.parsers.*;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.*;
//import org.apache.commons.jxpath.*;


public class XPathUsageTest {

	public static void main(String[] args) throws ParserConfigurationException,
			SAXException, IOException, XPathExpressionException {

		DocumentBuilderFactory domFactory = DocumentBuilderFactory
				.newInstance();
		domFactory.setNamespaceAware(true); // never forget this!
		DocumentBuilder builder = domFactory.newDocumentBuilder();
		Document doc = builder.parse("books.xml");

		XPathFactory factory = XPathFactory.newInstance();
		XPath xpath = factory.newXPath();
		XPathExpression expr = xpath
				.compile("//book[author='Isaac Asimov']/title/text()");

		Object result = expr.evaluate(doc, XPathConstants.NODESET);
		NodeList nodes = (NodeList) result;
		for (int i = 0; i < nodes.getLength(); i++) {
			System.out.println(nodes.item(i).getNodeValue());
		}
	}

	public String getAction() {
		try {
			Node doc = null;
			return XPathAPI.eval(doc, "/Envelope/Header/Action").toString();
		} catch (TransformerException e) {
			//logger.debug("", e);
			return null;
		}
	}
}
