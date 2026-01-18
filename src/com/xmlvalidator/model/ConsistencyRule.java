package com.xmlvalidator.model;

/**
 * 정합성 규칙을 담는 모델 클래스
 */
public class ConsistencyRule {
	private String id;
	private String elementPath;
	private String attribute;
	private String condition;
	private String expectedValue;
	private String errorMessage;
	
	public ConsistencyRule(String id, String elementPath, String attribute, 
			String condition, String expectedValue, String errorMessage) {
		this.id = id;
		this.elementPath = elementPath;
		this.attribute = attribute;
		this.condition = condition;
		this.expectedValue = expectedValue;
		this.errorMessage = errorMessage;
	}
	
	public String getId() {
		return id;
	}
	
	public String getElementPath() {
		return elementPath;
	}
	
	public String getAttribute() {
		return attribute;
	}
	
	public String getCondition() {
		return condition;
	}
	
	public String getExpectedValue() {
		return expectedValue;
	}
	
	public String getErrorMessage() {
		return errorMessage;
	}
	
	@Override
	public String toString() {
		return String.format("Rule[%s]: %s@%s %s %s", 
				id, elementPath, attribute, condition, expectedValue);
	}
}
