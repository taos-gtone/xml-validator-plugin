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
		
		// 파일이 존재하는지 확인
		if (!xmlFile.exists()) {
			System.err.println("파일이 존재하지 않습니다: " + xmlFile.getAbsolutePath());
			errors.add(new ValidationError(xmlFile, -1, -1, 
					"파일이 존재하지 않습니다: " + xmlFile.getAbsolutePath(),
					ValidationError.ErrorType.SYNTAX));
			return false;
		}
		
		// 파일 정보 로깅 (최신 정보 확인)
		long fileSize = xmlFile.length();
		long lastModified = xmlFile.lastModified();
		System.out.println("문법 검증 시작: " + xmlFile.getName() + 
				" (크기: " + fileSize + " bytes, 수정 시간: " + lastModified + ")");
		
		// DocumentBuilderFactory를 매번 새로 생성하여 캐시 방지
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setValidating(false);
		factory.setNamespaceAware(true);
		
		try {
			DocumentBuilder builder = factory.newDocumentBuilder();
			builder.setErrorHandler(new XmlErrorHandler(xmlFile));
			
			// 항상 FileInputStream을 사용하여 최신 파일 내용을 읽음
			try (FileInputStream fis = new FileInputStream(xmlFile)) {
				if (encoding != null && !encoding.isEmpty()) {
					org.xml.sax.InputSource is = new org.xml.sax.InputSource(fis);
					is.setEncoding(encoding);
					// SystemId를 설정하여 파일 경로 명시
					is.setSystemId(xmlFile.toURI().toString());
					builder.parse(is);
				} else {
					org.xml.sax.InputSource is = new org.xml.sax.InputSource(fis);
					is.setSystemId(xmlFile.toURI().toString());
					builder.parse(is);
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
