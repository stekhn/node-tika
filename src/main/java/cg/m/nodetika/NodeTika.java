/*
 * Copyright (c) 2013, Matthew Caruana Galizia. Licensed under an MIT-style license.
 * You may obtain a copy of the License at
 *
 *		 http://mattcg.mit-license.org
 */

package cg.m.nodetika;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.File;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;

import java.net.URL;
import java.net.MalformedURLException;

import java.lang.Exception;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.html.HtmlParser;
import org.apache.tika.mime.MediaType;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.AutoDetectReader;
import org.apache.tika.language.LanguageIdentifier;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.EncryptedDocumentException;

import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.google.gson.Gson;

public class NodeTika {

	private static final TikaConfig config = TikaConfig.getDefaultConfig();

	private static TikaInputStream createInputStream(String uri) throws FileNotFoundException, MalformedURLException, IOException {
		InputStream inputStream;

		if (uri.startsWith("http://") || uri.startsWith("https://") || uri.startsWith("ftp://")) {
			inputStream = new URL(uri).openStream();
		} else {
			inputStream = new FileInputStream(uri);
		}

		return TikaInputStream.get(inputStream);
	}

	private static AutoDetectParser createParser() {
		final AutoDetectParser parser = new AutoDetectParser();

		Map<MediaType, Parser> parsers = parser.getParsers();
		parsers.put(MediaType.APPLICATION_XML, new HtmlParser());
		parser.setParsers(parsers);

		parser.setFallback(new Parser() {
			public Set<MediaType> getSupportedTypes(ParseContext parseContext) {
				return parser.getSupportedTypes(parseContext);
			}

			public void parse(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, ParseContext parseContext) throws TikaException {
				throw new TikaException("Unsupported Media Type");
			}
		});

		return parser;
	}

	private static void fillMetadata(AutoDetectParser parser, Metadata metadata, String contentType, String uri) {
		fillMetadata(metadata, contentType, uri);

		final Detector detector = parser.getDetector();

		parser.setDetector(new Detector() {
			public MediaType detect(InputStream inputStream, Metadata metadata) throws IOException {
				String contentType = metadata.get(HttpHeaders.CONTENT_TYPE);

				if (contentType != null) {
					return MediaType.parse(contentType);
				} else {
					return detector.detect(inputStream, metadata);
				}
			}
		});
	}

	private static void fillMetadata(Metadata metadata, String contentType, String uri) {

		// Set the file name.
		metadata.set(TikaMetadataKeys.RESOURCE_NAME_KEY, new File(uri).getName());

		// Normalise the content-type.
		if (contentType != null && "xml".equals(MediaType.parse(contentType).getSubtype())) {
			contentType = null;
		}

		if (contentType != null && contentType.equals(MediaType.OCTET_STREAM)) {
			contentType = null;
		}

		// Set the content-type.
		if (contentType != null) {
			metadata.add(HttpHeaders.CONTENT_TYPE, contentType);
		}
	}

	public static String extractText(String uri) throws Exception {
		return extractText(uri, null);
	}

	public static String extractText(String uri, String contentType) throws Exception {
		return extractText(uri, contentType, "UTF-8");
	}

	public static String extractText(String uri, String contentType, String outputEncoding) throws Exception {
		final AutoDetectParser parser = createParser();
		final Metadata metadata = new Metadata();
		final ParseContext context = new ParseContext();

		fillMetadata(parser, metadata, contentType, uri);

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		OutputStreamWriter writer = new OutputStreamWriter(outputStream, outputEncoding);
		BodyContentHandler body = new BodyContentHandler(new RichTextContentHandler(writer));

		TikaInputStream inputStream = createInputStream(uri);

		try {
			parser.parse(inputStream, body, metadata);
		} catch (SAXException e) {
			throw e;
		} catch (EncryptedDocumentException e) {
			throw e;
		} catch (TikaException e) {
			throw e;
		} finally {
			inputStream.close();
		}

		return outputStream.toString("UTF-8");
	}

	public static String extractMeta(String uri) throws Exception {
		return extractMeta(uri, null);
	}

	public static String extractMeta(String uri, String contentType) throws Exception {
		final AutoDetectParser parser = createParser();
		final Metadata metadata = new Metadata();

		fillMetadata(parser, metadata, contentType, uri);

		final TikaInputStream inputStream = createInputStream(uri);

		parser.parse(inputStream, new DefaultHandler(), metadata);

		Map meta = new HashMap();
		for (String name : metadata.names()) {
			String[] values = metadata.getValues(name);
			meta.put(name, values);
		}

		inputStream.close();

		return new Gson().toJson(meta);
	}

	public static String detectCharset(String uri) throws FileNotFoundException, IOException, TikaException {
		return detectCharset(uri, null);
	}

	public static String detectCharset(String uri, String contentType) throws FileNotFoundException, IOException, TikaException {
		final TikaInputStream inputStream = createInputStream(uri);
		final Metadata metadata = new Metadata();

		// Use metadata to provide type-hinting to the AutoDetectReader.
		fillMetadata(metadata, contentType, uri);

		// Detect the character set.
		final AutoDetectReader reader = new AutoDetectReader(inputStream, metadata);
		String charset = reader.getCharset().toString();

		inputStream.close();

		return charset;
	}

	public static String detectContentType(String uri) throws FileNotFoundException, IOException, TikaException {
		final Detector detector = config.getDetector();
		final TikaInputStream inputStream = createInputStream(uri);
		final Metadata metadata = new Metadata();

		// Set the file name. This provides some level of type-hinting.
		metadata.add(TikaMetadataKeys.RESOURCE_NAME_KEY, new File(uri).getName());

		// Detect the content type.
		String contentType = detector.detect(inputStream, metadata).toString();

		inputStream.close();

		// Return the default content-type if undetermined.
		if (contentType == null || contentType.isEmpty()) {
			return MediaType.OCTET_STREAM.toString();
		}

		return contentType;
	}

	public static String detectContentTypeAndCharset(String uri) throws FileNotFoundException, IOException, TikaException {
		final Detector detector = config.getDetector();
		final TikaInputStream inputStream = createInputStream(uri);
		final Metadata metadata = new Metadata();

		// Set the file name. This provides some level of type-hinting.
		metadata.add(TikaMetadataKeys.RESOURCE_NAME_KEY, new File(uri).getName());

		// Detect the content type.
		String contentType = detector.detect(inputStream, metadata).toString();

		// Use metadata to provide type-hinting to the AutoDetectReader.
		fillMetadata(metadata, contentType, uri);

		// Detect the character set.
		final AutoDetectReader reader = new AutoDetectReader(inputStream, metadata);
		String charset = reader.getCharset().toString();

		inputStream.close();

		// Return the default content-type if undetermined.
		if (contentType == null || contentType.isEmpty()) {
			return MediaType.OCTET_STREAM.toString();
		}

		// Append the charset if the content-type was determined.
		if (charset != null && !charset.isEmpty()) {
			return contentType + "; charset=" + charset;
		}

		return contentType;
	}

	public static String detectLanguage(String text) {
		LanguageIdentifier identifier = new LanguageIdentifier(text);
		Map language = new HashMap();

		language.put("language", identifier.getLanguage());
		language.put("reasonablyCertain", identifier.isReasonablyCertain());

		return new Gson().toJson(language);
	}
}
