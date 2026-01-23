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
	private Map<String, Integer> elementLineNumbers;  // 요소별 라인 번호 저장 (요소이름#순번 -> 라인번호)
	
	// 무한 루프 방지를 위한 최대 재귀 깊이 제한
	private static final int MAX_RECURSION_DEPTH = 100;
	// 현재 검증 중인 요소 추적 (무한 루프 방지)
	private java.util.Set<Element> visitedElements;
	
	// 성능 최적화: 파일 라인 캐시 (한 번만 읽음)
	private List<String> cachedFileLines;
	private File cachedFile;
	
	// 요소별 방문 카운터 (같은 이름의 요소가 여러 개 있을 때 순서대로 라인 번호 매칭)
	private Map<String, Integer> elementVisitCounter;
	
	public ConsistencyValidator(YamlRuleParser ruleParser) {
		this.ruleParser = ruleParser;
		this.errors = new ArrayList<>();
		this.elementLineNumbers = new HashMap<>();
		this.visitedElements = new java.util.HashSet<>();
		this.cachedFileLines = null;
		this.cachedFile = null;
		this.elementVisitCounter = new HashMap<>();
	}
	
	/**
	 * XML 파일의 정합성을 체크합니다.
	 * @param xmlFile 체크할 XML 파일
	 * @return 정합성 오류가 없으면 true, 있으면 false
	 */
	public boolean validate(File xmlFile) {
		errors.clear();
		elementLineNumbers.clear();
		visitedElements.clear();
		elementVisitCounter.clear();
		cachedFileLines = null;
		cachedFile = null;
		
		// ruleParser가 null인지 확인
		if (ruleParser == null) {
			System.err.println("규칙 파서가 초기화되지 않았습니다.");
			addError(xmlFile, -1, -1, "규칙 파서가 초기화되지 않았습니다.");
			return false;
		}
		
		// 파일이 존재하는지 확인
		if (!xmlFile.exists()) {
			System.err.println("파일이 존재하지 않습니다: " + xmlFile.getAbsolutePath());
			addError(xmlFile, -1, -1, "파일이 존재하지 않습니다: " + xmlFile.getAbsolutePath());
			return false;
		}
		
		try {
			// 인코딩 처리
			String encoding = ruleParser.getEncoding();
			
			// 먼저 라인 번호 매핑 생성 (항상 최신 파일에서 읽음)
			buildLineNumberMap(xmlFile, encoding);
			
			// DocumentBuilderFactory를 매번 새로 생성하여 캐시 방지
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			// 캐시 방지를 위한 추가 설정
			factory.setValidating(false);
			// 엔티티 해석 비활성화 (캐시 방지)
			factory.setExpandEntityReferences(false);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			
			// 항상 FileInputStream을 사용하여 최신 파일 내용을 읽음
			// 파일 수정 시간을 확인하여 항상 최신 파일을 읽도록 보장
			Document doc;
			if (encoding != null && !encoding.isEmpty()) {
				try (FileInputStream fis = new FileInputStream(xmlFile)) {
					org.xml.sax.InputSource is = new org.xml.sax.InputSource(fis);
					is.setEncoding(encoding);
					// SystemId를 설정하여 파일 경로 명시 (캐시 방지)
					is.setSystemId(xmlFile.toURI().toString());
					// 파일을 항상 새로 읽기 위해 캐시를 사용하지 않음
					doc = builder.parse(is);
				}
			} else {
				// 인코딩이 없어도 FileInputStream을 사용하여 최신 내용 보장
				try (FileInputStream fis = new FileInputStream(xmlFile)) {
					org.xml.sax.InputSource is = new org.xml.sax.InputSource(fis);
					is.setSystemId(xmlFile.toURI().toString());
					// 파일을 항상 새로 읽기 위해 캐시를 사용하지 않음
					doc = builder.parse(is);
				}
			}
			
			// 루트 요소 검증
			Element root = doc.getDocumentElement();
			if (root == null) {
				addError(xmlFile, 1, -1, "XML 파일에 루트 요소가 없습니다.");
				return false;
			}
			
			// 루트 요소 이름으로 규칙 가져오기 (STR이 아닌 다른 루트 요소도 지원)
			String rootElementName = root.getLocalName();
			if (rootElementName == null) {
				rootElementName = root.getNodeName();
				if (rootElementName.contains(":")) {
					rootElementName = rootElementName.substring(rootElementName.indexOf(":") + 1);
				}
			}
			
			@SuppressWarnings("unchecked")
			Map<String, Object> rootRule = (Map<String, Object>) ruleParser.getRules().get(rootElementName);
			if (rootRule != null) {
				validateElement(xmlFile, root, rootRule, rootElementName);
			} else {
				addError(xmlFile, 1, -1, "루트 요소 '" + rootElementName + "'에 대한 규칙이 정의되지 않았습니다.");
			}
			
			return errors.isEmpty();
			
		} catch (Exception e) {
			addError(xmlFile, 1, -1, "정합성 검사 오류: " + e.getMessage());
			return false;
		}
	}
	
	/**
	 * XML 파일 내용을 캐시합니다 (성능 최적화).
	 * 항상 최신 파일을 읽기 위해 캐시를 사용하지 않습니다.
	 */
	private void cacheFileLines(File xmlFile, String encoding) {
		// 항상 최신 파일을 읽기 위해 캐시를 무시하고 새로 읽음
		cachedFileLines = new ArrayList<>();
		cachedFile = xmlFile;
		
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(xmlFile), 
						encoding != null ? encoding : "UTF-8"))) {
			
			String line;
			int lineCount = 0;
			while ((line = reader.readLine()) != null && lineCount < MAX_LINES_TO_READ) {
				cachedFileLines.add(line);
				lineCount++;
			}
			
		} catch (Exception e) {
			cachedFileLines = new ArrayList<>();
		}
	}
	
	/**
	 * XML 파일에서 요소별 라인 번호를 매핑합니다.
	 */
	private void buildLineNumberMap(File xmlFile, String encoding) {
		// 먼저 파일 캐시
		cacheFileLines(xmlFile, encoding);
		
		Pattern elementPattern = Pattern.compile("<([a-zA-Z][a-zA-Z0-9_]*)(?:\\s|>|/)");
		Map<String, Integer> elementCounts = new HashMap<>();
		
		for (int i = 0; i < cachedFileLines.size(); i++) {
			String line = cachedFileLines.get(i);
			int lineNumber = i + 1;
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
	}
	
	/**
	 * 요소 이름으로 라인 번호를 찾습니다.
	 * 같은 이름의 요소가 여러 개 있을 때는 첫 번째 요소의 라인 번호를 반환합니다.
	 */
	private int getLineNumber(String elementName) {
		Integer lineNum = elementLineNumbers.get(elementName);
		return lineNum != null ? lineNum : -1;
	}
	
	// 파일 읽기 시 최대 라인 수 제한 (무한 루프 방지)
	private static final int MAX_LINES_TO_READ = 100000;
	
	/**
	 * 요소의 실제 라인 번호를 찾습니다 (성능 최적화 버전).
	 * 캐시된 라인을 사용하여 파일을 다시 읽지 않습니다.
	 * 각 요소의 방문 순서를 추적하여 정확한 라인 번호를 반환합니다.
	 */
	private int findElementLineNumber(File xmlFile, Element element, String path, String encoding) {
		// 요소 이름 추출
		String elementName = element.getLocalName();
		if (elementName == null) {
			elementName = element.getNodeName();
			if (elementName.contains(":")) {
				elementName = elementName.substring(elementName.indexOf(":") + 1);
			}
		}
		
		// 캐시된 라인이 없으면 기본 라인 번호 반환
		if (cachedFileLines == null || cachedFileLines.isEmpty()) {
			return getLineNumber(elementName);
		}
		
		// 방문 카운터 증가 및 순번 기반 라인 번호 조회
		int visitCount = elementVisitCounter.getOrDefault(elementName, 0) + 1;
		elementVisitCounter.put(elementName, visitCount);
		
		// 요소이름#순번 형식으로 정확한 라인 번호 조회
		String key = elementName + "#" + visitCount;
		Integer lineNum = elementLineNumbers.get(key);
		if (lineNum != null && lineNum > 0) {
			return lineNum;
		}
		
		// 찾지 못한 경우 첫 번째 출현 위치 사용
		lineNum = elementLineNumbers.get(elementName);
		if (lineNum != null && lineNum > 0) {
			return lineNum;
		}
		
		// 그래도 찾지 못하면 기본값 1 반환
		return 1;
	}
	
	/**
	 * 속성의 라인 번호를 찾습니다 (성능 최적화 버전).
	 * 요소의 라인 번호를 반환합니다 (속성은 같은 라인에 있다고 가정).
	 */
	private int findAttributeLineNumber(File xmlFile, Element element, String path, String attrName, String encoding) {
		// 요소의 라인 번호를 그대로 사용 (성능 최적화)
		int elementLineNum = findElementLineNumber(xmlFile, element, path, encoding);
		if (elementLineNum < 0) {
			elementLineNum = 1;
		}
		return elementLineNum;
	}
	
	/**
	 * 요소를 검증합니다.
	 */
	@SuppressWarnings("unchecked")
	private void validateElement(File xmlFile, Element element, Map<String, Object> rule, String path) {
		validateElement(xmlFile, element, rule, path, 0);
	}
	
	/**
	 * 요소를 검증합니다 (재귀 깊이 추적 포함).
	 */
	@SuppressWarnings("unchecked")
	private void validateElement(File xmlFile, Element element, Map<String, Object> rule, String path, int depth) {
		// 무한 루프 방지: 최대 재귀 깊이 체크
		if (depth > MAX_RECURSION_DEPTH) {
			addError(xmlFile, 1, -1, "정합성 검사 오류: 최대 검증 깊이를 초과했습니다. 경로: " + path);
			return;
		}
		
		// 무한 루프 방지: 이미 방문한 요소인지 체크
		if (visitedElements.contains(element)) {
			return;
		}
		visitedElements.add(element);
		
		// 인코딩 가져오기
		String encoding = null;
		try {
			if (ruleParser != null) {
				encoding = ruleParser.getEncoding();
			}
		} catch (Exception e) {
			// 무시
		}
		if (encoding == null || encoding.isEmpty()) {
			encoding = "UTF-8";
		}
		
		// 1. 속성 검증
		@SuppressWarnings("unchecked")
		Map<String, Object> attributes = (Map<String, Object>) rule.get("attributes");
		if (attributes != null) {
			validateAttributes(xmlFile, element, attributes, path);
		}
		
		// 2. 요소 텍스트 값 필수 여부 검증
		String required = (String) rule.get("required");
		String dataType = (String) rule.get("data_type");
		boolean isRequired = "required".equalsIgnoreCase(required) || 
							 (required != null && required.contains("required"));
		
		// 요소의 직접 텍스트 값 가져오기 (자식 요소의 텍스트 제외)
		String textValue = getDirectTextContent(element);
		
		// 필수 요소인데 텍스트 값이 비어있으면 오류
		if (isRequired && (textValue == null || textValue.trim().isEmpty())) {
			// 자식 요소가 없는 경우에만 텍스트 값 필수 검증 (leaf 노드)
			// 자식 요소가 있는 경우는 자식 요소들이 필수인지 별도로 검증
			Map<String, Object> children = (Map<String, Object>) rule.get("children");
			if (children == null || children.isEmpty()) {
				int lineNum = findElementLineNumber(xmlFile, element, path, encoding);
				if (lineNum < 0) lineNum = 1;
				addError(xmlFile, lineNum, -1,
						path + " 요소의 값은 필수입니다.");
			}
		}
		
		// 3. 데이터 타입 검증 (최대 길이 등)
		if (dataType != null && !dataType.isEmpty()) {
			validateDataType(xmlFile, element, dataType, path, encoding);
		}
		
		// 4. 포맷 검증
		String format = (String) rule.get("format");
		if (format != null && !format.isEmpty()) {
			validateFormat(xmlFile, element, format, path, encoding);
		}
		
		// 5. 허용 코드 검증
		List<String> allowedCodes = (List<String>) rule.get("allowed_codes");
		if (allowedCodes != null && !allowedCodes.isEmpty()) {
			validateAllowedCodes(xmlFile, element, allowedCodes, path, encoding);
		}
		
		// 6. 자식 요소 검증
		Map<String, Object> childrenRule = (Map<String, Object>) rule.get("children");
		if (childrenRule != null) {
			validateChildren(xmlFile, element, childrenRule, path, depth);
		}
	}
	
	/**
	 * 속성을 검증합니다.
	 */
	@SuppressWarnings("unchecked")
	private void validateAttributes(File xmlFile, Element element, Map<String, Object> attributes, String path) {
		// 요소의 실제 라인 번호 찾기
		String encoding = null;
		try {
			if (ruleParser != null) {
				encoding = ruleParser.getEncoding();
			}
		} catch (Exception e) {
			// 무시
		}
		if (encoding == null || encoding.isEmpty()) {
			encoding = "UTF-8";
		}
		int baseLineNum = findElementLineNumber(xmlFile, element, path, encoding);
		
		for (Map.Entry<String, Object> entry : attributes.entrySet()) {
			String attrName = entry.getKey();
			Object attrRule = entry.getValue();
			
			// 라인 번호
			int lineNum = baseLineNum > 0 ? baseLineNum : 1;
			
			// 속성 값 읽기 - 따옴표 안의 모든 값을 정확히 읽기 위해 getAttribute() 우선 사용
			// ZipCode="419679"의 경우 정확히 6자리를 모두 읽어야 함
			String attrValue = null;
			
			// 방법 1: getAttribute() 먼저 시도 (가장 안정적이고 정확함)
			// getAttribute()는 XML 속성 값을 정규화하지 않고 원본 그대로 반환
			if (element.hasAttribute(attrName)) {
				attrValue = element.getAttribute(attrName);
			}
			
			// 방법 2: 네임스페이스 처리
			if (attrValue == null || attrValue.isEmpty()) {
				String localName = attrName;
				if (localName.contains(":")) {
					localName = localName.substring(localName.indexOf(":") + 1);
				}
				if (element.hasAttributeNS(null, localName)) {
					attrValue = element.getAttributeNS(null, localName);
				}
			}
			
			// 방법 3: NamedNodeMap을 통한 직접 접근 (fallback)
			// getNodeValue()도 원본 값을 반환하지만 getAttribute()가 더 안정적
			if (attrValue == null || attrValue.isEmpty()) {
				NamedNodeMap attrs = element.getAttributes();
				for (int i = 0; i < attrs.getLength(); i++) {
					Node attr = attrs.item(i);
					String attrNodeName = attr.getNodeName();
					String attrLocalName = attr.getLocalName();
					
					if (attrNodeName.equals(attrName)) {
						attrValue = attr.getNodeValue();
						break;
					} else if (attrLocalName != null && attrLocalName.equals(attrName)) {
						attrValue = attr.getNodeValue();
						break;
					} else if (attrLocalName == null && attrNodeName.contains(":")) {
						String localPart = attrNodeName.substring(attrNodeName.indexOf(":") + 1);
						if (localPart.equals(attrName)) {
							attrValue = attr.getNodeValue();
							break;
						}
					}
				}
			}
			
			// 속성 값이 없으면 빈 문자열로 처리
			if (attrValue == null) {
				attrValue = "";
			}
			
			// 공백 제거 (앞뒤 공백만 제거) - trim()은 앞뒤 공백만 제거하므로 안전
			// ZipCode="419679"의 경우 trim() 후에도 정확히 6자리가 유지되어야 함
			String originalAttrValue = attrValue;
			attrValue = attrValue.trim();
			
			// 속성 규칙이 Map인 경우 required 체크
			if (attrRule instanceof Map) {
				Map<String, Object> attrRuleMap = (Map<String, Object>) attrRule;
				
				// 속성 필수 여부 확인
				Object requiredObj = attrRuleMap.get("required");
				boolean isRequired = false;
				if (requiredObj != null) {
					String requiredStr = requiredObj.toString();
					isRequired = "true".equalsIgnoreCase(requiredStr) || 
								"required".equalsIgnoreCase(requiredStr) ||
								"1".equals(requiredStr);
				}
				
				// 필수 속성인데 값이 비어있으면 오류
				if (isRequired && (attrValue == null || attrValue.isEmpty())) {
					addError(xmlFile, lineNum, -1,
							path + " 요소의 " + attrName + " 속성은 필수입니다.");
					// 필수 속성이 없으면 다른 검증은 건너뜀
					continue;
				}
			}
			
			if (attrRule instanceof Map) {
				Map<String, Object> attrRuleMap = (Map<String, Object>) attrRule;
				String type = (String) attrRuleMap.get("type");
				
				// enum 타입 검증
				if ("enum".equals(type)) {
					String codeRef = (String) attrRuleMap.get("code_ref");
					@SuppressWarnings("unchecked")
					List<String> allowedValues = (List<String>) attrRuleMap.get("allowed_values");
					
					// code_ref가 있으면 code_values에서 동적으로 허용 값 가져오기
					if (codeRef != null && !codeRef.isEmpty() && ruleParser != null) {
						Map<String, Object> codeCategory = ruleParser.getCodeValues().get(codeRef);
						if (codeCategory != null) {
							allowedValues = new ArrayList<>(codeCategory.keySet());
						}
					}
					
					if (allowedValues != null && !allowedValues.isEmpty()) {
						Object requiredObj = attrRuleMap.get("required");
						boolean isRequired = false;
						if (requiredObj != null) {
							String requiredStr = requiredObj.toString();
							isRequired = "true".equalsIgnoreCase(requiredStr) || 
										"required".equalsIgnoreCase(requiredStr) ||
										"1".equals(requiredStr);
						}
						
						if (attrValue.isEmpty()) {
							if (isRequired) {
								addError(xmlFile, lineNum, -1,
										path + " 요소의 " + attrName + " 속성은 필수입니다.");
							}
						} else {
							boolean isAllowed = allowedValues.contains(attrValue);
							if (!isAllowed) {
								String errorMessage;
								if (codeRef != null && !codeRef.isEmpty()) {
									errorMessage = path + " 요소의 " + attrName + " 속성 값 '" + attrValue + 
											"'이(가) '" + codeRef + "' 코드에 정의된 값이 아닙니다. 허용값: " + allowedValues;
								} else {
									errorMessage = path + " 요소의 " + attrName + " 속성 값 '" + attrValue + 
											"'이(가) 허용된 값이 아닙니다. 허용값: " + allowedValues;
								}
								addError(xmlFile, lineNum, -1, errorMessage);
							}
						}
					}
					
					// enum 타입이면서 length가 지정된 경우 길이도 검증
					Object lengthObj = attrRuleMap.get("length");
					if (lengthObj != null && !attrValue.isEmpty()) {
						try {
							int requiredLength = Integer.parseInt(lengthObj.toString());
							int actualLength = attrValue.length();
							if (actualLength != requiredLength) {
								addError(xmlFile, lineNum, -1, path + " 요소의 " + attrName + 
										" 속성 값의 길이가 정확히 " + requiredLength + "자여야 합니다. 현재 길이: " + actualLength);
							}
						} catch (NumberFormatException e) {
							// 무시
						}
					}
				} else {
					// enum 타입이 아닌 경우 format 검증
					String format = (String) attrRuleMap.get("format");
					if (format != null && !format.isEmpty() && !attrValue.isEmpty()) {
						try {
							java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(format);
							java.util.regex.Matcher matcher = pattern.matcher(attrValue);
							if (!matcher.matches()) {
								addError(xmlFile, lineNum, -1, path + " 요소의 " + attrName + 
										" 속성 값 '" + attrValue + "'이(가) 허용된 형식이 아닙니다.");
							}
						} catch (Exception e) {
							// 무시
						}
					}
				}
				
				// fixed_length 타입 검증
				if ("fixed_length".equals(type)) {
					Object lengthObj = attrRuleMap.get("length");
					Object minLengthObj = attrRuleMap.get("min_length");
					Object maxLengthObj = attrRuleMap.get("max_length");
					
					if (!attrValue.isEmpty()) {
						// 실제 길이 계산 - trim 후의 정확한 길이를 사용
						// ZipCode="419679"의 경우 정확히 6자리를 읽어야 함
						int actualLength = attrValue.length();
						
						// min_length와 max_length가 모두 지정된 경우 (범위 검증) - ZipCode 등
						if (minLengthObj != null && maxLengthObj != null) {
							int minLength = Integer.parseInt(minLengthObj.toString());
							int maxLength = Integer.parseInt(maxLengthObj.toString());
							
							// 길이 검증 - 실제 읽은 값의 길이를 정확히 확인
							if (actualLength < minLength || actualLength > maxLength) {
								// 원본 값과 trim 후 값, 그리고 각각의 길이를 모두 표시
								String originalDisplay = originalAttrValue;
								String trimmedDisplay = attrValue;
								int originalLength = originalAttrValue.length();
								
								// 오류 메시지에 상세 정보 포함
								addError(xmlFile, lineNum, -1, path + " 요소의 " + attrName + 
										" 속성 값이 잘못되었습니다. " +
										"원본 값: '" + originalDisplay + "' (길이: " + originalLength + "), " +
										"trim 후: '" + trimmedDisplay + "' (길이: " + actualLength + "). " +
										"요구 길이: " + minLength + "~" + maxLength + "자");
							}
						}
						// length만 지정된 경우 (정확한 길이 검증)
						else if (lengthObj != null) {
							int requiredLength = Integer.parseInt(lengthObj.toString());
							
							// 길이 검증 - 실제 읽은 값의 길이를 정확히 확인
							if (actualLength != requiredLength) {
								// 원본 값과 trim 후 값, 그리고 각각의 길이를 모두 표시
								String originalDisplay = originalAttrValue;
								String trimmedDisplay = attrValue;
								int originalLength = originalAttrValue.length();
								
								// 오류 메시지에 상세 정보 포함
								addError(xmlFile, lineNum, -1, path + " 요소의 " + attrName + 
										" 속성 값이 잘못되었습니다. " +
										"원본 값: '" + originalDisplay + "' (길이: " + originalLength + "), " +
										"trim 후: '" + trimmedDisplay + "' (길이: " + actualLength + "). " +
										"요구 길이: 정확히 " + requiredLength + "자");
							}
						}
					}
				}
				
				// max_length 타입 검증
				if ("max_length".equals(type)) {
					Object lengthObj = attrRuleMap.get("length");
					Object minLengthObj = attrRuleMap.get("min_length");
					
					if (!attrValue.isEmpty()) {
						int actualLength = attrValue.length();
						
						// min_length가 지정된 경우 최소 길이 검증
						if (minLengthObj != null) {
							int minLength = Integer.parseInt(minLengthObj.toString());
							if (actualLength < minLength) {
								addError(xmlFile, lineNum, -1, path + " 요소의 " + attrName + 
										" 속성 값의 길이가 최소 " + minLength + "자 이상이어야 합니다. 현재 길이: " + actualLength);
							}
						}
						
						// max_length (length로 지정) 검증
						if (lengthObj != null) {
							int maxLength = Integer.parseInt(lengthObj.toString());
							if (actualLength > maxLength) {
								addError(xmlFile, lineNum, -1, path + " 요소의 " + attrName + 
										" 속성 값의 길이가 " + maxLength + "자를 초과합니다. 현재 길이: " + actualLength);
							}
						}
					}
				}
			} else if (attrRule instanceof String) {
				// 고정 값 검증
				String expectedValue = (String) attrRule;
				if (!expectedValue.isEmpty() && !attrValue.equals(expectedValue)) {
					addError(xmlFile, lineNum, -1,
							path + " 요소의 " + attrName + " 속성 값이 '" + expectedValue + 
							"'이어야 합니다. 현재 값: '" + attrValue + "'");
				}
			}
		}
	}
	
	/**
	 * 데이터 타입을 검증합니다.
	 */
	private void validateDataType(File xmlFile, Element element, String dataType, String path, String encoding) {
		// 요소의 정확한 라인 번호 찾기
		int lineNum = findElementLineNumber(xmlFile, element, path, encoding);
		if (lineNum < 0) {
			// 찾지 못한 경우 기본값 사용
			lineNum = getLineNumber(element.getNodeName());
			if (lineNum < 0) {
				lineNum = 1;
			}
		}
		
		// 직접 텍스트 콘텐츠만 가져옴 (자식 요소의 텍스트 제외)
		String value = getDirectTextContent(element);
		if (value == null) {
			value = "";
		}
		
		// 빈 값은 필수 검증에서 처리 (validateElement에서 이미 처리됨)
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
						addError(xmlFile, lineNum, -1,
								path + " 요소의 값 '" + value + "'이(가) 숫자 형식이 아닙니다.");
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
				addError(xmlFile, lineNum, -1,
						path + " 요소의 값 길이가 " + maxLength + "자를 초과합니다. 현재 길이: " + value.length());
			}
		}
	}
	
	/**
	 * 포맷을 검증합니다.
	 */
	private void validateFormat(File xmlFile, Element element, String format, String path, String encoding) {
		// 요소의 정확한 라인 번호 찾기
		int lineNum = findElementLineNumber(xmlFile, element, path, encoding);
		if (lineNum < 0) {
			// 찾지 못한 경우 기본값 사용
			lineNum = getLineNumber(element.getNodeName());
			if (lineNum < 0) {
				lineNum = 1;
			}
		}
		
		// 직접 텍스트 콘텐츠만 가져옴 (자식 요소의 텍스트 제외)
		String value = getDirectTextContent(element);
		if (value == null || value.isEmpty()) {
			return;
		}
		
		// YYYYMMDD 형식 검증
		if (format.contains("YYYYMMDD")) {
			if (!value.matches("\\d{8}")) {
				addError(xmlFile, lineNum, -1,
						path + " 요소의 값 '" + value + "'이(가) YYYYMMDD 형식이 아닙니다.");
			} else {
				// 날짜 유효성 검증
				try {
					int year = Integer.parseInt(value.substring(0, 4));
					int month = Integer.parseInt(value.substring(4, 6));
					int day = Integer.parseInt(value.substring(6, 8));
					
					if (year < 1900 || month < 1 || month > 12 || day < 1 || day > 31) {
						addError(xmlFile, lineNum, -1,
								path + " 요소의 날짜 값 '" + value + "'이(가) 유효하지 않습니다.");
					}
				} catch (NumberFormatException e) {
					// 이미 위에서 검증됨
				}
			}
		}
		
		// HHMISS 형식 검증
		if (format.contains("HHMISS")) {
			if (!value.matches("\\d{6}")) {
				addError(xmlFile, lineNum, -1,
						path + " 요소의 값 '" + value + "'이(가) HHMISS 형식이 아닙니다.");
			} else {
				try {
					int hour = Integer.parseInt(value.substring(0, 2));
					int minute = Integer.parseInt(value.substring(2, 4));
					int second = Integer.parseInt(value.substring(4, 6));
					
					if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 59) {
						addError(xmlFile, lineNum, -1,
								path + " 요소의 시간 값 '" + value + "'이(가) 유효하지 않습니다.");
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
				addError(xmlFile, lineNum, -1,
						path + " 요소의 값에 금칙어(<, >, \", ;)가 포함되어 있습니다.");
			}
		}
		
		// 1~5 범위 검증
		if (format.contains("1에서5사이")) {
			try {
				int num = Integer.parseInt(value);
				if (num < 1 || num > 5) {
					addError(xmlFile, lineNum, -1,
							path + " 요소의 값 '" + value + "'이(가) 1~5 범위를 벗어났습니다.");
				}
			} catch (NumberFormatException e) {
				addError(xmlFile, lineNum, -1,
						path + " 요소의 값 '" + value + "'이(가) 숫자가 아닙니다.");
			}
		}
	}
	
	/**
	 * 허용 코드를 검증합니다.
	 */
	private void validateAllowedCodes(File xmlFile, Element element, List<String> allowedCodes, String path, String encoding) {
		// 요소의 정확한 라인 번호 찾기
		int lineNum = findElementLineNumber(xmlFile, element, path, encoding);
		if (lineNum < 0) {
			// 찾지 못한 경우 기본값 사용
			lineNum = getLineNumber(element.getNodeName());
			if (lineNum < 0) {
				lineNum = 1;
			}
		}
		
		// 속성에서 Code 값 확인
		String codeValue = element.getAttribute("Code");
		if (codeValue != null && !codeValue.isEmpty()) {
			if (!allowedCodes.contains(codeValue)) {
				addError(xmlFile, lineNum, -1,
						path + " 요소의 Code 속성 값 '" + codeValue + "'이(가) 허용된 코드가 아닙니다.");
			}
		}
	}
	
	/**
	 * 자식 요소들을 검증합니다.
	 */
	@SuppressWarnings("unchecked")
	private void validateChildren(File xmlFile, Element parent, Map<String, Object> childrenRules, String parentPath) {
		validateChildren(xmlFile, parent, childrenRules, parentPath, 0);
	}
	
	/**
	 * 자식 요소들을 검증합니다 (재귀 깊이 추적 포함).
	 */
	@SuppressWarnings("unchecked")
	private void validateChildren(File xmlFile, Element parent, Map<String, Object> childrenRules, String parentPath, int depth) {
		// 무한 루프 방지: 최대 재귀 깊이 체크
		if (depth > MAX_RECURSION_DEPTH) {
			return;
		}
		
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
				String childPath = parentPath + "/" + childName;
				validateElement(xmlFile, child, childRule, childPath, depth + 1);
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
				addError(xmlFile, lineNum, -1,
						parentPath + " 요소에 필수 자식 요소 '" + elementName + "'이(가) 없습니다.");
			} else if (count > 1) {
				addError(xmlFile, lineNum, -1,
						parentPath + " 요소에 '" + elementName + "' 요소가 1개만 있어야 하지만 " + count + "개가 있습니다.");
			}
		} else if (occurrence.equals("0..1")) {
			// 0개 또는 1개
			if (count > 1) {
				addError(xmlFile, lineNum, -1,
						parentPath + " 요소에 '" + elementName + "' 요소가 최대 1개만 있어야 하지만 " + count + "개가 있습니다.");
			}
		} else if (occurrence.equals("1..n")) {
			// 1개 이상
			if (count == 0) {
				addError(xmlFile, lineNum, -1,
						parentPath + " 요소에 '" + elementName + "' 요소가 최소 1개 이상 있어야 합니다.");
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
				String nodeName = elem.getNodeName();
				String localName = elem.getLocalName();
				if (localName == null) {
					localName = nodeName;
				}
				// 네임스페이스 프리픽스 제거
				String compareName = localName;
				if (compareName.contains(":")) {
					compareName = compareName.substring(compareName.indexOf(":") + 1);
				}
				
				if (compareName.equals(childName)) {
					children.add(elem);
				}
			}
		}
		
		return children;
	}
	
	/**
	 * 요소의 직접 텍스트 콘텐츠만 가져옵니다 (자식 요소의 텍스트 제외).
	 * 예: <RealNumber Code="01">9001011234567</RealNumber>에서 "9001011234567"만 반환
	 */
	private String getDirectTextContent(Element element) {
		StringBuilder sb = new StringBuilder();
		NodeList childNodes = element.getChildNodes();
		for (int i = 0; i < childNodes.getLength(); i++) {
			Node child = childNodes.item(i);
			// TEXT_NODE만 수집 (자식 요소 제외)
			if (child.getNodeType() == Node.TEXT_NODE) {
				sb.append(child.getNodeValue());
			}
		}
		return sb.toString().trim();
	}
	
	/**
	 * 문자열의 각 문자를 상세히 분석합니다 (디버깅용).
	 */
	private String getCharDetails(String str) {
		if (str == null || str.isEmpty()) {
			return "empty";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			sb.append("[").append(i).append("]='").append(c).append("'");
			sb.append("(U+").append(String.format("%04X", (int)c)).append(")");
			if (i < str.length() - 1) {
				sb.append(" ");
			}
		}
		return sb.toString();
	}
	
	/**
	 * 오류를 추가합니다 (중복 체크 포함).
	 */
	private void addError(File xmlFile, int lineNum, int columnNum, String message) {
		// 중복 오류 확인 - 같은 파일, 같은 라인, 같은 메시지인 경우 추가하지 않음
		for (ValidationError error : errors) {
			if (error.getFile().equals(xmlFile) && 
				error.getLineNumber() == lineNum && 
				error.getMessage().equals(message)) {
				return; // 중복 오류는 추가하지 않음
			}
		}
		errors.add(new ValidationError(xmlFile, lineNum, columnNum, message, 
				ValidationError.ErrorType.CONSISTENCY));
	}
	
	/**
	 * 검증 결과 오류 목록을 라인 번호순으로 정렬하여 반환합니다.
	 */
	public List<ValidationError> getErrors() {
		List<ValidationError> sortedErrors = new ArrayList<>(errors);
		sortedErrors.sort((e1, e2) -> Integer.compare(e1.getLineNumber(), e2.getLineNumber()));
		return sortedErrors;
	}
}
