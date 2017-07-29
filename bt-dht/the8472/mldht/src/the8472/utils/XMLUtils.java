/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class XMLUtils {
	
	public static XPathExpression buildXPath(String path) {
		return buildXPath(path, null);
	}

	public static XPathExpression buildXPath(String path, Map<String, String> map) {
		XPathFactory xPathfactory = XPathFactory.newInstance();
		XPath xpath = xPathfactory.newXPath();
		if(map != null)
			xpath.setNamespaceContext(new NamespaceContext() {

				public Iterator getPrefixes(String namespaceURI) {
					throw new UnsupportedOperationException();
				}

				public String getPrefix(String namespaceURI) {
					throw new UnsupportedOperationException();
				}

				public String getNamespaceURI(String prefix) {
					Objects.requireNonNull(prefix);
					if(map.containsKey(prefix))
						return map.get(prefix);
					return XMLConstants.NULL_NS_URI;
				}
			});

		try {
			return xpath.compile(path);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
	}
}
