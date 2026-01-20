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
		
		// 파일 정보 로깅 (최신 정보 확인)
		long fileSize = xmlFile.length();
		long lastModified = xmlFile.lastModified();
		System.out.println("정합성 검증 시작: " + xmlFile.getName());
		System.out.println("파일 정보: 크기=" + fileSize + " bytes, 수정 시간=" + lastModified);
		
		try {
			// 인코딩 처리
			String encoding = ruleParser.getEncoding();
			System.out.println("인코딩: " + encoding);
			
			// 먼저 라인 번호 매핑 생성 (항상 최신 파일에서 읽음)
			buildLineNumberMap(xmlFile, encoding);
			
			// DocumentBuilderFactory를 매번 새로 생성하여 캐시 방지
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			// 캐시 방지를 위한 추가 설정
			factory.setValidating(false);
			DocumentBuilder builder = factory.newDocumentBuilder();
			
			// 항상 FileInputStream을 사용하여 최신 파일 내용을 읽음
			Document doc;
			if (encoding != null && !encoding.isEmpty()) {
				try (FileInputStream fis = new FileInputStream(xmlFile)) {
					org.xml.sax.InputSource is = new org.xml.sax.InputSource(fis);
					is.setEncoding(encoding);
					// SystemId를 설정하여 파일 경로 명시
					is.setSystemId(xmlFile.toURI().toString());
					doc = builder.parse(is);
				}
			} else {
				// 인코딩이 없어도 FileInputStream을 사용하여 최신 내용 보장
				try (FileInputStream fis = new FileInputStream(xmlFile)) {
					org.xml.sax.InputSource is = new org.xml.sax.InputSource(fis);
					is.setSystemId(xmlFile.toURI().toString());
					doc = builder.parse(is);
				}
			}
			
			// 루트 요소 검증
			Element root = doc.getDocumentElement();
			if (root == null) {
				addError(xmlFile, 1, -1, "XML 파일에 루트 요소가 없습니다.");
				return false;
			}
			
			System.out.println("루트 요소: " + root.getNodeName());
			
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
				System.err.println("경고: 루트 요소 '" + rootElementName + "'에 대한 규칙이 없습니다!");
				addError(xmlFile, 1, -1, "루트 요소 '" + rootElementName + "'에 대한 규칙이 정의되지 않았습니다.");
			}
			
			System.out.println("검증 완료. 오류 수: " + errors.size());
			return errors.isEmpty();
			
		} catch (Exception e) {
			System.err.println("정합성 검사 예외: " + e.getMessage());
			e.printStackTrace();
			addError(xmlFile, 1, -1, "정합성 검사 오류: " + e.getMessage());
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
	 * 같은 이름의 요소가 여러 개 있을 때는 첫 번째 요소의 라인 번호를 반환합니다.
	 */
	private int getLineNumber(String elementName) {
		Integer lineNum = elementLineNumbers.get(elementName);
		return lineNum != null ? lineNum : -1;
	}
	
	/**
	 * 요소의 실제 라인 번호를 찾습니다.
	 * 요소의 경로와 내용을 사용하여 정확한 라인을 찾습니다.
	 */
	private int findElementLineNumber(File xmlFile, Element element, String path, String encoding) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(xmlFile), 
						encoding != null ? encoding : "UTF-8"))) {
			
			String line;
			int lineNumber = 0;
			String elementName = element.getLocalName();
			if (elementName == null) {
				elementName = element.getNodeName();
				if (elementName.contains(":")) {
					elementName = elementName.substring(elementName.indexOf(":") + 1);
				}
			}
			
			// 요소의 고유 속성 값으로 정확한 요소를 찾기 (Code 속성뿐만 아니라 다른 속성도 지원)
			// 규칙 파일에서 정의된 속성 중 첫 번째 속성을 사용하여 요소를 구분
			String uniqueAttrValue = null;
			String uniqueAttrName = null;
			NamedNodeMap attrs = element.getAttributes();
			if (attrs == null) {
				// 속성이 없으면 요소 이름만으로 찾기
				String searchElementName = path.contains("/") ? 
						path.substring(path.lastIndexOf("/") + 1) : path;
				Pattern pattern = Pattern.compile("<" + Pattern.quote(searchElementName) + "(?:\\s|>|/)");
				while ((line = reader.readLine()) != null) {
					lineNumber++;
					Matcher matcher = pattern.matcher(line);
					if (matcher.find()) {
						return lineNumber;
					}
				}
				return getLineNumber(elementName);
			}
			// Code 속성이 있으면 우선 사용, 없으면 첫 번째 속성 사용
			for (int i = 0; i < attrs.getLength(); i++) {
				Node attr = attrs.item(i);
				String attrName = attr.getLocalName();
				if (attrName == null) {
					attrName = attr.getNodeName();
					if (attrName.contains(":")) {
						attrName = attrName.substring(attrName.indexOf(":") + 1);
					}
				}
				if ("Code".equals(attrName)) {
					uniqueAttrName = attrName;
					uniqueAttrValue = attr.getNodeValue();
					break;
				}
			}
			// Code 속성이 없으면 첫 번째 속성 사용
			if (uniqueAttrValue == null && attrs.getLength() > 0) {
				Node firstAttr = attrs.item(0);
				uniqueAttrName = firstAttr.getLocalName();
				if (uniqueAttrName == null) {
					uniqueAttrName = firstAttr.getNodeName();
					if (uniqueAttrName.contains(":")) {
						uniqueAttrName = uniqueAttrName.substring(uniqueAttrName.indexOf(":") + 1);
					}
				}
				uniqueAttrValue = firstAttr.getNodeValue();
			}
			
			// 경로에서 마지막 요소 이름 추출
			String searchElementName = path.contains("/") ? 
					path.substring(path.lastIndexOf("/") + 1) : path;
			
			// 정규식 패턴: 요소 이름과 고유 속성 (있는 경우)
			String patternStr;
			if (uniqueAttrValue != null && !uniqueAttrValue.isEmpty() && uniqueAttrName != null) {
				// 고유 속성이 있는 경우: 속성 값으로 정확히 매칭
				patternStr = "<" + Pattern.quote(searchElementName) + "\\s+" + Pattern.quote(uniqueAttrName) + 
						"\\s*=\\s*\"" + Pattern.quote(uniqueAttrValue) + "\"";
			} else {
				// 고유 속성이 없는 경우: 요소 이름만으로 찾기
				patternStr = "<" + Pattern.quote(searchElementName) + "(?:\\s|>|/)";
			}
			
			Pattern pattern = Pattern.compile(patternStr);
			int matchCount = 0;
			int targetMatch = -1;
			
			// 같은 경로의 요소 중 몇 번째인지 찾기
			// 부모 경로를 사용하여 같은 경로의 요소들을 그룹화
			String parentPath = path.contains("/") ? 
					path.substring(0, path.lastIndexOf("/")) : "";
			
			// 먼저 같은 경로의 요소가 몇 번째인지 계산
			// 이는 DOM 트리에서의 순서를 추적해야 함
			// 간단한 방법: 같은 부모 아래에서 같은 이름의 요소 중 몇 번째인지
			if (element.getParentNode() != null && element.getParentNode() instanceof Element) {
				Element parent = (Element) element.getParentNode();
				NodeList siblings = parent.getChildNodes();
				int index = 0;
				for (int i = 0; i < siblings.getLength(); i++) {
					Node sibling = siblings.item(i);
					if (sibling instanceof Element) {
						Element siblingElem = (Element) sibling;
						String siblingName = siblingElem.getLocalName();
						if (siblingName == null) {
							siblingName = siblingElem.getNodeName();
							if (siblingName.contains(":")) {
								siblingName = siblingName.substring(siblingName.indexOf(":") + 1);
							}
						}
						if (siblingName.equals(elementName)) {
							index++;
							if (siblingElem == element) {
								targetMatch = index;
								break;
							}
						}
					}
				}
			}
			
			// XML 파일에서 해당 요소 찾기
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				Matcher matcher = pattern.matcher(line);
				
				if (matcher.find()) {
					matchCount++;
					// 고유 속성으로 매칭한 경우 정확히 일치
					if (uniqueAttrValue != null && !uniqueAttrValue.isEmpty()) {
						// 고유 속성 값이 일치하면 바로 반환
						return lineNumber;
					} else if (targetMatch > 0 && matchCount == targetMatch) {
						// 같은 이름의 요소 중 targetMatch 번째 요소
						return lineNumber;
					} else if (targetMatch <= 0 && matchCount == 1) {
						// 첫 번째 매칭 (fallback)
						return lineNumber;
					}
				}
			}
			
			// 찾지 못한 경우 첫 번째 요소의 라인 번호 반환
			return getLineNumber(elementName);
			
		} catch (java.io.UnsupportedEncodingException e) {
			System.err.println("인코딩 오류: " + encoding + " - " + e.getMessage());
			// UTF-8로 재시도
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(new FileInputStream(xmlFile), "UTF-8"))) {
				return findElementLineNumberWithReader(reader, element, path);
			} catch (Exception e2) {
				System.err.println("요소 라인 번호 찾기 오류 (재시도 실패): " + e2.getMessage());
				return getLineNumber(element.getNodeName());
			}
		} catch (Exception e) {
			System.err.println("요소 라인 번호 찾기 오류: " + e.getMessage());
			e.printStackTrace();
			// 오류 발생 시 기본 방법 사용
			return getLineNumber(element.getNodeName());
		}
	}
	
	/**
	 * BufferedReader를 사용하여 요소의 라인 번호를 찾습니다 (헬퍼 메서드).
	 */
	private int findElementLineNumberWithReader(BufferedReader reader, Element element, String path) throws Exception {
		String line;
		int lineNumber = 0;
		String elementName = element.getLocalName();
		if (elementName == null) {
			elementName = element.getNodeName();
			if (elementName.contains(":")) {
				elementName = elementName.substring(elementName.indexOf(":") + 1);
			}
		}
		
		// 요소의 고유 속성 값으로 정확한 요소를 찾기 (Code 속성뿐만 아니라 다른 속성도 지원)
		String uniqueAttrValue = null;
		String uniqueAttrName = null;
		NamedNodeMap attrs = element.getAttributes();
		if (attrs == null) {
			// 속성이 없으면 요소 이름만으로 찾기
			String searchElementName = path.contains("/") ? 
					path.substring(path.lastIndexOf("/") + 1) : path;
			Pattern pattern = Pattern.compile("<" + Pattern.quote(searchElementName) + "(?:\\s|>|/)");
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				Matcher matcher = pattern.matcher(line);
				if (matcher.find()) {
					return lineNumber;
				}
			}
			return getLineNumber(elementName);
		}
		// Code 속성이 있으면 우선 사용, 없으면 첫 번째 속성 사용
		for (int i = 0; i < attrs.getLength(); i++) {
			Node attr = attrs.item(i);
			String attrName = attr.getLocalName();
			if (attrName == null) {
				attrName = attr.getNodeName();
				if (attrName.contains(":")) {
					attrName = attrName.substring(attrName.indexOf(":") + 1);
				}
			}
			if ("Code".equals(attrName)) {
				uniqueAttrName = attrName;
				uniqueAttrValue = attr.getNodeValue();
				break;
			}
		}
		// Code 속성이 없으면 첫 번째 속성 사용
		if (uniqueAttrValue == null && attrs.getLength() > 0) {
			Node firstAttr = attrs.item(0);
			uniqueAttrName = firstAttr.getLocalName();
			if (uniqueAttrName == null) {
				uniqueAttrName = firstAttr.getNodeName();
				if (uniqueAttrName.contains(":")) {
					uniqueAttrName = uniqueAttrName.substring(uniqueAttrName.indexOf(":") + 1);
				}
			}
			uniqueAttrValue = firstAttr.getNodeValue();
		}
		
		// 경로에서 마지막 요소 이름 추출
		String searchElementName = path.contains("/") ? 
				path.substring(path.lastIndexOf("/") + 1) : path;
		
		// 정규식 패턴: 요소 이름과 고유 속성 (있는 경우)
		String patternStr;
		if (uniqueAttrValue != null && !uniqueAttrValue.isEmpty() && uniqueAttrName != null) {
			// 고유 속성이 있는 경우: 속성 값으로 정확히 매칭
			patternStr = "<" + Pattern.quote(searchElementName) + "\\s+" + Pattern.quote(uniqueAttrName) + 
					"\\s*=\\s*\"" + Pattern.quote(uniqueAttrValue) + "\"";
		} else {
			// 고유 속성이 없는 경우: 요소 이름만으로 찾기
			patternStr = "<" + Pattern.quote(searchElementName) + "(?:\\s|>|/)";
		}
		
		Pattern pattern = Pattern.compile(patternStr);
		
		// XML 파일에서 해당 요소 찾기
		while ((line = reader.readLine()) != null) {
			lineNumber++;
			Matcher matcher = pattern.matcher(line);
			
			if (matcher.find()) {
				// 고유 속성으로 매칭한 경우 정확히 일치
				if (uniqueAttrValue != null && !uniqueAttrValue.isEmpty()) {
					// 고유 속성 값이 일치하면 바로 반환
					return lineNumber;
				} else {
					// 첫 번째 매칭
					return lineNumber;
				}
			}
		}
		
		// 찾지 못한 경우 첫 번째 요소의 라인 번호 반환
		return getLineNumber(elementName);
	}
	
	/**
	 * 속성의 라인 번호를 찾습니다.
	 */
	private int findAttributeLineNumber(File xmlFile, Element element, String path, String attrName, String encoding) {
		// 요소 이름 가져오기 (네임스페이스 처리) - 변수 스코프를 위해 메서드 시작 부분에서 선언
		String elementName = element.getLocalName();
		String elementNodeName = element.getNodeName();
		boolean hasNamespace = elementNodeName.contains(":");
		
		if (elementName == null) {
			elementName = elementNodeName;
			if (hasNamespace) {
				elementName = elementName.substring(elementName.indexOf(":") + 1);
			}
		}
		
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(xmlFile), 
						encoding != null ? encoding : "UTF-8"))) {
			
			String line;
			int lineNumber = 0;
			
			// 속성 값 가져오기
			String attrValue = null;
			NamedNodeMap attrs = element.getAttributes();
			for (int i = 0; i < attrs.getLength(); i++) {
				Node attr = attrs.item(i);
				String nodeName = attr.getNodeName();
				String localName = attr.getLocalName();
				if (nodeName.equals(attrName) || (localName != null && localName.equals(attrName))) {
					attrValue = attr.getNodeValue();
					break;
				} else if (localName == null && nodeName.contains(":")) {
					String localPart = nodeName.substring(nodeName.indexOf(":") + 1);
					if (localPart.equals(attrName)) {
						attrValue = attr.getNodeValue();
						break;
					}
				}
			}
			
			System.out.println("속성 라인 번호 찾기: 요소=" + elementName + ", 속성=" + attrName + ", 값=" + attrValue);
			
			// 속성 값이 있는 경우, 더 간단하고 확실한 방법 사용
			if (attrValue != null && !attrValue.isEmpty()) {
				// 속성 이름과 값을 포함하는 라인 찾기 (대소문자 구분 없이)
				// 여러 패턴 시도: Code="aBA", Code='aBA', Code = "aBA" 등
				String[] attrPatterns = {
					attrName + "\\s*=\\s*[\"']" + Pattern.quote(attrValue) + "[\"']",
					attrName + "\\s*=\\s*" + Pattern.quote(attrValue),
					attrName + "=" + Pattern.quote(attrValue)
				};
				
				// 요소 이름 검색 패턴
				String[] elementPatterns = new String[3];
				if (hasNamespace) {
					String prefix = elementNodeName.substring(0, elementNodeName.indexOf(":"));
					elementPatterns[0] = prefix + ":" + elementName;
					elementPatterns[1] = "<" + prefix + ":" + elementName;
					elementPatterns[2] = elementName;
				} else {
					elementPatterns[0] = elementName;
					elementPatterns[1] = "<" + elementName;
					elementPatterns[2] = elementName;
				}
				
				while ((line = reader.readLine()) != null) {
					lineNumber++;
					
					// 라인에 요소 이름이 포함되어 있는지 확인
					boolean hasElement = false;
					for (String elemPattern : elementPatterns) {
						if (line.contains(elemPattern)) {
							hasElement = true;
							break;
						}
					}
					
					if (hasElement) {
						// 속성 패턴 매칭 시도
						for (String attrPatternStr : attrPatterns) {
							try {
								Pattern attrPattern = Pattern.compile(attrPatternStr, Pattern.CASE_INSENSITIVE);
								Matcher matcher = attrPattern.matcher(line);
								if (matcher.find()) {
									System.out.println("속성 라인 번호 찾기 성공: 라인 " + lineNumber);
									System.out.println("매칭된 라인: " + line.trim());
									return lineNumber;
								}
							} catch (Exception e) {
								// 패턴 컴파일 실패 시 다음 패턴 시도
							}
						}
						
						// 정규식 실패 시 단순 문자열 검색
						if (line.contains(attrName + "=") && line.contains(attrValue)) {
							System.out.println("속성 라인 번호 찾기 성공 (문자열 검색): 라인 " + lineNumber);
							System.out.println("매칭된 라인: " + line.trim());
							return lineNumber;
						}
					}
				}
			} else {
				// 속성 값이 없으면 속성 이름만으로 찾기
				String searchAttr = attrName + "\\s*=";
				Pattern attrPattern = Pattern.compile(searchAttr, Pattern.CASE_INSENSITIVE);
				
				String elementSearch = elementName;
				if (hasNamespace) {
					String prefix = elementNodeName.substring(0, elementNodeName.indexOf(":"));
					elementSearch = prefix + ":" + elementName;
				}
				
				while ((line = reader.readLine()) != null) {
					lineNumber++;
					if (line.contains(elementSearch) || line.contains(elementName)) {
						Matcher matcher = attrPattern.matcher(line);
						if (matcher.find()) {
							System.out.println("속성 라인 번호 찾기 성공: 라인 " + lineNumber);
							System.out.println("매칭된 라인: " + line.trim());
							return lineNumber;
						}
					}
				}
			}
			
			System.err.println("========================================");
			System.err.println("속성 라인 번호를 찾지 못했습니다!");
			System.err.println("  요소: " + elementName);
			System.err.println("  속성: " + attrName);
			System.err.println("  값: " + attrValue);
			System.err.println("  파일: " + xmlFile.getName());
			System.err.println("========================================");
		} catch (Exception e) {
			System.err.println("========================================");
			System.err.println("속성 라인 번호 찾기 오류 발생!");
			System.err.println("  요소: " + elementName);
			System.err.println("  속성: " + attrName);
			System.err.println("  오류: " + e.getMessage());
			System.err.println("========================================");
			e.printStackTrace();
		}
		
		// 찾지 못한 경우 요소의 라인 번호 반환
		System.out.println(">>> 요소 라인 번호 찾기 시도...");
		int elementLineNum = findElementLineNumber(xmlFile, element, path, encoding);
		System.out.println(">>> 요소 라인 번호: " + elementLineNum);
		return elementLineNum;
	}
	
	/**
	 * 요소를 검증합니다.
	 */
	@SuppressWarnings("unchecked")
	private void validateElement(File xmlFile, Element element, Map<String, Object> rule, String path) {
		System.out.println("========================================");
		System.out.println("요소 검증 시작: " + path);
		System.out.println("요소 nodeName: " + element.getNodeName());
		System.out.println("요소 localName: " + element.getLocalName());
		System.out.println("요소 namespaceURI: " + element.getNamespaceURI());
		
		// 1. 속성 검증
		@SuppressWarnings("unchecked")
		Map<String, Object> attributes = (Map<String, Object>) rule.get("attributes");
		System.out.println("========================================");
		System.out.println(">>> 속성 검증 규칙 확인: " + path);
		System.out.println(">>> attributes 규칙 존재 여부: " + (attributes != null));
		if (attributes != null) {
			System.out.println(">>> 속성 검증 규칙 발견: " + attributes.keySet());
			System.out.println(">>> 속성 규칙 개수: " + attributes.size());
			
			// 요소의 모든 속성 출력 (디버깅)
			NamedNodeMap allAttrs = element.getAttributes();
			System.out.println("요소 '" + path + "'의 모든 속성 (총 " + allAttrs.getLength() + "개):");
			for (int i = 0; i < allAttrs.getLength(); i++) {
				Node attr = allAttrs.item(i);
				System.out.println("  속성[" + i + "]: nodeName='" + attr.getNodeName() + 
						"', localName='" + attr.getLocalName() + 
						"', namespaceURI='" + attr.getNamespaceURI() +
						"', value='" + attr.getNodeValue() + "'");
			}
			
			// 규칙 파일의 attributes 규칙에 따라 검증 (특정 element/attribute에 특화된 로직 없음)
			validateAttributes(xmlFile, element, attributes, path);
		} else {
			System.out.println("속성 검증 규칙 없음");
		}
		
		// 2. 필수 여부 및 occurrence 검증은 validateChildren에서 처리
		
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
		// 요소의 실제 라인 번호 찾기 (Code 속성 값 사용)
		String encoding = null;
		try {
			if (ruleParser != null) {
				encoding = ruleParser.getEncoding();
			}
		} catch (Exception e) {
			System.err.println("인코딩 가져오기 오류: " + e.getMessage());
		}
		if (encoding == null || encoding.isEmpty()) {
			encoding = "UTF-8";
		}
		int baseLineNum = findElementLineNumber(xmlFile, element, path, encoding);
		
		// 디버깅: 요소의 모든 속성 출력
		NamedNodeMap allAttrs = element.getAttributes();
		System.out.println("========================================");
		System.out.println("요소 '" + path + "'의 모든 속성 (총 " + allAttrs.getLength() + "개):");
		for (int i = 0; i < allAttrs.getLength(); i++) {
			Node attr = allAttrs.item(i);
			String attrNodeName = attr.getNodeName();
			String attrLocalName = attr.getLocalName();
			String attrValue = attr.getNodeValue();
			
			// 바이트 단위 분석
			byte[] bytes = attrValue != null ? attrValue.getBytes() : new byte[0];
			StringBuilder byteHex = new StringBuilder();
			for (byte b : bytes) {
				byteHex.append(String.format("%02X ", b));
			}
			
			System.out.println("  속성[" + i + "]:");
			System.out.println("    nodeName='" + attrNodeName + "'");
			System.out.println("    localName='" + attrLocalName + "'");
			System.out.println("    value='" + attrValue + "'");
			System.out.println("    문자 길이: " + (attrValue != null ? attrValue.length() : 0));
			System.out.println("    바이트 길이: " + bytes.length);
			System.out.println("    바이트 값 (HEX): " + byteHex.toString().trim());
			System.out.println("    각 문자: " + (attrValue != null ? getCharDetails(attrValue) : "null"));
		}
		System.out.println("========================================");
		
		for (Map.Entry<String, Object> entry : attributes.entrySet()) {
			String attrName = entry.getKey();
			Object attrRule = entry.getValue();
			
			// 각 속성의 라인 번호 계산 (속성별로 정확한 라인 찾기)
			System.out.println("========================================");
			System.out.println(">>> 라인 번호 찾기 시작: " + path + "/@" + attrName);
			System.out.println(">>> baseLineNum: " + baseLineNum);
			System.out.println(">>> 파일: " + xmlFile.getName());
			
			int lineNum = findAttributeLineNumber(xmlFile, element, path, attrName, encoding);
			System.out.println(">>> findAttributeLineNumber 반환값: " + lineNum);
			
			if (lineNum < 0) {
				System.out.println(">>> 속성 라인을 찾지 못함. baseLineNum 사용: " + baseLineNum);
				lineNum = baseLineNum; // 속성 라인을 찾지 못하면 요소 라인 사용
			}
			if (lineNum < 0) {
				// 요소 라인도 찾지 못한 경우, 최소한 1로 설정 (루트 요소)
				System.out.println(">>> 요소 라인도 찾지 못함. 기본값 1 사용");
				lineNum = 1;
				System.err.println("경고: 라인 번호를 찾을 수 없어 기본값 1을 사용합니다: " + path + "/@" + attrName);
			}
			
			System.out.println(">>> 최종 라인 번호: " + lineNum);
			System.out.println("========================================");
			
			System.out.println("========================================");
			System.out.println("속성 검증 시작: " + path + "/@" + attrName + " (라인: " + lineNum + ")");
			System.out.println("속성 규칙 타입: " + (attrRule instanceof Map ? "Map" : attrRule.getClass().getSimpleName()));
			if (attrRule instanceof Map) {
				Map<String, Object> attrRuleMap = (Map<String, Object>) attrRule;
				String type = (String) attrRuleMap.get("type");
				System.out.println("속성 규칙 type: " + type);
				if ("enum".equals(type)) {
					System.out.println(">>> enum 타입 속성 발견: " + attrName);
				}
			}
			
			// 속성 값 읽기 - 여러 방법 시도
			String attrValue = null;
			
			// 방법 1: NamedNodeMap을 통해 직접 읽기 (가장 안정적)
			NamedNodeMap attrs = element.getAttributes();
			System.out.println("요소의 모든 속성 개수: " + attrs.getLength());
			for (int i = 0; i < attrs.getLength(); i++) {
				Node attr = attrs.item(i);
				String attrNodeName = attr.getNodeName();
				String attrLocalName = attr.getLocalName();
				
				System.out.println("  속성[" + i + "]: nodeName='" + attrNodeName + 
						"', localName='" + attrLocalName + 
						"', 찾는 속성명='" + attrName + "'");
				
				// nodeName 또는 localName이 일치하는지 확인
				if (attrNodeName.equals(attrName)) {
					attrValue = attr.getNodeValue();
					System.out.println(">>> 속성 '" + attrName + "' 발견 (nodeName 일치)");
					System.out.println("    읽은 값: '" + attrValue + "'");
					System.out.println("    문자 길이: " + attrValue.length());
					System.out.println("    바이트 길이: " + attrValue.getBytes().length);
					System.out.println("    각 문자: " + getCharDetails(attrValue));
					break;
				} else if (attrLocalName != null && attrLocalName.equals(attrName)) {
					attrValue = attr.getNodeValue();
					System.out.println(">>> 속성 '" + attrName + "' 발견 (localName 일치)");
					System.out.println("    읽은 값: '" + attrValue + "'");
					System.out.println("    문자 길이: " + attrValue.length());
					System.out.println("    바이트 길이: " + attrValue.getBytes().length);
					System.out.println("    각 문자: " + getCharDetails(attrValue));
					break;
				} else if (attrLocalName == null) {
					// localName이 null인 경우 nodeName에서 콜론 뒤 부분 확인
					if (attrNodeName.contains(":")) {
						String localPart = attrNodeName.substring(attrNodeName.indexOf(":") + 1);
						if (localPart.equals(attrName)) {
							attrValue = attr.getNodeValue();
							System.out.println(">>> 속성 '" + attrName + "' 발견 (nodeName에서 추출)");
							System.out.println("    읽은 값: '" + attrValue + "'");
							System.out.println("    문자 길이: " + attrValue.length());
							System.out.println("    바이트 길이: " + attrValue.getBytes().length);
							System.out.println("    각 문자: " + getCharDetails(attrValue));
							break;
						}
					}
				}
			}
			
			// 방법 2: getAttribute() 시도 (방법 1이 실패한 경우)
			if (attrValue == null && element.hasAttribute(attrName)) {
				attrValue = element.getAttribute(attrName);
				System.out.println("속성 '" + attrName + "' 발견 (getAttribute): '" + attrValue + "'");
			}
			
			// 방법 3: 네임스페이스 처리 (방법 1, 2가 실패한 경우)
			if (attrValue == null) {
				String localName = attrName;
				if (localName.contains(":")) {
					localName = localName.substring(localName.indexOf(":") + 1);
				}
				if (element.hasAttributeNS(null, localName)) {
					attrValue = element.getAttributeNS(null, localName);
					System.out.println("속성 '" + attrName + "' 발견 (getAttributeNS): '" + attrValue + "'");
				}
			}
			
			// 속성 값이 없으면 빈 문자열로 처리
			if (attrValue == null) {
				attrValue = "";
				System.out.println("경고: 속성 '" + attrName + "'를 찾을 수 없습니다.");
			}
			
			// 공백 제거 (앞뒤 공백만)
			String originalValue = attrValue;
			attrValue = attrValue.trim();
			if (!originalValue.equals(attrValue)) {
				System.out.println("속성 값 공백 제거: '" + originalValue + "' -> '" + attrValue + "'");
			}
			
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
			
			// 디버깅 로그
			if (attrRule instanceof Map) {
				Map<String, Object> attrRuleMap = (Map<String, Object>) attrRule;
				String type = (String) attrRuleMap.get("type");
				if ("fixed_length".equals(type)) {
					System.out.println("속성 검증: " + path + "/@" + attrName + 
							" = '" + attrValue + "' (길이: " + attrValue.length() + 
							", 바이트 길이: " + attrValue.getBytes().length + ")");
				}
			}
			
			if (attrRule instanceof Map) {
				Map<String, Object> attrRuleMap = (Map<String, Object>) attrRule;
				
				// enum 타입 검증
				String type = (String) attrRuleMap.get("type");
				System.out.println("속성 규칙 type 확인: '" + type + "' (비교 대상: 'enum')");
				if ("enum".equals(type)) {
					System.out.println("========================================");
					System.out.println(">>> enum 검증 시작: " + path + "/@" + attrName);
					System.out.println("속성 값: '" + attrValue + "'");
					System.out.println("속성 값 길이: " + attrValue.length());
					
					@SuppressWarnings("unchecked")
					List<String> allowedValues = (List<String>) attrRuleMap.get("allowed_values");
					System.out.println("허용된 값 목록: " + allowedValues);
					System.out.println("허용된 값 목록 타입: " + (allowedValues != null ? allowedValues.getClass().getName() : "null"));
					if (allowedValues != null) {
						System.out.println("허용된 값 목록 크기: " + allowedValues.size());
						for (int i = 0; i < allowedValues.size(); i++) {
							String allowedValue = allowedValues.get(i);
							System.out.println("  허용값[" + i + "]: '" + allowedValue + "' (길이: " + allowedValue.length() + ")");
						}
					}
					
					if (allowedValues != null && !allowedValues.isEmpty()) {
						// 필수 속성인 경우 빈 값도 체크
						Object requiredObj = attrRuleMap.get("required");
						boolean isRequired = false;
						if (requiredObj != null) {
							String requiredStr = requiredObj.toString();
							isRequired = "true".equalsIgnoreCase(requiredStr) || 
										"required".equalsIgnoreCase(requiredStr) ||
										"1".equals(requiredStr);
						}
						
						// 빈 값 체크
						if (attrValue.isEmpty()) {
							if (isRequired) {
								System.err.println("enum 검증 오류: 필수 속성인데 값이 비어있습니다.");
								addError(xmlFile, lineNum, -1,
										path + " 요소의 " + attrName + " 속성은 필수입니다.");
							} else {
								System.out.println("enum 검증: 선택적 속성이므로 빈 값은 허용됩니다.");
							}
						} else {
							// 허용된 값 목록에 포함되어 있는지 확인
							// 대소문자 구분 없이 비교 (필요한 경우)
							boolean isAllowed = false;
							for (String allowedValue : allowedValues) {
								if (allowedValue.equals(attrValue)) {
									isAllowed = true;
									break;
								}
							}
							
							System.out.println("값 '" + attrValue + "'이(가) 허용 목록에 있는가? " + isAllowed);
							System.out.println("비교 상세:");
							for (String allowedValue : allowedValues) {
								boolean matches = allowedValue.equals(attrValue);
								System.out.println("  '" + attrValue + "' == '" + allowedValue + "' ? " + matches);
								if (matches) {
									System.out.println("    (문자 길이 비교: " + attrValue.length() + " vs " + allowedValue.length() + ")");
								}
							}
							
							if (!isAllowed) {
								String errorMessage = path + " 요소의 " + attrName + " 속성 값 '" + attrValue + 
										"'이(가) 허용된 값이 아닙니다. 허용값: " + allowedValues;
								System.err.println("enum 검증 오류: " + errorMessage);
								System.err.println(">>> 라인 번호: " + lineNum + " (baseLineNum: " + baseLineNum + ")");
								if (lineNum <= 0) {
									System.err.println(">>> 경고: 라인 번호가 유효하지 않습니다. 다시 찾기를 시도합니다.");
									// 라인 번호를 다시 찾기
									int retryLineNum = findAttributeLineNumber(xmlFile, element, path, attrName, encoding);
									if (retryLineNum > 0) {
										lineNum = retryLineNum;
										System.err.println(">>> 재시도 성공: 라인 번호 " + lineNum);
									} else {
										lineNum = baseLineNum > 0 ? baseLineNum : 1;
										System.err.println(">>> 재시도 실패: 기본 라인 번호 " + lineNum + " 사용");
									}
								}
								addError(xmlFile, lineNum, -1, errorMessage);
							} else {
								System.out.println("enum 검증 통과: " + attrName + " = '" + attrValue + "'");
							}
						}
					} else {
						System.err.println("경고: enum 타입인데 allowed_values가 비어있거나 null입니다.");
					}
					System.out.println("========================================");
					// enum 검증이 있으면 format 검증은 건너뜀 (중복 방지)
				} else {
					// enum 타입이 아닌 경우에만 format 검증 수행
					String format = (String) attrRuleMap.get("format");
					if (format != null && !format.isEmpty() && !attrValue.isEmpty()) {
						System.out.println("format 검증 시작: " + path + "/@" + attrName + " = '" + attrValue + "'");
						System.out.println("format 패턴: '" + format + "'");
						try {
							java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(format);
							java.util.regex.Matcher matcher = pattern.matcher(attrValue);
							if (!matcher.matches()) {
								String errorMessage = path + " 요소의 " + attrName + " 속성 값 '" + attrValue + 
										"'이(가) 허용된 형식이 아닙니다. 형식: " + format;
								System.err.println("format 검증 오류: " + errorMessage);
								addError(xmlFile, lineNum, -1, errorMessage);
							} else {
								System.out.println("format 검증 통과: " + attrName + " = '" + attrValue + "'");
							}
						} catch (Exception e) {
							System.err.println("format 패턴 컴파일 오류: " + e.getMessage());
						}
					}
				}
				
				// fixed_length 타입 검증 (정확한 길이 검증)
				if ("fixed_length".equals(type)) {
					Object lengthObj = attrRuleMap.get("length");
					if (lengthObj != null) {
						int requiredLength = Integer.parseInt(lengthObj.toString());
						System.out.println("fixed_length 검증 시작: " + path + "/@" + attrName + 
								", 요구 길이: " + requiredLength + 
								", 실제 값: '" + attrValue + "'");
						
						if (attrValue.isEmpty()) {
							// 빈 값은 필수 검증에서 처리
							System.out.println("속성 값이 비어있음 - 필수 검증에서 처리");
						} else {
							// 실제 문자 길이 확인 (유니코드 문자 기준)
							int actualLength = attrValue.length();
							byte[] bytes = attrValue.getBytes();
							int byteLength = bytes.length;
							
							System.out.println("속성 길이 검증: 요구=" + requiredLength + 
									", 실제 문자 길이=" + actualLength + 
									", 바이트 길이=" + byteLength);
							
							// 각 문자 출력 (디버깅용)
							System.out.print("속성 값 문자 분석: ");
							for (int i = 0; i < attrValue.length(); i++) {
								char c = attrValue.charAt(i);
								System.out.print("[" + i + "]='" + c + "'(U+" + 
										Integer.toHexString(c).toUpperCase() + ") ");
							}
							System.out.println();
							
							if (actualLength != requiredLength) {
								String errorMessage = path + " 요소의 " + attrName + " 속성 값의 길이가 정확히 " + requiredLength + 
										"자여야 합니다. 현재 길이: " + actualLength + ", 값: '" + attrValue + 
										"' (바이트 길이: " + byteLength + ")";
								System.err.println("속성 길이 오류: " + errorMessage);
								addError(xmlFile, lineNum, -1, errorMessage);
							} else {
								System.out.println("속성 길이 검증 통과: " + attrName + " = '" + attrValue + "'");
							}
						}
					}
				}
				
				// max_length 타입 검증 (최대 길이 검증)
				if ("max_length".equals(type)) {
					Object lengthObj = attrRuleMap.get("length");
					if (lengthObj != null) {
						int maxLength = Integer.parseInt(lengthObj.toString());
						System.out.println("max_length 검증 시작: " + path + "/@" + attrName + 
								", 최대 길이: " + maxLength + 
								", 실제 값: '" + attrValue + "'");
						
						if (attrValue.isEmpty()) {
							// 빈 값은 필수 검증에서 처리
							System.out.println("속성 값이 비어있음 - 필수 검증에서 처리");
						} else {
							// 실제 문자 길이 확인 (유니코드 문자 기준)
							int actualLength = attrValue.length();
							byte[] bytes = attrValue.getBytes();
							int byteLength = bytes.length;
							
							System.out.println("속성 길이 검증: 최대=" + maxLength + 
									", 실제 문자 길이=" + actualLength + 
									", 바이트 길이=" + byteLength);
							
							if (actualLength > maxLength) {
								String errorMessage = path + " 요소의 " + attrName + " 속성 값의 길이가 " + maxLength + 
										"자를 초과합니다. 현재 길이: " + actualLength + ", 값: '" + attrValue + 
										"' (바이트 길이: " + byteLength + ")";
								System.err.println("속성 길이 오류: " + errorMessage);
								addError(xmlFile, lineNum, -1, errorMessage);
							} else {
								System.out.println("속성 길이 검증 통과: " + attrName + " = '" + attrValue + "' (길이: " + actualLength + ")");
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
	private void validateAllowedCodes(File xmlFile, Element element, List<String> allowedCodes, String path) {
		int lineNum = getLineNumber(element.getNodeName());
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
		System.out.println("자식 요소 검증 시작: 부모 경로 = " + parentPath);
		System.out.println("검증할 자식 요소 규칙: " + childrenRules.keySet());
		
		// 부모 요소의 모든 자식 요소 출력 (디버깅)
		NodeList allChildren = parent.getChildNodes();
		System.out.println("부모 요소 '" + parentPath + "'의 모든 자식 노드:");
		for (int i = 0; i < allChildren.getLength(); i++) {
			Node node = allChildren.item(i);
			if (node instanceof Element) {
				Element elem = (Element) node;
				String nodeName = elem.getNodeName();
				String localName = elem.getLocalName();
				System.out.println("  자식 요소[" + i + "]: nodeName='" + nodeName + 
						"', localName='" + localName + "'");
			}
		}
		
		for (Map.Entry<String, Object> entry : childrenRules.entrySet()) {
			String childName = entry.getKey();
			Map<String, Object> childRule = (Map<String, Object>) entry.getValue();
			
			System.out.println("자식 요소 '" + childName + "' 검증 시작");
			
			// 자식 요소 찾기
			List<Element> childElements = getChildElements(parent, childName);
			System.out.println("자식 요소 '" + childName + "' 발견 개수: " + childElements.size());
			
			// occurrence 검증
			String occurrence = (String) childRule.get("occurrence");
			String required = (String) childRule.get("required");
			
			validateOccurrence(xmlFile, childElements, childName, occurrence, required, parentPath);
			
			// 각 자식 요소 검증
			for (Element child : childElements) {
				String childPath = parentPath + "/" + childName;
				System.out.println("자식 요소 검증 실행: " + childPath);
				validateElement(xmlFile, child, childRule, childPath);
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
		
		System.out.println("자식 요소 찾기: 찾을 이름 = '" + childName + "', 부모 = '" + parent.getNodeName() + "'");
		
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
				
				System.out.println("  자식 요소 비교: nodeName='" + nodeName + 
						"', localName='" + localName + 
						"', compareName='" + compareName + 
						"', 찾는 이름='" + childName + 
						"', 일치=" + compareName.equals(childName));
				
				if (compareName.equals(childName)) {
					children.add(elem);
					System.out.println("  -> 일치! 추가됨");
				}
			}
		}
		
		System.out.println("자식 요소 찾기 완료: " + children.size() + "개 발견");
		return children;
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
	 * 검증 결과 오류 목록을 반환합니다.
	 */
	public List<ValidationError> getErrors() {
		return new ArrayList<>(errors);
	}
}
