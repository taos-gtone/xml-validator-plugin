package com.xmlvalidator.validators;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.xmlvalidator.model.ValidationError;
import com.xmlvalidator.util.YamlRuleParser;

/**
 * XML 파일의 정합성 규칙을 체크하는 클래스
 * YAML 규칙 파일을 기반으로 검증합니다.
 */
public class ConsistencyValidator {
	
	private YamlRuleParser ruleParser;
	private List<ValidationError> errors;
	private String xmlEncoding;
	private Map<String, Integer> elementLineNumbers;  // 요소별 라인 번호 저장
	
	public ConsistencyValidator(YamlRuleParser ruleParser) {
		this.ruleParser = ruleParser;
		this.errors = new ArrayList<>();
		this.elementLineNumbers = new HashMap<>();
	}
	
	/**
	 * XML 파일의 정합성을 체크합니다.
	 * @param xmlFile 체크할 XML 파일
	 * @return 정합성 오류가 없으면 true, 있으면 false
	 */
	public boolean validate(File xmlFile) {
		errors.clear();
		elementLineNumbers.clear();
		
		System.out.println("정합성 검증 시작: " + xmlFile.getName());
		
		try {
			// 인코딩 처리
			String encoding = ruleParser.getEncoding();
			System.out.println("인코딩: " + encoding);
			
			// 먼저 라인 번호 매핑 생성
			buildLineNumberMap(xmlFile, encoding);
			
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			
			Document doc;
			if (encoding != null && !encoding.isEmpty()) {
				try (FileInputStream fis = new FileInputStream(xmlFile)) {
					org.xml.sax.InputSource is = new org.xml.sax.InputSource(fis);
					is.setEncoding(encoding);
					doc = builder.parse(is);
				}
			} else {
				doc = builder.parse(xmlFile);
			}
			
			// 루트 요소 검증
			Element root = doc.getDocumentElement();
			if (root == null) {
				errors.add(new ValidationError(xmlFile, 1, -1, "XML 파일에 루트 요소가 없습니다."));
				return false;
			}
			
			System.out.println("루트 요소: " + root.getNodeName());
			
			// STR 규칙 가져오기
			@SuppressWarnings("unchecked")
			Map<String, Object> strRule = (Map<String, Object>) ruleParser.getRules().get("STR");
			System.out.println("STR 규칙 존재 여부: " + (strRule != null));
			if (strRule != null) {
				System.out.println("STR 규칙 키: " + strRule.keySet());
				validateElement(xmlFile, root, strRule, "STR");
			} else {
				System.out.println("경고: STR 규칙이 없습니다!");
			}
			
			System.out.println("검증 완료. 오류 수: " + errors.size());
			return errors.isEmpty();
			
		} catch (Exception e) {
			System.err.println("정합성 검사 예외: " + e.getMessage());
			e.printStackTrace();
			errors.add(new ValidationError(xmlFile, 1, -1, 
					"정합성 검사 오류: " + e.getMessage()));
			return false;
		}
	}
	
