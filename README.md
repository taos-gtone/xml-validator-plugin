# XML Validator Eclipse Plugin

Eclipse 플러그인으로 XML 파일의 문법 체크와 정합성 규칙 검증을 수행하는 도구입니다.

## 주요 기능

1. **XML 문법 체크**: XML 파일의 기본적인 문법 오류를 검사합니다.
2. **정합성 규칙 검증**: 엑셀 파일에서 생성된 규칙 파일을 기반으로 XML 요소 간 정합성을 검증합니다.
3. **엑셀 규칙 변환**: 엑셀 파일에서 정합성 규칙을 읽어 가독성 있는 텍스트 파일로 변환합니다.
4. **배치 검증**: 단일 XML 파일 또는 폴더를 선택하여 모든 하위 XML 파일을 일괄 검증할 수 있습니다.
5. **오류 리포트**: 검증 오류를 파일명, 라인 번호, 컬럼 번호와 함께 표시합니다.

## Eclipse 환경 구성 방법

### 1. Eclipse IDE 설치

1. [Eclipse IDE for RCP and RAP Developers](https://www.eclipse.org/downloads/packages/) 다운로드 및 설치
   - 또는 Eclipse IDE for Java Developers + 플러그인 개발 도구(Plug-in Development Environment) 설치

### 2. 플러그인 개발 환경 설정

1. Eclipse 실행 후 `Window > Preferences` 메뉴 열기
2. `Plug-in Development > Target Platform` 설정 확인
3. 필요한 경우 Java 버전 확인: `Java > Installed JREs` (Java 11 이상 권장)

### 3. 의존성 라이브러리 추가

다음 라이브러리를 `lib/` 폴더에 추가해야 합니다:

#### Apache POI (엑셀 파일 처리용)
- `poi-5.2.3.jar`
- `poi-ooxml-5.2.3.jar`
- 다운로드: https://poi.apache.org/download.html

#### Apache Commons Lang3
- `org.apache.commons.lang3_3.12.0.jar`
- 또는 Eclipse 내장 버전 사용

**라이브러리 설치 방법:**
1. Maven Central에서 JAR 파일 다운로드
2. 프로젝트의 `lib/` 폴더에 복사
3. `build.properties` 파일에서 `lib.includes` 확인

### 4. 프로젝트 임포트 및 실행

1. Eclipse에서 `File > Import > Existing Projects into Workspace` 선택
2. 프로젝트 폴더 선택
3. 프로젝트 우클릭 > `Plug-in Tools > Update Classpath...`
4. 플러그인 실행: 프로젝트 우클릭 > `Run As > Eclipse Application`

### 5. 플러그인 빌드 및 배포

**디버그 모드:**
- 프로젝트 우클릭 > `Run As > Eclipse Application`

**배포용 빌드:**
1. 프로젝트 우클릭 > `Export > Plug-in Development > Deployable plug-ins and fragments`
2. 대상 폴더 선택 및 Export 실행

## 사용 방법

### 1. 엑셀에서 규칙 파일 생성

1. `XML Validator > Generate Rules from Excel` 메뉴 선택
2. 엑셀 파일 선택 (첫 번째 시트 사용, 헤더 행 제외)
3. 출력 텍스트 파일 경로 지정
4. 변환 완료 후 생성된 `rules.txt` 파일 확인

**엑셀 파일 형식:**
| ID | Element Path | Attribute | Condition | Expected Value | Error Message |
|----|-------------|-----------|-----------|----------------|---------------|
| R001 | root/child | id | EQ | 123 | ID must be 123 |
| R002 | root/item | price | GT | 0 | Price must be greater than 0 |

### 2. XML 파일 검증

1. `XML Validator > Validate XML` 메뉴 선택
2. 검증할 XML 파일 또는 폴더 선택
3. 규칙 파일 선택 (선택 사항, 정합성 검증을 원하는 경우)
4. 검증 결과 확인:
   - `Window > Show View > Other > XML Validation Results` 뷰 열기
   - 또는 결과 다이얼로그에서 확인

### 3. 규칙 파일 편집

생성된 `rules.txt` 파일은 일반 텍스트 에디터로 편집 가능합니다:

```
[Rule R001]
Element: root/child
Attribute: id
Condition: EQ
Expected: 123
Error: ID must be 123
---
```

## 정합성 규칙 조건

지원하는 조건 타입:
- `EQ`, `EQUALS`, `==`: 값이 일치해야 함
- `NE`, `NOTEQUALS`, `!=`: 값이 일치하지 않아야 함
- `CONTAINS`: 값에 포함되어야 함
- `NOTCONTAINS`: 값에 포함되지 않아야 함
- `GT`, `GREATERTHAN`, `>`: 숫자 값이 더 커야 함
- `LT`, `LESSTHAN`, `<`: 숫자 값이 더 작아야 함
- `GE`, `GREATEREQUAL`, `>=`: 숫자 값이 크거나 같아야 함
- `LE`, `LESSEQUAL`, `<=`: 숫자 값이 작거나 같아야 함

## GitHub 버전 관리

### 초기 설정

```bash
# 저장소 초기화
git init

# 원격 저장소 추가
git remote add origin https://github.com/yourusername/xml-validator-plugin.git

# 첫 커밋
git add .
git commit -m "Initial commit: XML Validator Eclipse Plugin"

# 푸시
git push -u origin main
```

### .gitignore 설정

`.gitignore` 파일이 포함되어 있어 빌드 산출물은 제외됩니다.

### 버전 관리 항목

다음 항목이 버전 관리됩니다:
- 소스 코드 (`src/`)
- 플러그인 설정 파일 (`META-INF/`, `plugin.xml`, `build.properties`)
- 규칙 텍스트 파일 (`rules/` 폴더)
- 문서 (`README.md`)

제외 항목:
- 빌드 산출물 (`bin/`, `.classpath`, `.project` 등)
- 라이브러리 JAR 파일 (`lib/` - Maven/Gradle로 관리하는 경우)

## 프로젝트 구조

```
xml-validator-plugin/
├── META-INF/
│   └── MANIFEST.MF          # 플러그인 메타데이터
├── src/
│   └── com/xmlvalidator/
│       ├── handlers/        # 명령 핸들러
│       ├── model/           # 데이터 모델
│       ├── util/            # 유틸리티 클래스
│       ├── validators/      # 검증 로직
│       └── views/           # UI 뷰
├── rules/                   # 정합성 규칙 파일
│   ├── *.xlsx              # 엑셀 규칙 템플릿 파일
│   └── *.txt, *.rules      # 생성된 규칙 파일 (ymd 형식 권장)
├── lib/                     # 외부 라이브러리 (gitignore)
├── plugin.xml               # 플러그인 확장점 정의
├── build.properties         # 빌드 설정
└── README.md               # 프로젝트 문서
```

## 문제 해결

### 플러그인이 실행되지 않는 경우

1. `META-INF/MANIFEST.MF`에서 `Require-Bundle` 확인
2. Eclipse 버전 호환성 확인 (Eclipse 2021-12 이상 권장)
3. Java 버전 확인 (Java 11 이상 필요)

### 라이브러리 오류

1. `lib/` 폴더에 필요한 JAR 파일이 있는지 확인
2. `build.properties`의 `lib.includes` 경로 확인
3. 프로젝트 우클릭 > `Properties > Java Build Path > Libraries` 확인

### 규칙 파일 파싱 오류

1. 규칙 파일 형식 확인 (예: `[Rule ID]` 형식)
2. 엑셀 파일의 헤더 행이 첫 번째 행인지 확인
3. 빈 행이나 특수 문자가 있는지 확인

## 라이선스

이 프로젝트는 MIT 라이선스 하에 배포됩니다.
