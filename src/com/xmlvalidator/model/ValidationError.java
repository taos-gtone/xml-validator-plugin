package com.xmlvalidator.model;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 검증 오류 정보를 담는 모델 클래스
 */
public class ValidationError {
	
	/**
	 * 오류 유형
	 */
	public enum ErrorType {
		SYNTAX("문법 오류"),
		CONSISTENCY("정합성 오류"),
		WARNING("경고");
		
		private String description;
		
		ErrorType(String description) {
			this.description = description;
		}
		
		public String getDescription() {
			return description;
		}
	}
	
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	
	private File file;
	private int lineNumber;
	private int columnNumber;
	private String message;
	private ErrorType errorType;
	private String elementPath;
	private LocalDateTime timestamp;
	
	public ValidationError(File file, int lineNumber, int columnNumber, String message) {
		this(file, lineNumber, columnNumber, message, ErrorType.SYNTAX, null);
	}
	
	public ValidationError(File file, int lineNumber, int columnNumber, String message, ErrorType errorType) {
		this(file, lineNumber, columnNumber, message, errorType, null);
	}
	
	public ValidationError(File file, int lineNumber, int columnNumber, String message, 
			ErrorType errorType, String elementPath) {
		this.file = file;
		this.lineNumber = lineNumber;
		this.columnNumber = columnNumber;
		this.message = message;
		this.errorType = errorType;
		this.elementPath = elementPath;
		this.timestamp = LocalDateTime.now();
	}
	
	public File getFile() {
		return file;
	}
	
	public int getLineNumber() {
		return lineNumber;
	}
	
	public int getColumnNumber() {
		return columnNumber;
	}
	
	public String getMessage() {
		return message;
	}
	
	public ErrorType getErrorType() {
		return errorType;
	}
	
	public String getElementPath() {
		return elementPath;
	}
	
	public LocalDateTime getTimestamp() {
		return timestamp;
	}
	
	public String getFormattedTimestamp() {
		if (timestamp != null) {
			return timestamp.format(TIME_FORMATTER);
		}
		return "";
	}
	
	@Override
	public String toString() {
		String location = lineNumber > 0 
				? String.format("Line %d, Column %d", lineNumber, columnNumber)
				: "Unknown location";
		String typeStr = errorType != null ? "[" + errorType.getDescription() + "] " : "";
		return String.format("%s [%s]: %s%s", file.getName(), location, typeStr, message);
	}
}
