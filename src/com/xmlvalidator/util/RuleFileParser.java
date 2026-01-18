package com.xmlvalidator.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.xmlvalidator.model.ConsistencyRule;

/**
 * 텍스트 파일에서 정합성 규칙을 파싱하는 유틸리티 클래스
 */
public class RuleFileParser {
	
	private static final Pattern RULE_ID_PATTERN = Pattern.compile("\\[Rule\\s+(.+?)\\]");
	
	/**
	 * 텍스트 파일에서 규칙 목록을 읽습니다.
	 * @param ruleFile 규칙 파일
	 * @return 규칙 목록
	 * @throws IOException 파일 읽기 오류
	 */
	public List<ConsistencyRule> parseRules(File ruleFile) throws IOException {
		List<ConsistencyRule> rules = new ArrayList<>();
		
		try (BufferedReader reader = new BufferedReader(new FileReader(ruleFile))) {
			String line;
			String currentId = null;
			String elementPath = null;
			String attribute = null;
			String condition = null;
			String expectedValue = null;
			String errorMessage = null;
			
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				
				// 주석이나 빈 줄 건너뛰기
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				
				// 규칙 ID 파싱
				Matcher idMatcher = RULE_ID_PATTERN.matcher(line);
				if (idMatcher.matches()) {
					// 이전 규칙 저장
					if (currentId != null) {
						rules.add(new ConsistencyRule(currentId, elementPath, attribute, 
								condition, expectedValue, errorMessage));
					}
					
					// 새 규칙 시작
					currentId = idMatcher.group(1);
					elementPath = null;
					attribute = null;
					condition = null;
					expectedValue = null;
					errorMessage = null;
					continue;
				}
				
				// 규칙 필드 파싱
				if (line.startsWith("Element:")) {
					elementPath = line.substring(8).trim();
				} else if (line.startsWith("Attribute:")) {
					attribute = line.substring(10).trim();
				} else if (line.startsWith("Condition:")) {
					condition = line.substring(10).trim();
				} else if (line.startsWith("Expected:")) {
					expectedValue = line.substring(9).trim();
				} else if (line.startsWith("Error:")) {
					errorMessage = line.substring(6).trim();
				} else if (line.equals("---")) {
					// 규칙 종료 표시 - 규칙 저장
					if (currentId != null) {
						rules.add(new ConsistencyRule(currentId, elementPath, attribute, 
								condition, expectedValue, errorMessage));
						currentId = null;
					}
				}
			}
			
			// 마지막 규칙 저장
			if (currentId != null) {
				rules.add(new ConsistencyRule(currentId, elementPath, attribute, 
						condition, expectedValue, errorMessage));
			}
		}
		
		return rules;
	}
}
