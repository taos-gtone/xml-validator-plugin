package com.xmlvalidator.views;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ui.IEditorPart;
import org.osgi.framework.Bundle;

import com.xmlvalidator.model.ValidationError;
import com.xmlvalidator.util.YamlRuleParser;
import com.xmlvalidator.validators.ConsistencyValidator;
import com.xmlvalidator.validators.XmlSyntaxValidator;

/**
 * XML 검증 결과를 표시하는 뷰
 */
public class XmlValidationView extends ViewPart {
	
	public static final String ID = "com.xmlvalidator.views.xmlValidationView";
	
	// 기본 규칙 파일 경로
	private static final String DEFAULT_RULE_FILE = "rules/xml_validation_rules_with_codes.yaml";
	
	private TableViewer tableViewer;
	private Text xmlPathText;
	private Text rulePathText;
	private Button validateButton;
	private Label statusLabel;
	
	// 선택된 파일들 저장
	private List<File> selectedXmlFiles = new ArrayList<>();
	
	// 규칙 파서 (한 번만 로드)
	private YamlRuleParser ruleParser = null;
	private File currentRuleFile = null;
	
	@Override
	public void createPartControl(Composite parent) {
		// 레이아웃 설정
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 10;
		layout.marginHeight = 10;
		parent.setLayout(layout);
		
		// 상단 컨트롤 패널
		Composite controlPanel = new Composite(parent, SWT.NONE);
		controlPanel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		GridLayout controlLayout = new GridLayout(4, false);
		controlLayout.marginWidth = 0;
		controlLayout.marginHeight = 0;
		controlPanel.setLayout(controlLayout);
		
		// XML 파일/폴더 선택
		Label xmlLabel = new Label(controlPanel, SWT.NONE);
		xmlLabel.setText("XML 파일/폴더:");
		xmlLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		
		xmlPathText = new Text(controlPanel, SWT.BORDER);
		xmlPathText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		xmlPathText.setMessage("파일 또는 폴더 경로를 입력하거나 선택하세요");
		
		// 파일 선택 버튼
		Button selectFileButton = new Button(controlPanel, SWT.PUSH);
		selectFileButton.setText("파일 선택...");
		selectFileButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectXmlFile();
			}
		});
		
		// 폴더 선택 버튼
		Button selectFolderButton = new Button(controlPanel, SWT.PUSH);
		selectFolderButton.setText("폴더 선택...");
		selectFolderButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectXmlFolder();
			}
		});
		
		// 규칙 파일 선택
		Label ruleLabel = new Label(controlPanel, SWT.NONE);
		ruleLabel.setText("규칙 파일:");
		ruleLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
		
		rulePathText = new Text(controlPanel, SWT.BORDER | SWT.READ_ONLY);
		GridData rulePathData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		rulePathData.horizontalSpan = 2;
		rulePathText.setLayoutData(rulePathData);
		
		Button selectRuleButton = new Button(controlPanel, SWT.PUSH);
		selectRuleButton.setText("변경...");
		selectRuleButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				selectRuleFile();
			}
		});
		
		// 검증 실행 버튼
		validateButton = new Button(controlPanel, SWT.PUSH);
		validateButton.setText("검증 실행");
		validateButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));
		validateButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				performValidation();
			}
		});
		
		// 상태 레이블
		statusLabel = new Label(parent, SWT.NONE);
		statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		statusLabel.setText("XML 파일 또는 폴더를 선택하고 검증을 실행하세요.");
		
		// 기본 규칙 파일 설정 및 로드 (statusLabel 생성 후에 호출)
		loadDefaultRuleFile();
		
		// 결과 테이블
		tableViewer = new TableViewer(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER);
		tableViewer.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		
		// 테이블 컬럼 설정
		createColumns();
		
		tableViewer.setContentProvider(new ArrayContentProvider());
		tableViewer.getTable().setHeaderVisible(true);
		tableViewer.getTable().setLinesVisible(true);
		
		// 더블클릭 시 파일 열기
		tableViewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();
				Object selected = selection.getFirstElement();
				if (selected instanceof ValidationError) {
					ValidationError error = (ValidationError) selected;
					openFileInEditor(error.getFile(), error.getLineNumber());
				}
			}
		});
	}
	
	/**
	 * 파일을 편집기에서 열고 특정 라인으로 이동합니다.
	 */
	private void openFileInEditor(File file, int lineNumber) {
		try {
			IFileStore fileStore = EFS.getLocalFileSystem().getStore(file.toURI());
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			
			if (page != null) {
				IEditorPart editor = IDE.openEditorOnFileStore(page, fileStore);
				
				// 라인 번호가 유효하면 해당 라인으로 이동
				if (lineNumber > 0 && editor instanceof ITextEditor) {
					ITextEditor textEditor = (ITextEditor) editor;
					IDocument document = textEditor.getDocumentProvider()
							.getDocument(textEditor.getEditorInput());
					
					if (document != null) {
						try {
							// 라인 번호는 1부터 시작하지만 getLineOffset은 0부터 시작
							int offset = document.getLineOffset(lineNumber - 1);
							textEditor.selectAndReveal(offset, 0);
						} catch (Exception e) {
							System.err.println("라인 이동 오류: " + e.getMessage());
						}
					}
				}
			}
		} catch (PartInitException e) {
			MessageDialog.openError(getSite().getShell(), "오류", 
					"파일을 열 수 없습니다: " + e.getMessage());
		}
	}
	
	/**
	 * 기본 규칙 파일 설정 및 로드
	 */
	private void loadDefaultRuleFile() {
		File defaultFile = null;
		List<String> searchedPaths = new ArrayList<>();
		
		// 1. 절대 경로로 직접 시도 (가장 확실한 방법)
		File directFile = new File("C:\\Users\\230228\\xml-validator-plugin\\rules\\xml_validation_rules_with_codes.yaml");
		searchedPaths.add("직접경로: " + directFile.getAbsolutePath() + " (exists=" + directFile.exists() + ")");
		if (directFile.exists()) {
			defaultFile = directFile;
		}
		
		// 2. 클래스 파일 위치 기준으로 찾기
		if (defaultFile == null) {
			try {
				java.net.URL classUrl = getClass().getProtectionDomain().getCodeSource().getLocation();
				if (classUrl != null) {
					File classDir = new File(classUrl.toURI());
					searchedPaths.add("클래스URL: " + classUrl.toString());
					// bin 폴더인 경우 상위로 이동
					if (classDir.isDirectory() && classDir.getName().equals("bin")) {
						classDir = classDir.getParentFile();
					}
					// jar 파일인 경우 상위 폴더로 이동
					if (classDir.isFile() && classDir.getName().endsWith(".jar")) {
						classDir = classDir.getParentFile();
					}
					File candidate = new File(classDir, DEFAULT_RULE_FILE);
					searchedPaths.add("클래스위치: " + candidate.getAbsolutePath() + " (exists=" + candidate.exists() + ")");
					if (candidate.exists()) {
						defaultFile = candidate;
					}
				}
			} catch (Exception e) {
				searchedPaths.add("클래스위치 오류: " + e.getMessage());
			}
		}
		
		// 3. 플러그인 번들 위치에서 찾기
		if (defaultFile == null) {
			try {
				Bundle bundle = Platform.getBundle("com.xmlvalidator");
				if (bundle != null) {
					java.net.URL bundleUrl = bundle.getEntry("/");
					if (bundleUrl != null) {
						java.net.URL fileUrl = FileLocator.toFileURL(bundleUrl);
						File bundleDir = new File(fileUrl.toURI());
						File candidate = new File(bundleDir, DEFAULT_RULE_FILE);
						searchedPaths.add("번들: " + candidate.getAbsolutePath() + " (exists=" + candidate.exists() + ")");
						if (candidate.exists()) {
							defaultFile = candidate;
						}
					}
				} else {
					searchedPaths.add("번들: com.xmlvalidator 번들을 찾을 수 없음");
				}
			} catch (Exception e) {
				searchedPaths.add("번들 오류: " + e.getMessage());
			}
		}
		
		// 4. 워크스페이스의 프로젝트에서 찾기
		if (defaultFile == null) {
			try {
				IWorkspace workspace = ResourcesPlugin.getWorkspace();
				IWorkspaceRoot root = workspace.getRoot();
				
				// xml-validator-plugin 프로젝트 찾기
				IProject project = root.getProject("xml-validator-plugin");
				if (project != null && project.exists() && project.getLocation() != null) {
					String projectPath = project.getLocation().toOSString();
					File candidate = new File(projectPath, DEFAULT_RULE_FILE);
					searchedPaths.add("프로젝트: " + candidate.getAbsolutePath() + " (exists=" + candidate.exists() + ")");
					if (candidate.exists()) {
						defaultFile = candidate;
					}
				} else {
					searchedPaths.add("프로젝트: xml-validator-plugin 프로젝트를 찾을 수 없음");
				}
				
				// 프로젝트를 못 찾으면 워크스페이스 루트에서 찾기
				if (defaultFile == null && root.getLocation() != null) {
					String workspacePath = root.getLocation().toOSString();
					File candidate = new File(workspacePath, "xml-validator-plugin/" + DEFAULT_RULE_FILE);
					searchedPaths.add("워크스페이스: " + candidate.getAbsolutePath() + " (exists=" + candidate.exists() + ")");
					if (candidate.exists()) {
						defaultFile = candidate;
					}
				}
			} catch (Exception e) {
				searchedPaths.add("워크스페이스 오류: " + e.getMessage());
			}
		}
		
		// 6. 현재 디렉토리 기준으로 찾기
		if (defaultFile == null) {
			File candidate = new File(DEFAULT_RULE_FILE);
			searchedPaths.add("상대경로: " + candidate.getAbsolutePath() + " (exists=" + candidate.exists() + ")");
			if (candidate.exists()) {
				defaultFile = candidate;
			}
		}
		
		// 검색 결과 로그 출력
		System.out.println("======================================");
		System.out.println("규칙 파일 검색 결과:");
		for (String path : searchedPaths) {
			System.out.println("  - " + path);
		}
		System.out.println("======================================");
		
		// 파일 로드
		if (defaultFile != null && defaultFile.exists()) {
			System.out.println("규칙 파일 발견: " + defaultFile.getAbsolutePath());
			loadRuleFile(defaultFile);
		} else {
			rulePathText.setText("(규칙 파일 없음)");
			statusLabel.setText("규칙 파일을 찾을 수 없습니다. '변경...' 버튼을 클릭하여 선택하세요.");
		}
	}
	
	/**
	 * 규칙 파일을 로드합니다.
	 */
	private void loadRuleFile(File ruleFile) {
		System.out.println("======================================");
		System.out.println("규칙 파일 로드 시도: " + ruleFile.getAbsolutePath());
		System.out.println("파일 존재 여부: " + ruleFile.exists());
		System.out.println("파일 읽기 가능: " + ruleFile.canRead());
		System.out.println("파일 크기: " + ruleFile.length() + " bytes");
		System.out.println("======================================");
		
		try {
			ruleParser = new YamlRuleParser();
			ruleParser.parse(ruleFile);
			currentRuleFile = ruleFile;
			
			// 파일명만 표시
			rulePathText.setText(ruleFile.getName());
			statusLabel.setText("규칙 파일 로드 완료: " + ruleFile.getName());
			System.out.println("규칙 파일 로드 성공!");
			System.out.println("파싱된 규칙 수: " + (ruleParser.getRules() != null ? ruleParser.getRules().size() : 0));
			System.out.println("파싱된 코드값 수: " + (ruleParser.getCodeValues() != null ? ruleParser.getCodeValues().size() : 0));
		} catch (Exception e) {
			ruleParser = null;
			currentRuleFile = null;
			rulePathText.setText("(로드 실패)");
			String errorMsg = "규칙 파일 로드 실패:\n" + ruleFile.getAbsolutePath() + "\n\n오류: " + e.getMessage();
			System.err.println("======================================");
			System.err.println("규칙 파일 로드 실패!");
			System.err.println("파일: " + ruleFile.getAbsolutePath());
			System.err.println("오류: " + e.getMessage());
			e.printStackTrace();
			System.err.println("======================================");
			MessageDialog.openError(getSite().getShell(), "규칙 파일 로드 오류", errorMsg);
		}
	}
	
	private void createColumns() {
		// 오류 유형 컬럼
		TableViewerColumn typeColumn = new TableViewerColumn(tableViewer, SWT.NONE);
		typeColumn.getColumn().setWidth(100);
		typeColumn.getColumn().setText("유형");
		typeColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof ValidationError) {
					ValidationError error = (ValidationError) element;
					if (error.getErrorType() != null) {
						return error.getErrorType().getDescription();
					}
				}
				return "오류";
			}
			
			@Override
			public Color getForeground(Object element) {
				if (element instanceof ValidationError) {
					ValidationError error = (ValidationError) element;
					if (error.getErrorType() == ValidationError.ErrorType.SYNTAX) {
						return Display.getCurrent().getSystemColor(SWT.COLOR_RED);
					} else if (error.getErrorType() == ValidationError.ErrorType.CONSISTENCY) {
						return Display.getCurrent().getSystemColor(SWT.COLOR_DARK_YELLOW);
					}
				}
				return null;
			}
		});
		
		// 파일명 컬럼
		TableViewerColumn fileColumn = new TableViewerColumn(tableViewer, SWT.NONE);
		fileColumn.getColumn().setWidth(200);
		fileColumn.getColumn().setText("파일");
		fileColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof ValidationError) {
					return ((ValidationError) element).getFile().getName();
				}
				return "";
			}
		});
		
		// 라인 번호 컬럼
		TableViewerColumn lineColumn = new TableViewerColumn(tableViewer, SWT.NONE);
		lineColumn.getColumn().setWidth(60);
		lineColumn.getColumn().setText("라인");
		lineColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof ValidationError) {
					int line = ((ValidationError) element).getLineNumber();
					return line > 0 ? String.valueOf(line) : "-";
				}
				return "";
			}
		});
		
		// 오류 메시지 컬럼
		TableViewerColumn messageColumn = new TableViewerColumn(tableViewer, SWT.NONE);
		messageColumn.getColumn().setWidth(500);
		messageColumn.getColumn().setText("오류 메시지");
		messageColumn.setLabelProvider(new ColumnLabelProvider() {
			@Override
			public String getText(Object element) {
				if (element instanceof ValidationError) {
					return ((ValidationError) element).getMessage();
				}
				return "";
			}
		});
	}
	
	/**
	 * XML 파일 선택
	 */
	private void selectXmlFile() {
		FileDialog dialog = new FileDialog(getSite().getShell(), SWT.OPEN | SWT.MULTI);
		dialog.setFilterExtensions(new String[] { "*.xml", "*.*" });
		dialog.setFilterNames(new String[] { "XML Files (*.xml)", "All Files (*.*)" });
		dialog.setText("XML 파일 선택");
		
		String firstFile = dialog.open();
		if (firstFile != null) {
			String[] fileNames = dialog.getFileNames();
			String filterPath = dialog.getFilterPath();
			
			selectedXmlFiles.clear();
			for (String fileName : fileNames) {
				selectedXmlFiles.add(new File(filterPath, fileName));
			}
			
			if (fileNames.length == 1) {
				xmlPathText.setText(selectedXmlFiles.get(0).getAbsolutePath());
			} else {
				xmlPathText.setText(filterPath);
			}
			
			statusLabel.setText(selectedXmlFiles.size() + "개 XML 파일이 선택되었습니다.");
		}
	}
	
	/**
	 * XML 폴더 선택 (하위 폴더 포함)
	 */
	private void selectXmlFolder() {
		DirectoryDialog dialog = new DirectoryDialog(getSite().getShell(), SWT.OPEN);
		dialog.setText("XML 파일이 있는 폴더 선택");
		dialog.setMessage("XML 파일이 포함된 폴더를 선택하세요.\n(하위 폴더의 모든 XML 파일이 포함됩니다)");
		
		String selectedPath = dialog.open();
		if (selectedPath != null) {
			xmlPathText.setText(selectedPath);
			updateSelectedFiles(selectedPath);
		}
	}
	
	/**
	 * 경로에서 XML 파일들을 수집하고 상태를 업데이트합니다.
	 */
	private void updateSelectedFiles(String path) {
		selectedXmlFiles.clear();
		
		if (path == null || path.trim().isEmpty()) {
			statusLabel.setText("XML 파일 또는 폴더를 선택하고 검증을 실행하세요.");
			return;
		}
		
		File selected = new File(path.trim());
		
		if (!selected.exists()) {
			statusLabel.setText("경로가 존재하지 않습니다: " + path);
			return;
		}
		
		if (selected.isFile()) {
			if (selected.getName().toLowerCase().endsWith(".xml")) {
				selectedXmlFiles.add(selected);
				statusLabel.setText("1개 XML 파일이 선택되었습니다.");
			} else {
				statusLabel.setText("선택한 파일이 XML 파일이 아닙니다.");
			}
		} else if (selected.isDirectory()) {
			collectXmlFiles(selected, selectedXmlFiles);
			if (selectedXmlFiles.isEmpty()) {
				statusLabel.setText("선택한 폴더에 XML 파일이 없습니다.");
			} else {
				statusLabel.setText(selectedXmlFiles.size() + "개 XML 파일이 발견되었습니다. (하위 폴더 포함)");
			}
		}
	}
	
	/**
	 * 규칙 파일 선택
	 */
	private void selectRuleFile() {
		FileDialog dialog = new FileDialog(getSite().getShell(), SWT.OPEN);
		dialog.setFilterExtensions(new String[] { "*.yaml", "*.yml", "*.*" });
		dialog.setFilterNames(new String[] { "YAML Files (*.yaml, *.yml)", "All Files (*.*)" });
		dialog.setText("규칙 파일 선택");
		
		// 현재 규칙 파일이 있으면 해당 경로를 기본으로 설정
		if (currentRuleFile != null && currentRuleFile.getParentFile() != null) {
			dialog.setFilterPath(currentRuleFile.getParentFile().getAbsolutePath());
		}
		
		String ruleFilePath = dialog.open();
		if (ruleFilePath != null) {
			File newRuleFile = new File(ruleFilePath);
			if (newRuleFile.exists()) {
				loadRuleFile(newRuleFile);
			}
		}
	}
	
	/**
	 * 검증 수행
	 */
	private void performValidation() {
		// XML 파일 확인 - 항상 경로에서 다시 수집
		String xmlPath = xmlPathText.getText().trim();
		if (xmlPath.isEmpty()) {
			MessageDialog.openWarning(getSite().getShell(), "경고", 
					"XML 파일 또는 폴더를 선택해주세요.");
			return;
		}
		
		// 경로에서 파일 수집
		updateSelectedFiles(xmlPath);
		
		if (selectedXmlFiles.isEmpty()) {
			MessageDialog.openWarning(getSite().getShell(), "경고", 
					"선택한 경로에 XML 파일이 없습니다.");
			return;
		}
		
		// 규칙 파일 확인 (이미 로드된 파서 사용)
		if (ruleParser == null) {
			MessageDialog.openWarning(getSite().getShell(), "경고", 
					"규칙 파일이 로드되지 않았습니다. 규칙 파일을 선택해주세요.");
			return;
		}
		
		// 디버깅: 로드된 규칙 정보 출력
		System.out.println("======================================");
		System.out.println("검증 시작");
		System.out.println("XML 파일 수: " + selectedXmlFiles.size());
		System.out.println("규칙 파서 상태: " + (ruleParser != null ? "로드됨" : "없음"));
		if (ruleParser != null) {
			System.out.println("파싱된 규칙: " + ruleParser.getRules());
			System.out.println("파싱된 규칙 키: " + ruleParser.getRules().keySet());
		}
		System.out.println("======================================");
		
		// 검증 수행
		List<ValidationError> allErrors = new ArrayList<>();
		XmlSyntaxValidator syntaxValidator = new XmlSyntaxValidator();
		int validCount = 0;
		int invalidCount = 0;
		
		statusLabel.setText("검증 중... (0/" + selectedXmlFiles.size() + ")");
		
		for (int i = 0; i < selectedXmlFiles.size(); i++) {
			File xmlFile = selectedXmlFiles.get(i);
			boolean hasError = false;
			
			// 1. 문법 체크
			boolean syntaxValid = syntaxValidator.validate(xmlFile);
			if (!syntaxValid) {
				for (ValidationError error : syntaxValidator.getErrors()) {
					allErrors.add(new ValidationError(
							error.getFile(), 
							error.getLineNumber(), 
							error.getColumnNumber(), 
							error.getMessage(),
							ValidationError.ErrorType.SYNTAX));
				}
				hasError = true;
			}
			
			// 2. 정합성 체크 (규칙 파일이 있고 문법 오류가 없는 경우)
			if (ruleParser != null && syntaxValid) {
				ConsistencyValidator consistencyValidator = new ConsistencyValidator(ruleParser);
				boolean consistencyValid = consistencyValidator.validate(xmlFile);
				if (!consistencyValid) {
					for (ValidationError error : consistencyValidator.getErrors()) {
						allErrors.add(new ValidationError(
								error.getFile(), 
								error.getLineNumber(), 
								error.getColumnNumber(), 
								error.getMessage(),
								ValidationError.ErrorType.CONSISTENCY));
					}
					hasError = true;
				}
			}
			
			if (hasError) {
				invalidCount++;
			} else {
				validCount++;
			}
			
			// 진행 상태 업데이트
			final int progress = i + 1;
			Display.getCurrent().asyncExec(() -> {
				if (!statusLabel.isDisposed()) {
					statusLabel.setText("검증 중... (" + progress + "/" + selectedXmlFiles.size() + ")");
				}
			});
		}
		
		// 결과 표시
		tableViewer.setInput(allErrors);
		
		// 상태 메시지 업데이트
		String statusMessage = String.format("검증 완료: 총 %d개 파일 (성공: %d, 실패: %d, 오류: %d건)",
				selectedXmlFiles.size(), validCount, invalidCount, allErrors.size());
		statusLabel.setText(statusMessage);
		
		// 결과 메시지
		if (allErrors.isEmpty()) {
			MessageDialog.openInformation(getSite().getShell(), "검증 완료", 
					"모든 " + selectedXmlFiles.size() + "개 XML 파일이 검증을 통과했습니다.");
		} else {
			MessageDialog.openWarning(getSite().getShell(), "검증 완료", 
					selectedXmlFiles.size() + "개 파일 중 " + invalidCount + "개 파일에서 " + 
					allErrors.size() + "건의 오류가 발견되었습니다.");
		}
	}
	
	/**
	 * 폴더에서 모든 XML 파일을 재귀적으로 수집합니다.
	 */
	private void collectXmlFiles(File directory, List<File> xmlFiles) {
		if (!directory.isDirectory()) {
			return;
		}
		
		File[] files = directory.listFiles();
		if (files == null) {
			return;
		}
		
		for (File file : files) {
			if (file.isFile() && file.getName().toLowerCase().endsWith(".xml")) {
				xmlFiles.add(file);
			} else if (file.isDirectory()) {
				collectXmlFiles(file, xmlFiles);
			}
		}
	}
	
	/**
	 * 검증 결과를 업데이트합니다.
	 */
	public void updateResults(List<File> xmlFiles, List<ValidationError> errors) {
		if (tableViewer != null && !tableViewer.getControl().isDisposed()) {
			tableViewer.setInput(errors);
		}
	}
	
	@Override
	public void setFocus() {
		if (validateButton != null && !validateButton.isDisposed()) {
			validateButton.setFocus();
		}
	}
}
