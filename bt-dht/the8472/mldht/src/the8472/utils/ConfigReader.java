/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;



public class ConfigReader {
	
	public static class ParseException extends RuntimeException {
		public ParseException(Exception cause) {
			super(cause);
		}
	};
	
	FilesystemNotifications notifications;
	Path configFile;
	Schema schema;
	Supplier<InputStream> defaults;
	Document current;
	
	public ConfigReader(Path toRead, Supplier<InputStream> defaults, Supplier<InputStream> schemaSource) {
		configFile = toRead;
		this.defaults = defaults;
		
		try {
			buildSchema(schemaSource.get());
		} catch (SAXException e1) {
			throw new RuntimeException(e1);
		}
		
		if(!Files.exists(configFile)) {
			try {
				Files.copy(defaults.get(), configFile);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
			
	}
	
	private void buildSchema(InputStream source) throws SAXException {
		// create a SchemaFactory capable of understanding WXS schemas
		SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);

		// load a WXS schema, represented by a Schema instance
		Source schemaFile = new StreamSource(source);
		schema = schemaFactory.newSchema(schemaFile);
	}
	
	List<Runnable> callbacks = new ArrayList<>();
	
	public void addChangeCallback(Runnable callback) {
		callbacks.add(callback);
	}
	
	public void registerFsNotifications(FilesystemNotifications notifier) {
		notifications = notifier;
		notifier.addRegistration(configFile, (path, kind) -> {
			if(path.equals(configFile)) {
				current = readFile(configFile);
				callbacks.forEach(Runnable::run);
			}
		});
		
	}
	
	
	private void write(Path source) {
		/*
		JSONObject obj = new JSONObject(toWrite);
		try {
			Files.write(configFile, obj.toJSONString().getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		*/
	}
	
	public Document read() {
		if(current == null)
			readConfig();
		return current;
	}
	
	void readConfig() {
		current = readFile(configFile);
		
	}
	
	public Optional<String> get(XPathExpression expr) {
		Node result;
		try {
			result = (Node) expr.evaluate(current, XPathConstants.NODE);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
		
		if(result == null)
			return Optional.empty();
		
		return Optional.of(result.getTextContent());
	}
	
	public Optional<Boolean> getBoolean(String path) {
		return get(XMLUtils.buildXPath(path)).map(str -> str.equals("true") || str.equals("1"));
	}
	
	public Optional<Long> getLong(String path) {
		return get(XMLUtils.buildXPath(path)).map(Long::valueOf);
	}
	
	public Stream<String> getAll(XPathExpression path) {
		

		NodeList result;
		try {
			result = (NodeList) path.evaluate(current, XPathConstants.NODESET);
		} catch (XPathExpressionException e) {
			throw new RuntimeException(e);
		}
		
		if(result == null)
			return Stream.empty();
		
		
		
		return IntStream.range(0, result.getLength()).mapToObj(result::item).map(Node::getTextContent);
	}
	
	
	Document readFile(Path p) {
		Document document = null;
		try {
			// parse an XML document into a DOM tree
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			DocumentBuilder parser = factory.newDocumentBuilder();
			document = parser.parse(p.toFile());



			// create a Validator instance, which can be used to validate an instance document
			Validator validator = schema.newValidator();

			// validate the DOM tree

			validator.validate(new DOMSource(document));
		} catch (SAXException | IOException | ParserConfigurationException e) {
			throw new ParseException(e);
		}

		return document;
	}

	

}
