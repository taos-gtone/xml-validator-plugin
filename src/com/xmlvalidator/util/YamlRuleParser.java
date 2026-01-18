package com.xmlvalidator.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YAML 형식의 정합성 규칙 파일을 파싱하는 클래스
 */
public class YamlRuleParser {
	
	private Map<String, Object> rules;
	private Map<String, Map<String, Object>> codeValues;
	private String encoding = "UTF-8";
	
	public YamlRuleParser() {
		this.rules = new HashMap<>();
		this.codeValues = new HashMap<>();
	}
	
	/**
	 * YAML 규칙 파일을 파싱합니다.
	 * @param yamlFile YAML 파일
	 * @throws IOException 파일 읽기 오류
	 */
	public void parse(File yamlFile) throws IOException {
		rules.clear();
		codeValues.clear();
		
		// 먼저 인코딩 확인
		detectEncoding(yamlFile);
		
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(yamlFile), encoding))) {
			
			List<String> lines = new ArrayList<>();
			String line;
			while ((line = reader.readLine()) != null) {
				lines.add(line);
			}
			
			parseLines(lines);
		}
	}
	
	/**
	 * 파일의 인코딩을 감지합니다.
	 */
	private void detectEncoding(File file) throws IOException {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.trim().startsWith("encoding:")) {
					String enc = line.substring(line.indexOf(":") + 1).trim();
					if (enc.startsWith("'") || enc.startsWith("\"")) {
						enc = enc.substring(1, enc.length() - 1);
					}
					if (!enc.isEmpty()) {
						this.encoding = enc;
					}
					break;
				}
			}
		}
	}
	
	/**
	 * 라인들을 파싱합니다.
	 */
	private void parseLines(List<String> lines) {
		String currentSection = null;
		int i = 0;
		
		while (i < lines.size()) {
			String line = lines.get(i);
			String trimmed = line.trim();
			
			// 빈 줄이나 주석 건너뛰기
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				i++;
				continue;
			}
			
			// 최상위 섹션 확인
			if (!line.startsWith(" ") && !line.startsWith("\t")) {
				if (trimmed.startsWith("rules:")) {
					currentSection = "rules";
				} else if (trimmed.startsWith("code_values:")) {
					currentSection = "code_values";
				} else if (trimmed.startsWith("version:")) {
					// 버전 정보 저장 - 콜론 뒤의 값만 추출
					String verValue = trimmed.substring(trimmed.indexOf(":") + 1).trim();
					rules.put("version", extractValue(verValue));
				} else if (trimmed.startsWith("encoding:")) {
					// 인코딩 정보 저장 - 콜론 뒤의 값만 추출
					String encValue = trimmed.substring(trimmed.indexOf(":") + 1).trim();
					encoding = extractValue(encValue);
				}
			}
			
			i++;
		}
		
		// 간단한 파싱 - 실제로는 더 정교한 YAML 파서가 필요할 수 있음
		parseRulesSection(lines);
		parseCodeValuesSection(lines);
	}
	
	/**
	 * rules 섹션을 파싱합니다.
	 */
	private void parseRulesSection(List<String> lines) {
		boolean inRules = false;
		int baseIndent = -1;
		
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			String trimmed = line.trim();
			
			if (trimmed.equals("rules:")) {
				inRules = true;
				continue;
			}
			
			if (inRules) {
				if (!line.startsWith(" ") && !line.startsWith("\t") && !trimmed.isEmpty()) {
					// 다른 최상위 섹션 시작
					if (trimmed.startsWith("code_values:")) {
						break;
					}
				}
				
				// STR 요소 파싱
				if (trimmed.startsWith("STR:")) {
					Map<String, Object> strRule = parseElement(lines, i + 1, getIndent(lines.get(i)) + 2);
					rules.put("STR", strRule);
				}
			}
		}
	}
	
	/**
	 * 요소를 재귀적으로 파싱합니다.
	 */
	private Map<String, Object> parseElement(List<String> lines, int startIndex, int expectedIndent) {
		Map<String, Object> element = new HashMap<>();
		
		for (int i = startIndex; i < lines.size(); i++) {
			String line = lines.get(i);
			String trimmed = line.trim();
			
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			
			int indent = getIndent(line);
			
			// 들여쓰기가 줄어들면 현재 요소 파싱 종료
			if (indent < expectedIndent && !trimmed.isEmpty()) {
				break;
			}
			
			if (indent == expectedIndent) {
				// 속성 파싱
				if (trimmed.contains(":")) {
					String key = trimmed.substring(0, trimmed.indexOf(":")).trim();
					String value = trimmed.substring(trimmed.indexOf(":") + 1).trim();
					
					if (key.equals("children") || key.equals("attributes")) {
						// 하위 요소 파싱
						Map<String, Object> children = parseChildren(lines, i + 1, indent + 2);
						element.put(key, children);
					} else if (key.equals("allowed_values") || key.equals("allowed_codes")) {
						// 리스트 파싱
						List<String> list = parseList(lines, i + 1, indent + 2);
						element.put(key, list);
					} else if (key.equals("code_labels")) {
						// 코드 레이블 맵 파싱
						Map<String, String> labels = parseCodeLabels(lines, i + 1, indent + 2);
						element.put(key, labels);
					} else {
						element.put(key, value.isEmpty() ? "" : extractValue(value));
					}
				}
			}
		}
		
		return element;
	}
	
	/**
	 * children 섹션을 파싱합니다.
	 */
	private Map<String, Object> parseChildren(List<String> lines, int startIndex, int expectedIndent) {
		Map<String, Object> children = new HashMap<>();
		
		for (int i = startIndex; i < lines.size(); i++) {
			String line = lines.get(i);
			String trimmed = line.trim();
			
			if (trimmed.isEmpty() || trimmed.startsWith("#")) {
				continue;
			}
			
			int indent = getIndent(line);
			
			if (indent < expectedIndent && !trimmed.isEmpty()) {
				break;
			}
			
			if (indent == expectedIndent && trimmed.contains(":")) {
				String key = trimmed.substring(0, trimmed.indexOf(":")).trim();
				Map<String, Object> child = parseElement(lines, i + 1, indent + 2);
				children.put(key, child);
			}
		}
		
		return children;
	}
	
	/**
	 * 리스트를 파싱합니다.
	 */
	private List<String> parseList(List<String> lines, int startIndex, int expectedIndent) {
		List<String> list = new ArrayList<>();
		
		for (int i = startIndex; i < lines.size(); i++) {
			String line = lines.get(i);
			String trimmed = line.trim();
			
			if (trimmed.isEmpty()) {
				continue;
			}
			
			int indent = getIndent(line);
			
			if (indent < expectedIndent && !trimmed.isEmpty()) {
				break;
			}
			
			if (trimmed.startsWith("- ")) {
				String value = trimmed.substring(2).trim();
				list.add(extractValue(value));
			}
		}
		
		return list;
	}
	
	/**
	 * 코드 레이블 맵을 파싱합니다.
	 */
	private Map<String, String> parseCodeLabels(List<String> lines, int startIndex, int expectedIndent) {
		Map<String, String> labels = new HashMap<>();
		
		for (int i = startIndex; i < lines.size(); i++) {
			String line = lines.get(i);
			String trimmed = line.trim();
			
			if (trimmed.isEmpty()) {
				continue;
			}
			
			int indent = getIndent(line);
			
			if (indent < expectedIndent && !trimmed.isEmpty()) {
				break;
			}
			
			if (trimmed.contains(":")) {
				String key = trimmed.substring(0, trimmed.indexOf(":")).trim();
				String value = trimmed.substring(trimmed.indexOf(":") + 1).trim();
				labels.put(extractValue(key), extractValue(value));
			}
		}
		
		return labels;
	}
	
	/**
	 * code_values 섹션을 파싱합니다.
	 */
	private void parseCodeValuesSection(List<String> lines) {
		boolean inCodeValues = false;
		
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			String trimmed = line.trim();
			
			if (trimmed.equals("code_values:")) {
				inCodeValues = true;
				continue;
			}
			
			if (inCodeValues) {
				int indent = getIndent(line);
				
				// 최상위 코드 카테고리
				if (indent == 2 && trimmed.contains(":")) {
					String category = trimmed.substring(0, trimmed.indexOf(":")).trim();
					Map<String, Object> codes = parseCodeCategory(lines, i + 1, indent + 2);
					codeValues.put(extractValue(category), codes);
				}
			}
		}
	}
	
	/**
	 * 코드 카테고리를 파싱합니다.
	 */
	private Map<String, Object> parseCodeCategory(List<String> lines, int startIndex, int expectedIndent) {
		Map<String, Object> codes = new HashMap<>();
		
		for (int i = startIndex; i < lines.size(); i++) {
			String line = lines.get(i);
			String trimmed = line.trim();
			
			if (trimmed.isEmpty()) {
				continue;
			}
			
			int indent = getIndent(line);
			
			if (indent < expectedIndent && !trimmed.isEmpty()) {
				break;
			}
			
			if (indent == expectedIndent && trimmed.contains(":")) {
				String code = trimmed.substring(0, trimmed.indexOf(":")).trim();
				// 하위 값들 파싱 (general, card 등)
				Map<String, String> values = new HashMap<>();
				
				for (int j = i + 1; j < lines.size(); j++) {
					String subLine = lines.get(j);
					String subTrimmed = subLine.trim();
					int subIndent = getIndent(subLine);
					
					if (subIndent <= indent && !subTrimmed.isEmpty()) {
						break;
					}
					
					if (subTrimmed.contains(":")) {
						String key = subTrimmed.substring(0, subTrimmed.indexOf(":")).trim();
						String value = subTrimmed.substring(subTrimmed.indexOf(":") + 1).trim();
						values.put(key, extractValue(value));
					}
				}
				
				codes.put(extractValue(code), values);
			}
		}
		
		return codes;
	}
	
	/**
	 * 들여쓰기 레벨을 계산합니다.
	 */
	private int getIndent(String line) {
		int indent = 0;
		for (char c : line.toCharArray()) {
			if (c == ' ') {
				indent++;
			} else if (c == '\t') {
				indent += 2;
			} else {
				break;
			}
		}
		return indent;
	}
	
	/**
	 * 값에서 따옴표를 제거합니다.
	 */
	private String extractValue(String value) {
		if (value == null) {
			return "";
		}
		value = value.trim();
		if ((value.startsWith("'") && value.endsWith("'")) ||
			(value.startsWith("\"") && value.endsWith("\""))) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}
	
	/**
	 * 파싱된 규칙을 반환합니다.
	 */
	public Map<String, Object> getRules() {
		return rules;
	}
	
	/**
	 * 파싱된 코드 값을 반환합니다.
	 */
	public Map<String, Map<String, Object>> getCodeValues() {
		return codeValues;
	}
	
	/**
	 * 인코딩을 반환합니다.
	 */
	public String getEncoding() {
		return encoding;
	}
	
	/**
	 * 특정 코드의 유효성을 검사합니다.
	 * @param category 코드 카테고리 (예: "실명번호구분")
	 * @param code 검사할 코드
	 * @return 유효하면 true
	 */
	public boolean isValidCode(String category, String code) {
		Map<String, Object> categoryMap = codeValues.get(category);
		if (categoryMap == null) {
			return true; // 카테고리가 없으면 검증 통과
		}
		return categoryMap.containsKey(code);
	}
	
	/**
	 * 코드의 레이블을 반환합니다.
	 * @param category 코드 카테고리
	 * @param code 코드
	 * @return 레이블 또는 null
	 */
	@SuppressWarnings("unchecked")
	public String getCodeLabel(String category, String code) {
		Map<String, Object> categoryMap = codeValues.get(category);
		if (categoryMap == null) {
			return null;
		}
		Object value = categoryMap.get(code);
		if (value instanceof Map) {
			Map<String, String> labels = (Map<String, String>) value;
			return labels.get("general");
		}
		return null;
	}
}
