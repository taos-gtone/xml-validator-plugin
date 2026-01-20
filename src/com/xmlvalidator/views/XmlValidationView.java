package com.xmlvalidator.views;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
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
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IWorkbenchPart;
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
	private Combo xmlPathCombo;
	private Text rulePathText;
	private Button validateButton;
	private Button cancelButton;
	private Label statusLabel;
	private org.eclipse.swt.widgets.ProgressBar progressBar;
	
	// 검증 중단 플래그
	private volatile boolean validationCancelled = false;
	// 검증 완료 플래그
	private volatile boolean validationCompleted = false;
	
	// 선택된 파일들 저장
	private List<File> selectedXmlFiles = new ArrayList<>();
	
	// XML 경로 히스토리 (최근 선택한 경로들)
	private List<String> xmlPathHistory = new ArrayList<>();
	private static final int MAX_HISTORY_SIZE = 20;
	
	// 모든 오류 메시지 저장 (누적)
	private List<ValidationError> allValidationErrors = new ArrayList<>();
	
	// 규칙 파서 (한 번만 로드)
	private YamlRuleParser ruleParser = null;
	private File currentRuleFile = null;
	
	// 파일별 마지막 수정 시간 추적 (파일 경로 -> 마지막 수정 시간)
	private Map<String, Long> fileLastModifiedMap = new HashMap<>();
	
	// 검증 시작 시간 및 진행 상태
	private long validationStartTime = 0;
	private int currentProgress = 0;
	
	@Override
	public void createPartControl(Composite parent) {
		try {
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
		
		xmlPathCombo = new Combo(controlPanel, SWT.BORDER | SWT.DROP_DOWN);
		xmlPathCombo.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		xmlPathCombo.setToolTipText("파일 또는 폴더 경로를 입력하거나 이전에 선택한 경로를 선택하세요");
		xmlPathCombo.setVisibleItemCount(10);
		
		// Combo에서 항목 선택 시 파일 목록 업데이트
		xmlPathCombo.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String selectedPath = xmlPathCombo.getText();
				if (selectedPath != null && !selectedPath.trim().isEmpty()) {
					updateSelectedFiles(selectedPath);
				}
			}
		});
		
		// Enter 키 입력 시에도 파일 목록 업데이트
		xmlPathCombo.addTraverseListener(new TraverseListener() {
			@Override
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_RETURN) {
					String path = xmlPathCombo.getText();
					if (path != null && !path.trim().isEmpty()) {
						updateSelectedFiles(path);
						e.doit = false; // 기본 동작 방지
					}
				}
			}
		});
		
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
		
		// 검증 실행 및 중단 버튼을 담을 Composite
		Composite buttonPanel = new Composite(controlPanel, SWT.NONE);
		buttonPanel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 4, 1));
		GridLayout buttonLayout = new GridLayout(2, false);
		buttonLayout.marginWidth = 0;
		buttonLayout.marginHeight = 0;
		buttonLayout.horizontalSpacing = 5;
		buttonPanel.setLayout(buttonLayout);
		
		// 검증 실행 버튼
		validateButton = new Button(buttonPanel, SWT.PUSH);
		validateButton.setText("검증 실행");
		validateButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		validateButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				performValidation();
			}
		});
		
		// 검증 중단 버튼
		cancelButton = new Button(buttonPanel, SWT.PUSH);
		cancelButton.setText("검증 중단");
		cancelButton.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		cancelButton.setEnabled(false); // 초기에는 비활성화
		cancelButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				cancelValidation();
			}
		});
		
		// 상태 레이블과 Progress Bar를 담을 Composite
		Composite statusPanel = new Composite(parent, SWT.NONE);
		statusPanel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		GridLayout statusLayout = new GridLayout(1, false);
		statusLayout.marginWidth = 0;
		statusLayout.marginHeight = 0;
		statusPanel.setLayout(statusLayout);
		
		// 상태 레이블
		statusLabel = new Label(statusPanel, SWT.NONE);
		statusLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		statusLabel.setText("XML 파일 또는 폴더를 선택하고 검증을 실행하세요.");
		
		// Progress Bar
		progressBar = new org.eclipse.swt.widgets.ProgressBar(statusPanel, SWT.HORIZONTAL | SWT.SMOOTH);
		progressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		progressBar.setVisible(false); // 초기에는 숨김
		
		// 기본 규칙 파일 설정 및 로드 (statusLabel 생성 후에 호출)
		try {
			loadDefaultRuleFile();
		} catch (Exception e) {
			System.err.println("기본 규칙 파일 로드 중 오류: " + e.getMessage());
			e.printStackTrace();
			statusLabel.setText("규칙 파일 로드 중 오류가 발생했습니다. '변경...' 버튼을 클릭하여 수동으로 선택하세요.");
		}
		
		// 오류 메시지 영역 헤더
		Composite errorHeaderPanel = new Composite(parent, SWT.BORDER);
		GridLayout errorHeaderLayout = new GridLayout(2, false);
		errorHeaderLayout.marginWidth = 5;
		errorHeaderLayout.marginHeight = 5;
		errorHeaderLayout.horizontalSpacing = 10;
		errorHeaderPanel.setLayout(errorHeaderLayout);
		errorHeaderPanel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		
		Label errorLabel = new Label(errorHeaderPanel, SWT.NONE);
		errorLabel.setText("오류 메시지:");
		errorLabel.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		
		Button clearButton = new Button(errorHeaderPanel, SWT.PUSH);
		clearButton.setText("Clear");
		clearButton.setToolTipText("모든 오류 메시지를 삭제합니다");
		clearButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
		clearButton.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				clearAllErrors();
			}
		});
		
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
				System.out.println("더블클릭 이벤트 발생");
				IStructuredSelection selection = (IStructuredSelection) event.getSelection();
				Object selected = selection.getFirstElement();
				System.out.println("선택된 객체: " + (selected != null ? selected.getClass().getName() : "null"));
				
				if (selected instanceof ValidationError) {
					ValidationError error = (ValidationError) selected;
					System.out.println("ValidationError 발견:");
					System.out.println("  파일: " + error.getFile().getAbsolutePath());
					System.out.println("  라인 번호: " + error.getLineNumber());
					System.out.println("  컬럼 번호: " + error.getColumnNumber());
					System.out.println("  메시지: " + error.getMessage());
					
					openFileInEditor(error.getFile(), error.getLineNumber());
				} else {
					System.out.println("경고: 선택된 객체가 ValidationError가 아닙니다.");
				}
			}
		});
		} catch (Throwable e) {
			// 초기화 중 오류 발생 시 에러 메시지 표시
			System.err.println("========================================");
			System.err.println("XmlValidationView 초기화 오류 발생!");
			System.err.println("오류 타입: " + e.getClass().getName());
			System.err.println("오류 메시지: " + e.getMessage());
			System.err.println("========================================");
			e.printStackTrace();
			
			// 최소한의 UI 표시
			try {
				if (statusLabel != null && !statusLabel.isDisposed()) {
					statusLabel.setText("뷰 초기화 중 오류가 발생했습니다: " + e.getClass().getSimpleName() + " - " + e.getMessage());
				} else {
					Label errorLabel = new Label(parent, SWT.WRAP);
					errorLabel.setText("뷰 초기화 중 오류가 발생했습니다:\n" + 
							e.getClass().getSimpleName() + "\n" + 
							(e.getMessage() != null ? e.getMessage() : "알 수 없는 오류"));
					errorLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
				}
			} catch (Throwable e2) {
				// UI 생성도 실패한 경우
				System.err.println("UI 생성도 실패했습니다: " + e2.getMessage());
				e2.printStackTrace();
			}
			
			// PartInitException은 checked exception이므로 RuntimeException으로 래핑하여 던짐
			// ViewPart.createPartControl은 throws를 선언하지 않으므로 RuntimeException만 던질 수 있음
			if (e instanceof PartInitException) {
				// PartInitException을 RuntimeException으로 래핑
				throw new RuntimeException("뷰 초기화 실패: " + e.getMessage(), e);
			} else if (e instanceof RuntimeException) {
				throw (RuntimeException) e;
			} else {
				// 다른 예외는 RuntimeException으로 래핑
				throw new RuntimeException("뷰 초기화 중 오류 발생: " + e.getMessage(), e);
			}
		}
	}
	
	/**
	 * 파일을 편집기에서 열고 특정 라인으로 이동합니다.
	 */
	private void openFileInEditor(File file, int lineNumber) {
		System.out.println("파일 열기 시도: " + file.getAbsolutePath() + ", 라인: " + lineNumber);
		
		try {
			IFileStore fileStore = EFS.getLocalFileSystem().getStore(file.toURI());
			IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
			
			if (page == null) {
				MessageDialog.openError(getSite().getShell(), "오류", 
						"워크벤치 페이지를 찾을 수 없습니다.");
				return;
			}
			
			// 에디터가 활성화될 때까지 기다리기 위한 리스너
			final int targetLine = lineNumber;
			final IWorkbenchPage finalPage = page;
			
			IPartListener partListener = new IPartListener() {
				@Override
				public void partOpened(IWorkbenchPart part) {}
				
				@Override
				public void partDeactivated(IWorkbenchPart part) {}
				
				@Override
				public void partClosed(IWorkbenchPart part) {}
				
				@Override
				public void partBroughtToTop(IWorkbenchPart part) {}
				
				@Override
				public void partActivated(IWorkbenchPart part) {
					if (part instanceof IEditorPart) {
						IEditorPart editor = (IEditorPart) part;
						// 파일 경로가 일치하는지 확인
						try {
							Object input = editor.getEditorInput().getAdapter(IFileStore.class);
							if (input != null || editor.getEditorInput().getName().equals(file.getName())) {
								finalPage.removePartListener(this);
								// 에디터가 활성화된 후 라인으로 이동
								Display.getCurrent().asyncExec(() -> {
									navigateToLineInEditor(editor, targetLine);
								});
							}
						} catch (Exception e) {
							// 무시하고 계속 진행
						}
					}
				}
			};
			
			page.addPartListener(partListener);
			
			IEditorPart editor = IDE.openEditorOnFileStore(page, fileStore);
			System.out.println("에디터 타입: " + (editor != null ? editor.getClass().getName() : "null"));
			System.out.println("ITextEditor 인스턴스인가? " + (editor instanceof ITextEditor));
			
			// 라인 번호가 유효하면 해당 라인으로 이동
			if (lineNumber > 0) {
				// 즉시 시도
				navigateToLineInEditor(editor, lineNumber);
				
				// 에디터가 활성화될 때도 시도 (백업)
				Display.getCurrent().timerExec(500, () -> {
					IEditorPart activeEditor = finalPage.getActiveEditor();
					if (activeEditor == editor) {
						navigateToLineInEditor(activeEditor, lineNumber);
					}
				});
			}
		} catch (PartInitException e) {
			MessageDialog.openError(getSite().getShell(), "오류", 
					"파일을 열 수 없습니다: " + e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			MessageDialog.openError(getSite().getShell(), "오류", 
					"파일을 열는 중 오류가 발생했습니다: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * 에디터에서 특정 라인으로 이동을 시도합니다.
	 */
	private void navigateToLineInEditor(IEditorPart editor, int lineNumber) {
		if (editor == null || lineNumber <= 0) {
			return;
		}
		
		ITextEditor textEditor = null;
		
		if (editor instanceof ITextEditor) {
			textEditor = (ITextEditor) editor;
		} else {
			// 어댑터를 통해 ITextEditor를 얻어보기
			textEditor = editor.getAdapter(ITextEditor.class);
		}
		
		if (textEditor != null) {
			System.out.println("텍스트 에디터로 라인 이동 시도: " + lineNumber);
			navigateToLine(textEditor, lineNumber, 0);
		} else {
			System.out.println("경고: 에디터가 ITextEditor가 아닙니다. 타입: " + 
					(editor != null ? editor.getClass().getName() : "null"));
		}
	}
	
	/**
	 * 에디터가 완전히 로드될 때까지 재시도하면서 특정 라인으로 이동합니다.
	 */
	private void navigateToLine(ITextEditor textEditor, int lineNumber, int retryCount) {
		System.out.println("라인 이동 시도 (재시도 " + retryCount + "): 라인 " + lineNumber);
		
		Display.getCurrent().asyncExec(() -> {
			try {
				// 에디터가 disposed되었는지 확인
				if (textEditor.getEditorSite() == null || 
					textEditor.getEditorSite().getShell() == null ||
					textEditor.getEditorSite().getShell().isDisposed()) {
					System.out.println("에디터가 disposed되었습니다.");
					return;
				}
				
				IDocument document = textEditor.getDocumentProvider()
						.getDocument(textEditor.getEditorInput());
				
				if (document == null) {
					System.out.println("문서가 null입니다. 재시도...");
					if (retryCount < 30) {
						Display.getCurrent().timerExec(100, () -> {
							navigateToLine(textEditor, lineNumber, retryCount + 1);
						});
					}
					return;
				}
				
				int totalLines = document.getNumberOfLines();
				System.out.println("문서 총 라인 수: " + totalLines);
				
				if (totalLines > 0) {
					// 라인 번호가 문서 범위 내에 있는지 확인
					int targetLine = lineNumber;
					if (targetLine > totalLines) {
						System.out.println("라인 번호가 범위를 벗어남. " + targetLine + " > " + totalLines);
						targetLine = totalLines;
					}
					if (targetLine < 1) {
						targetLine = 1;
					}
					
					System.out.println("목표 라인: " + targetLine);
					
					// 라인 번호는 1부터 시작하지만 getLineOffset은 0부터 시작
					int lineIndex = targetLine - 1;
					
					try {
						// 라인 정보 가져오기
						IRegion lineInfo = document.getLineInformation(lineIndex);
						int offset = lineInfo.getOffset();
						int length = lineInfo.getLength();
						
						System.out.println("라인 오프셋: " + offset + ", 라인 길이: " + length);
						
						// 에디터에 포커스 설정 (먼저)
						textEditor.setFocus();
						
						// 라인 시작 위치로 이동하고 선택
						// offset 위치에 커서를 두고, 0 길이로 선택 (커서만 이동)
						textEditor.selectAndReveal(offset, 0);
						
						// 추가로 한 번 더 시도 (때로는 한 번만으로는 작동하지 않음)
						Display.getCurrent().timerExec(100, () -> {
							try {
								textEditor.selectAndReveal(offset, 0);
								textEditor.setFocus();
							} catch (Exception e) {
								// 무시
							}
						});
						
						System.out.println("라인 이동 완료! 라인 " + targetLine + "로 이동했습니다.");
					} catch (BadLocationException e) {
						System.err.println("BadLocationException: 라인 " + lineIndex + " - " + e.getMessage());
						if (retryCount < 30) {
							Display.getCurrent().timerExec(100, () -> {
								navigateToLine(textEditor, lineNumber, retryCount + 1);
							});
						}
					}
				} else if (retryCount < 30) {
					// 문서가 아직 로드되지 않았으면 재시도 (최대 30번)
					System.out.println("문서가 아직 로드되지 않음. 재시도...");
					Display.getCurrent().timerExec(100, () -> {
						navigateToLine(textEditor, lineNumber, retryCount + 1);
					});
				} else {
					System.err.println("문서 로드 실패: 최대 재시도 횟수 초과");
				}
			} catch (Exception e) {
				System.err.println("라인 이동 중 예외 발생: " + e.getClass().getName() + " - " + e.getMessage());
				e.printStackTrace();
				if (retryCount < 30) {
					// 오류 발생 시 재시도 (최대 30번)
					Display.getCurrent().timerExec(100, () -> {
						navigateToLine(textEditor, lineNumber, retryCount + 1);
					});
				} else {
					System.err.println("라인 이동 오류 (재시도 실패): " + e.getMessage());
				}
			}
		});
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
			
			// 규칙 파일 경로를 정규화하여 저장 (심볼릭 링크 등 해결)
			try {
				currentRuleFile = ruleFile.getCanonicalFile();
			} catch (Exception e) {
				currentRuleFile = ruleFile;
			}
			
			// 파일명만 표시
			rulePathText.setText(ruleFile.getName());
			statusLabel.setText("규칙 파일 로드 완료: " + ruleFile.getName());
			System.out.println("규칙 파일 로드 성공!");
			System.out.println("  저장된 규칙 파일 경로: " + currentRuleFile.getAbsolutePath());
			System.out.println("  정규화된 경로: " + (currentRuleFile.getCanonicalPath()));
			System.out.println("  부모 디렉토리: " + (currentRuleFile.getParentFile() != null ? currentRuleFile.getParentFile().getAbsolutePath() : "null"));
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
			
			String pathToAdd;
			if (fileNames.length == 1) {
				pathToAdd = selectedXmlFiles.get(0).getAbsolutePath();
			} else {
				pathToAdd = filterPath;
			}
			
			xmlPathCombo.setText(pathToAdd);
			addToHistory(pathToAdd);
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
			xmlPathCombo.setText(selectedPath);
			addToHistory(selectedPath);
			updateSelectedFiles(selectedPath);
		}
	}
	
	/**
	 * 경로를 히스토리에 추가합니다.
	 */
	private void addToHistory(String path) {
		if (path == null || path.trim().isEmpty()) {
			return;
		}
		
		String normalizedPath = path.trim();
		
		// 이미 존재하는 경로는 제거 (중복 방지)
		xmlPathHistory.remove(normalizedPath);
		
		// 맨 앞에 추가
		xmlPathHistory.add(0, normalizedPath);
		
		// 최대 크기 제한
		if (xmlPathHistory.size() > MAX_HISTORY_SIZE) {
			xmlPathHistory = xmlPathHistory.subList(0, MAX_HISTORY_SIZE);
		}
		
		// Combo 목록 업데이트
		xmlPathCombo.setItems(xmlPathHistory.toArray(new String[0]));
		xmlPathCombo.setText(normalizedPath);
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
		
		// 현재 규칙 파일이 있으면 해당 경로와 파일명을 기본으로 설정
		if (currentRuleFile != null && currentRuleFile.exists()) {
			try {
				File parentDir = currentRuleFile.getParentFile();
				if (parentDir != null && parentDir.exists()) {
					// 절대 경로를 정규화 (심볼릭 링크 등 해결)
					String parentPath = parentDir.getCanonicalPath();
					
					System.out.println("======================================");
					System.out.println("규칙 파일 다이얼로그 초기 경로 설정");
					System.out.println("  현재 규칙 파일: " + currentRuleFile.getAbsolutePath());
					System.out.println("  정규화된 부모 경로: " + parentPath);
					System.out.println("  부모 디렉토리 존재: " + parentDir.exists());
					
					// Windows에서 FileDialog 경로 설정
					// setFilterPath는 Windows에서 때때로 작동하지 않을 수 있음
					// 하지만 일단 시도해봄
					dialog.setFilterPath(parentPath);
					dialog.setFileName(currentRuleFile.getName());
					
					// 설정 확인
					String setFilterPath = dialog.getFilterPath();
					String setFileName = dialog.getFileName();
					System.out.println("  설정된 FilterPath: " + setFilterPath);
					System.out.println("  설정된 FileName: " + setFileName);
					System.out.println("======================================");
					
					// Windows에서 setFilterPath가 무시될 수 있으므로
					// 다이얼로그를 열기 전에 경로를 확인하고 필요시 재설정
					// 참고: Windows FileDialog는 때때로 setFilterPath를 무시할 수 있음
					// 이는 Windows API의 제한사항일 수 있음
				} else {
					System.out.println("경고: 부모 디렉토리가 존재하지 않음");
				}
			} catch (Exception e) {
				System.err.println("규칙 파일 경로 설정 오류: " + e.getMessage());
				e.printStackTrace();
			}
		} else {
			System.out.println("현재 규칙 파일 정보:");
			System.out.println("  currentRuleFile: " + (currentRuleFile != null ? currentRuleFile.getAbsolutePath() : "null"));
			if (currentRuleFile != null) {
				System.out.println("  파일 존재: " + currentRuleFile.exists());
			}
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
	 * 파일 수정 시간을 확인하고 변경된 파일을 다시 로드합니다.
	 */
	private void checkAndReloadModifiedFiles() {
		List<File> filesToReload = new ArrayList<>();
		
		for (File file : selectedXmlFiles) {
			String filePath = file.getAbsolutePath();
			long currentModified = file.lastModified();
			Long lastKnownModified = fileLastModifiedMap.get(filePath);
			
			// 파일이 수정되었거나 처음 검증하는 경우
			if (lastKnownModified == null || currentModified != lastKnownModified) {
				filesToReload.add(file);
				fileLastModifiedMap.put(filePath, currentModified);
				System.out.println("파일 수정 감지: " + file.getName() + 
						(lastKnownModified == null ? " (첫 검증)" : " (수정됨)"));
			}
		}
		
		if (!filesToReload.isEmpty()) {
			System.out.println("수정된 파일 " + filesToReload.size() + "개를 다시 로드합니다.");
		}
	}
	
	/**
	 * 검증 중단
	 */
	private void cancelValidation() {
		System.out.println("========================================");
		System.out.println("검증 중단 요청 수신!");
		System.out.println("========================================");
		validationCancelled = true;
		Display display = getSite().getShell().getDisplay();
		display.asyncExec(() -> {
			if (!cancelButton.isDisposed()) {
				cancelButton.setEnabled(false);
			}
			if (!validateButton.isDisposed()) {
				validateButton.setEnabled(true);
			}
			if (!statusLabel.isDisposed()) {
				statusLabel.setText("검증 중단 중...");
			}
			if (!progressBar.isDisposed()) {
				progressBar.setVisible(false);
			}
		});
	}
	
	/**
	 * 검증 수행
	 */
	private void performValidation() {
		// XML 파일 확인 - 항상 경로에서 다시 수집
		String xmlPath = xmlPathCombo.getText().trim();
		if (xmlPath.isEmpty()) {
			MessageDialog.openWarning(getSite().getShell(), "경고", 
					"XML 파일 또는 폴더를 선택해주세요.");
			return;
		}
		
		// 히스토리에 추가
		addToHistory(xmlPath);
		
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
		
		// 검증 중단 플래그 초기화 및 버튼 상태 변경 (동기적으로 즉시 실행)
		validationCancelled = false;
		if (!validateButton.isDisposed()) {
			validateButton.setEnabled(false);
		}
		if (!cancelButton.isDisposed()) {
			cancelButton.setEnabled(true);
		}
		
		// 검증을 별도 스레드에서 실행하여 UI가 블로킹되지 않도록 함
		// Display 객체를 미리 가져와서 스레드에서 사용
		final Display display = getSite().getShell().getDisplay();
		
		// 검증 시작 시간 기록 및 플래그 초기화
		validationStartTime = System.currentTimeMillis();
		currentProgress = 0;
		validationCompleted = false;
		
		// Progress Bar 초기화 및 표시
		if (!progressBar.isDisposed()) {
			progressBar.setMaximum(selectedXmlFiles.size());
			progressBar.setSelection(0);
			progressBar.setVisible(true);
		}
		if (!statusLabel.isDisposed()) {
			statusLabel.setText("검증 중... (0/" + selectedXmlFiles.size() + ") [00:00]");
		}
		
		// 타이머 시작 (1초마다 시간 업데이트)
		final Runnable timerRunnable = new Runnable() {
			@Override
			public void run() {
				// 검증이 중단되었거나 완료되었으면 타이머 중단
				if (validationCancelled || validationCompleted) {
					return;
				}
				
				if (!statusLabel.isDisposed() && !progressBar.isDisposed()) {
					long elapsedTime = System.currentTimeMillis() - validationStartTime;
					String timeString = formatElapsedTime(elapsedTime);
					statusLabel.setText("검증 중... (" + currentProgress + "/" + selectedXmlFiles.size() + ") [" + timeString + "]");
					
					// 다음 타이머 예약 (검증이 진행 중인 경우에만)
					if (!validationCancelled && !validationCompleted) {
						display.timerExec(1000, this);
					}
				}
			}
		};
		display.timerExec(1000, timerRunnable);
		
		Thread validationThread = new Thread(new Runnable() {
			@Override
			public void run() {
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
				
				for (int i = 0; i < selectedXmlFiles.size(); i++) {
			// 검증 중단 확인
			if (validationCancelled) {
				System.out.println("검증이 중단되었습니다. (" + i + "/" + selectedXmlFiles.size() + " 파일 처리됨)");
				break;
			}
			
			File originalFile = selectedXmlFiles.get(i);
			String filePath = originalFile.getAbsolutePath();
			
			// 항상 최신 파일 객체를 생성하여 사용 (캐시 문제 방지)
			File xmlFile = new File(filePath);
			
			// 파일이 존재하는지 확인
			if (!xmlFile.exists()) {
				System.err.println("파일이 존재하지 않습니다: " + filePath);
				allErrors.add(new ValidationError(xmlFile, -1, -1, 
						"파일이 존재하지 않습니다: " + filePath,
						ValidationError.ErrorType.SYNTAX));
				invalidCount++;
				continue;
			}
			
			// 파일 수정 시간 확인 및 로깅
			long currentModified = xmlFile.lastModified();
			Long lastKnownModified = fileLastModifiedMap.get(filePath);
			
			if (lastKnownModified != null && currentModified != lastKnownModified) {
				System.out.println("파일 수정 감지: " + xmlFile.getName() + 
						" (수정 시간: " + lastKnownModified + " -> " + currentModified + ")");
			} else if (lastKnownModified == null) {
				System.out.println("파일 첫 검증: " + xmlFile.getName() + " (수정 시간: " + currentModified + ")");
			} else {
				System.out.println("파일 재검증: " + xmlFile.getName() + " (수정 시간: " + currentModified + ")");
			}
			
			// 파일 수정 시간 저장
			fileLastModifiedMap.put(filePath, currentModified);
			
			// 파일 내용 강제 리프레시를 위해 파일을 다시 읽음
			// FileInputStream을 사용하면 항상 최신 내용을 읽습니다
			System.out.println("파일 검증 시작: " + xmlFile.getAbsolutePath() + 
					" (크기: " + xmlFile.length() + " bytes, 수정 시간: " + currentModified + ")");
			
			// 검증 중단 확인 (파일 처리 시작 전)
			if (validationCancelled) {
				System.out.println("검증이 중단되었습니다. (" + i + "/" + selectedXmlFiles.size() + " 파일 처리됨)");
				break;
			}
			
			boolean hasError = false;
			
			// 1. 문법 체크
			boolean syntaxValid = syntaxValidator.validate(xmlFile);
			
			// 검증 중단 확인 (문법 체크 후)
			if (validationCancelled) {
				System.out.println("검증이 중단되었습니다. (" + i + "/" + selectedXmlFiles.size() + " 파일 처리됨)");
				break;
			}
			
			if (!syntaxValid) {
				// 검증 중단 확인 (오류 처리 전)
				if (validationCancelled) {
					System.out.println("검증이 중단되었습니다. (" + i + "/" + selectedXmlFiles.size() + " 파일 처리됨)");
					break;
				}
				
				for (ValidationError error : syntaxValidator.getErrors()) {
					// 검증 중단 확인 (각 오류 처리 전)
					if (validationCancelled) {
						System.out.println("검증이 중단되었습니다. (" + i + "/" + selectedXmlFiles.size() + " 파일 처리됨)");
						break;
					}
					allErrors.add(new ValidationError(
							error.getFile(), 
							error.getLineNumber(), 
							error.getColumnNumber(), 
							error.getMessage(),
							ValidationError.ErrorType.SYNTAX));
				}
				hasError = true;
			}
			
			// 검증 중단 확인 (문법 체크 오류 처리 후)
			if (validationCancelled) {
				System.out.println("검증이 중단되었습니다. (" + i + "/" + selectedXmlFiles.size() + " 파일 처리됨)");
				break;
			}
			
			// 2. 정합성 체크 (규칙 파일이 있고 문법 오류가 없는 경우)
			if (ruleParser != null && syntaxValid) {
				// 검증 중단 확인 (정합성 체크 전)
				if (validationCancelled) {
					System.out.println("검증이 중단되었습니다. (" + i + "/" + selectedXmlFiles.size() + " 파일 처리됨)");
					break;
				}
				
				ConsistencyValidator consistencyValidator = new ConsistencyValidator(ruleParser);
				boolean consistencyValid = consistencyValidator.validate(xmlFile);
				
				// 검증 중단 확인 (정합성 체크 후)
				if (validationCancelled) {
					System.out.println("검증이 중단되었습니다. (" + i + "/" + selectedXmlFiles.size() + " 파일 처리됨)");
					break;
				}
				
				if (!consistencyValid) {
					// 검증 중단 확인 (정합성 오류 처리 전)
					if (validationCancelled) {
						System.out.println("검증이 중단되었습니다. (" + i + "/" + selectedXmlFiles.size() + " 파일 처리됨)");
						break;
					}
					
					for (ValidationError error : consistencyValidator.getErrors()) {
						// 검증 중단 확인 (각 오류 처리 전)
						if (validationCancelled) {
							System.out.println("검증이 중단되었습니다. (" + i + "/" + selectedXmlFiles.size() + " 파일 처리됨)");
							break;
						}
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
			
			// 검증 중단 확인 (파일 처리 완료 후)
			if (validationCancelled) {
				System.out.println("검증이 중단되었습니다. (" + (i + 1) + "/" + selectedXmlFiles.size() + " 파일 처리됨)");
				break;
			}
			
			if (hasError) {
				invalidCount++;
			} else {
				validCount++;
			}
			
				// 진행 상태 업데이트
				final int progress = i + 1;
				currentProgress = progress;
				display.asyncExec(() -> {
					if (!progressBar.isDisposed()) {
						progressBar.setSelection(progress);
					}
					// 타이머가 시간을 업데이트하므로 여기서는 진행 상태만 업데이트
				});
			}
			
			// 검증 완료/중단 후 UI 상태 복원
			// 최종 소요 시간 계산
			final long totalElapsedTime = System.currentTimeMillis() - validationStartTime;
			final String totalTimeString = formatElapsedTime(totalElapsedTime);
			
			// 검증 완료 플래그 설정 (중단되지 않은 경우에만)
			if (!validationCancelled) {
				validationCompleted = true;
			}
			
			// 람다에서 사용하기 위해 final 변수로 복사
			final int finalValidCount = validCount;
			final int finalInvalidCount = invalidCount;
			final int finalTotalFiles = selectedXmlFiles.size();
			final int finalErrorCount = allErrors.size();
			final int finalTotalErrors = allValidationErrors.size();
			final boolean finalCancelled = validationCancelled;
			
			display.asyncExec(() -> {
				// 타이머는 validationCompleted 플래그로 자동 중단됨
				if (!progressBar.isDisposed()) {
					progressBar.setVisible(false);
				}
				if (!validateButton.isDisposed()) {
					validateButton.setEnabled(true);
				}
				if (!cancelButton.isDisposed()) {
					cancelButton.setEnabled(false);
				}
			});
			
			// 새로운 오류를 기존 오류 리스트의 앞에 추가 (최신 오류가 상단에 표시)
			allValidationErrors.addAll(0, allErrors);
			
			// 결과 표시 (모든 오류 포함) - UI 스레드에서 실행
			display.asyncExec(() -> {
				tableViewer.setInput(allValidationErrors);
				
				// 상태 메시지 업데이트
				String statusMessage;
				if (finalCancelled) {
					statusMessage = String.format("검증 중단: %d개 파일 처리됨 (성공: %d, 실패: %d, 오류: %d건) | 전체 누적 오류: %d건 | 소요 시간: %s",
							finalValidCount + finalInvalidCount, finalValidCount, finalInvalidCount, finalErrorCount, finalTotalErrors, totalTimeString);
				} else {
					statusMessage = String.format("검증 완료: 총 %d개 파일 (성공: %d, 실패: %d, 오류: %d건) | 전체 누적 오류: %d건 | 소요 시간: %s",
							finalTotalFiles, finalValidCount, finalInvalidCount, finalErrorCount, finalTotalErrors, totalTimeString);
				}
				if (!statusLabel.isDisposed()) {
					statusLabel.setText(statusMessage);
				}
			});
			
			// 결과 메시지 (UI 스레드에서 실행)
			display.asyncExec(() -> {
				if (finalErrorCount == 0) {
					MessageDialog.openInformation(getSite().getShell(), "검증 완료", 
							"모든 " + finalTotalFiles + "개 XML 파일이 검증을 통과했습니다.");
				} else {
					MessageDialog.openWarning(getSite().getShell(), "검증 완료", 
							finalTotalFiles + "개 파일 중 " + finalInvalidCount + "개 파일에서 " + 
							finalErrorCount + "건의 오류가 발견되었습니다.");
				}
			});
			}
		});
		
		// 검증 스레드 시작
		validationThread.start();
	}
	
	/**
	 * 경과 시간을 mm:ss 형식으로 포맷팅합니다.
	 * @param elapsedMillis 경과 시간 (밀리초)
	 * @return mm:ss 형식의 문자열
	 */
	private String formatElapsedTime(long elapsedMillis) {
		long totalSeconds = elapsedMillis / 1000;
		long minutes = totalSeconds / 60;
		long seconds = totalSeconds % 60;
		return String.format("%02d:%02d", minutes, seconds);
	}
	
	/**
	 * 모든 오류 메시지를 삭제합니다.
	 */
	private void clearAllErrors() {
		allValidationErrors.clear();
		tableViewer.setInput(allValidationErrors);
		statusLabel.setText("모든 오류 메시지가 삭제되었습니다.");
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
