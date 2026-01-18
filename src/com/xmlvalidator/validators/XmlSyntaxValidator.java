package com.xmlvalidator.validators;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.xmlvalidator.model.ValidationError;

/**
 * XML 파일의 기본 문법 체크를 수행하는 클래스
 */
public class XmlSyntaxValidator {
	
	private List<ValidationError> errors;
	private String encoding;
	
	public XmlSyntaxValidator() {
		this.errors = new ArrayList<>();
		this.encoding = null;
	}
	
	/**
	 * 인코딩을 설정합니다.
	 * @param encoding 인코딩 (예: "EUC-KR", "UTF-8")
	 */
	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}
	
	/**
	 * XML 파일의 문법을 체크합니다.
	 * @param xmlFile 체크할 XML 파일
	 * @return 문법 오류가 없으면 true, 있으면 false
	 */
	public boolean validate(File xmlFile) {
		errors.clear();
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(false);
		factory.setNamespaceAware(true);
		
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(new XmlErrorHandler(xmlFile));
			
			try (FileInputStream fis = new FileInputStream(xmlFile)) {
				if (encoding != null && !encoding.isEmpty()) {
					org.xml.sax.InputSource is = new org.xml.sax.InputSource(fis);
					is.setEncoding(encoding);
					builder.parse(is);
				} else {
					builder.parse(fis);
				}
			}
			
			return errors.isEmpty();
			
		} catch (ParserConfigurationException e) {
			errors.add(new ValidationError(xmlFile, -1, -1, 
					"파서 설정 오류: " + e.getMessage(),
					ValidationError.ErrorType.SYNTAX));
			return false;
		} catch (SAXException e) {
			// SAXException은 ErrorHandler에서 처리됨
			return false;
		} catch (IOException e) {
			errors.add(new ValidationError(xmlFile, -1, -1, 
					"파일 읽기 오류: " + e.getMessage(),
					ValidationError.ErrorType.SYNTAX));
			return false;
		}
	}
	
	/**
	 * 검증 결과 오류 목록을 반환합니다.
	 * @return 검증 오류 목록
	 */
	public List<ValidationError> getErrors() {
		return new ArrayList<>(errors);
	}
	
	/**
	 * XML 파싱 오류를 처리하는 ErrorHandler
	 */
	private class XmlErrorHandler implements ErrorHandler {
		private File xmlFile;
		
		public XmlErrorHandler(File xmlFile) {
			this.xmlFile = xmlFile;
		}
		
		@Override
		public void warning(SAXParseException exception) throws SAXException {
			errors.add(new ValidationError(xmlFile, 
					exception.getLineNumber(), 
					exception.getColumnNumber(),
					"경고: " + exception.getMessage(),
					ValidationError.ErrorType.WARNING));
		}
		
		@Override
		public void error(SAXParseException exception) throws SAXException {
			errors.add(new ValidationError(xmlFile, 
					exception.getLineNumber(), 
					exception.getColumnNumber(),
					"오류: " + exception.getMessage(),
					ValidationError.ErrorType.SYNTAX));
		}
		
		@Override
		public void fatalError(SAXParseException exception) throws SAXException {
			errors.add(new ValidationError(xmlFile, 
					exception.getLineNumber(), 
					exception.getColumnNumber(),
					"치명적 오류: " + exception.getMessage(),
					ValidationError.ErrorType.SYNTAX));
		}
	}
}