	/**
	 * XML 파일에서 요소별 라인 번호를 매핑합니다.
	 */
	private void buildLineNumberMap(File xmlFile, String encoding) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(xmlFile), 
						encoding != null ? encoding : "UTF-8"))) {
			
			String line;
			int lineNumber = 0;
			Pattern elementPattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_]*)(?:\\s|>|/)");
			Map<String, Integer> elementCounts = new HashMap<>();
			
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				Matcher matcher = elementPattern.matcher(line);
				
				while (matcher.find()) {
					String elementName = matcher.group(1);
					// 요소 이름과 출현 순서를 키로 사용
					int count = elementCounts.getOrDefault(elementName, 0) + 1;
					elementCounts.put(elementName, count);
					String key = elementName + "#" + count;
					elementLineNumbers.put(key, lineNumber);
					
					// 단순 요소 이름으로도 첫 번째 출현 저장
					if (count == 1) {
						elementLineNumbers.put(elementName, lineNumber);
					}
				}
			}
		} catch (Exception e) {
			System.err.println("라인 번호 매핑 오류: " + e.getMessage());
		}
	}
	
	/**
	 * 요소 이름으로 라인 번호를 찾습니다.
	 */
	private int getLineNumber(String elementName) {
		Integer lineNum = elementLineNumbers.get(elementName);
		return lineNum != null ? lineNum : -1;
	}
	
	/**
	 * 요소를 검증합니다.
	 */
	@SuppressWarnings("unchecked")
	private void validateElement(File xmlFile, Element element, Map<String, Object> rule, String path) {
		// 1. 속성 검증
		Map<String, Object> attributes = (Map<String, Object>) rule.get("attributes");
		if (attributes != null) {
			validateAttributes(xmlFile, element, attributes, path);
		}
		
		// 2. 필수 여부 검증
		String required = (String) rule.get("required");
		String occurrence = (String) rule.get("occurrence");
		
		// 3. 데이터 타입 검증
		String dataType = (String) rule.get("data_type");
		if (dataType != null && !dataType.isEmpty()) {
			validateDataType(xmlFile, element, dataType, path);
		}
		
		// 4. 포맷 검증
		String format = (String) rule.get("format");
		if (format != null && !format.isEmpty()) {
			validateFormat(xmlFile, element, format, path);
		}
		
		// 5. 허용 코드 검증
		List<String> allowedCodes = (List<String>) rule.get("allowed_codes");
		if (allowedCodes != null && !allowedCodes.isEmpty()) {
			validateAllowedCodes(xmlFile, element, allowedCodes, path);
		}
		
		// 6. 자식 요소 검증
		Map<String, Object> children = (Map<String, Object>) rule.get("children");
		if (children != null) {
			validateChildren(xmlFile, element, children, path);
		}
	}
	
	/**
	 * 속성을 검증합니다.
	 */
	@SuppressWarnings("unchecked")
	private void validateAttributes(File xmlFile, Element element, Map<String, Object> attributes, String path) {
		int lineNum = getLineNumber(element.getNodeName());
		
		for (Map.Entry<String, Object> entry : attributes.entrySet()) {
			String attrName = entry.getKey();
			Object attrRule = entry.getValue();
			
			String attrValue = element.getAttribute(attrName);
			
			if (attrRule instanceof Map) {
				Map<String, Object> attrRuleMap = (Map<String, Object>) attrRule;
				
				// enum 타입 검증
				String type = (String) attrRuleMap.get("type");
				if ("enum".equals(type)) {
					List<String> allowedValues = (List<String>) attrRuleMap.get("allowed_values");
					if (allowedValues != null && !allowedValues.isEmpty()) {
						if (!attrValue.isEmpty() && !allowedValues.contains(attrValue)) {
							errors.add(new ValidationError(xmlFile, lineNum, -1,
									path + " 요소의 " + attrName + " 속성 값 '" + attrValue + 
									"'이(가) 허용된 값이 아닙니다. 허용값: " + allowedValues));
						}
					}
				}
			} else if (attrRule instanceof String) {
				// 고정 값 검증
				String expectedValue = (String) attrRule;
				if (!expectedValue.isEmpty() && !attrValue.equals(expectedValue)) {
					errors.add(new ValidationError(xmlFile, lineNum, -1,
							path + " 요소의 " + attrName + " 속성 값이 '" + expectedValue + 
							"'이어야 합니다. 현재 값: '" + attrValue + "'"));
				}
			}
		}
	}
	
	/**
	 * 데이터 타입을 검증합니다.
	 */
	private void validateDataType(File xmlFile, Element element, String dataType, String path) {
		int lineNum = getLineNumber(element.getNodeName());
		String value = element.getTextContent();
		if (value == null) {
			value = "";
		}
		value = value.trim();
		
		// 빈 값은 필수 검증에서 처리
		if (value.isEmpty()) {
			return;
		}
		
		// numeric 타입 검증
		if (dataType.startsWith("numeric")) {
			try {
				// numeric(n) 또는 numeric(n,m) 형식
				if (dataType.contains("(")) {
					String params = dataType.substring(dataType.indexOf("(") + 1, dataType.indexOf(")"));
					String[] parts = params.split(",");
					int maxLength = Integer.parseInt(parts[0].trim());
					
					// 숫자 형식 검증
					if (!value.matches("-?\\d+(\\.\\d+)?")) {
						errors.add(new ValidationError(xmlFile, lineNum, -1,
								path + " 요소의 값 '" + value + "'이(가) 숫자 형식이 아닙니다."));
					}
				}
			} catch (Exception e) {
				// 파싱 오류 무시
			}
		}
		
		// 길이 검증 (숫자만 있는 경우 최대 길이)
		if (dataType.matches("\\d+")) {
			int maxLength = Integer.parseInt(dataType);
			if (value.length() > maxLength) {
				errors.add(new ValidationError(xmlFile, lineNum, -1,
						path + " 요소의 값 길이가 " + maxLength + "자를 초과합니다. 현재 길이: " + value.length()));
			}
		}
	}
	
	/**
	 * 포맷을 검증합니다.
	 */
	private void validateFormat(File xmlFile, Element element, String format, String path) {
		int lineNum = getLineNumber(element.getNodeName());
		String value = element.getTextContent();
		if (value == null || value.trim().isEmpty()) {
			return;
		}
		value = value.trim();
		
		// YYYYMMDD 형식 검증
		if (format.contains("YYYYMMDD")) {
			if (!value.matches("\\d{8}")) {
				errors.add(new ValidationError(xmlFile, lineNum, -1,
						path + " 요소의 값 '" + value + "'이(가) YYYYMMDD 형식이 아닙니다."));
			} else {
				// 날짜 유효성 검증
				try {
					int year = Integer.parseInt(value.substring(0, 4));
					int month = Integer.parseInt(value.substring(4, 6));
					int day = Integer.parseInt(value.substring(6, 8));
					
					if (year < 1900 || month < 1 || month > 12 || day < 1 || day > 31) {
						errors.add(new ValidationError(xmlFile, lineNum, -1,
								path + " 요소의 날짜 값 '" + value + "'이(가) 유효하지 않습니다."));
					}
				} catch (NumberFormatException e) {
					// 이미 위에서 검증됨
				}
			}
		}
		
		// HHMISS 형식 검증
		if (format.contains("HHMISS")) {
			if (!value.matches("\\d{6}")) {
				errors.add(new ValidationError(xmlFile, lineNum, -1,
						path + " 요소의 값 '" + value + "'이(가) HHMISS 형식이 아닙니다."));
			} else {
				try {
					int hour = Integer.parseInt(value.substring(0, 2));
					int minute = Integer.parseInt(value.substring(2, 4));
					int second = Integer.parseInt(value.substring(4, 6));
					
					if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 59) {
						errors.add(new ValidationError(xmlFile, lineNum, -1,
								path + " 요소의 시간 값 '" + value + "'이(가) 유효하지 않습니다."));
					}
				} catch (NumberFormatException e) {
					// 이미 위에서 검증됨
				}
			}
		}
		
		// 금칙어 체크
		if (format.contains("금칙어")) {
			if (value.contains("<") || value.contains(">") || 
				value.contains("\"") || value.contains(";")) {
				errors.add(new ValidationError(xmlFile, lineNum, -1,
						path + " 요소의 값에 금칙어(<, >, \", ;)가 포함되어 있습니다."));
			}
		}
		
		// 1~5 범위 검증
		if (format.contains("1에서5사이")) {
			try {
				int num = Integer.parseInt(value);
				if (num < 1 || num > 5) {
					errors.add(new ValidationError(xmlFile, lineNum, -1,
							path + " 요소의 값 '" + value + "'이(가) 1~5 범위를 벗어났습니다."));
				}
			} catch (NumberFormatException e) {
				errors.add(new ValidationError(xmlFile, lineNum, -1,
						path + " 요소의 값 '" + value + "'이(가) 숫자가 아닙니다."));
			}
		}
	}
	
	/**
	 * 허용 코드를 검증합니다.
	 */
	private void validateAllowedCodes(File xmlFile, Element element, List<String> allowedCodes, String path) {
		int lineNum = getLineNumber(element.getNodeName());
		// 속성에서 Code 값 확인
		String codeValue = element.getAttribute("Code");
		if (codeValue != null && !codeValue.isEmpty()) {
			if (!allowedCodes.contains(codeValue)) {
				errors.add(new ValidationError(xmlFile, lineNum, -1,
						path + " 요소의 Code 속성 값 '" + codeValue + "'이(가) 허용된 코드가 아닙니다."));
			}
		}
	}
	
	/**
	 * 자식 요소들을 검증합니다.
	 */
	@SuppressWarnings("unchecked")
	private void validateChildren(File xmlFile, Element parent, Map<String, Object> childrenRules, String parentPath) {
		for (Map.Entry<String, Object> entry : childrenRules.entrySet()) {
			String childName = entry.getKey();
			Map<String, Object> childRule = (Map<String, Object>) entry.getValue();
			
			// 자식 요소 찾기
			List<Element> childElements = getChildElements(parent, childName);
			
			// occurrence 검증
			String occurrence = (String) childRule.get("occurrence");
			String required = (String) childRule.get("required");
			
			validateOccurrence(xmlFile, childElements, childName, occurrence, required, parentPath);
			
			// 각 자식 요소 검증
			for (Element child : childElements) {
				validateElement(xmlFile, child, childRule, parentPath + "/" + childName);
			}
		}
	}
	
	/**
	 * 발생 횟수를 검증합니다.
	 */
	private void validateOccurrence(File xmlFile, List<Element> elements, String elementName, 
			String occurrence, String required, String parentPath) {
		int count = elements.size();
		int lineNum = getLineNumber(elementName);
		// 부모 요소의 라인 번호 사용 (자식이 없는 경우)
		if (lineNum < 0) {
			String parentName = parentPath.contains("/") ? 
					parentPath.substring(parentPath.lastIndexOf("/") + 1) : parentPath;
			lineNum = getLineNumber(parentName);
		}
		
		if (occurrence == null) {
			occurrence = "1";
		}
		
		// 필수 여부 확인
		boolean isRequired = "required".equalsIgnoreCase(required) || 
							 (required != null && required.contains("required"));
		
		if (occurrence.equals("1")) {
			// 정확히 1개
			if (count == 0 && isRequired) {
				errors.add(new ValidationError(xmlFile, lineNum, -1,
						parentPath + " 요소에 필수 자식 요소 '" + elementName + "'이(가) 없습니다."));
			} else if (count > 1) {
				errors.add(new ValidationError(xmlFile, lineNum, -1,
						parentPath + " 요소에 '" + elementName + "' 요소가 1개만 있어야 하지만 " + count + "개가 있습니다."));
			}
		} else if (occurrence.equals("0..1")) {
			// 0개 또는 1개
			if (count > 1) {
				errors.add(new ValidationError(xmlFile, lineNum, -1,
						parentPath + " 요소에 '" + elementName + "' 요소가 최대 1개만 있어야 하지만 " + count + "개가 있습니다."));
			}
		} else if (occurrence.equals("1..n")) {
			// 1개 이상
			if (count == 0) {
				errors.add(new ValidationError(xmlFile, lineNum, -1,
						parentPath + " 요소에 '" + elementName + "' 요소가 최소 1개 이상 있어야 합니다."));
			}
		} else if (occurrence.equals("0..n")) {
			// 0개 이상 - 항상 유효
		}
	}
	
	/**
	 * 부모 요소에서 특정 이름의 자식 요소들을 찾습니다.
	 */
	private List<Element> getChildElements(Element parent, String childName) {
		List<Element> children = new ArrayList<>();
		NodeList nodeList = parent.getChildNodes();
		
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			if (node instanceof Element) {
				Element elem = (Element) node;
				String localName = elem.getLocalName();
				if (localName == null) {
					localName = elem.getNodeName();
				}
				// 네임스페이스 프리픽스 제거
				if (localName.contains(":")) {
					localName = localName.substring(localName.indexOf(":") + 1);
				}
				if (localName.equals(childName)) {
					children.add(elem);
				}
			}
		}
		
		return children;
	}
	
	/**
	 * 검증 결과 오류 목록을 반환합니다.
	 */
	public List<ValidationError> getErrors() {
		return new ArrayList<>(errors);
	}
}
