package com.xmlvalidator.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * XML 파일 검증을 수행하는 핸들러
 * 뷰를 열어서 사용자가 파일을 선택하고 검증할 수 있게 합니다.
 */
public class ValidateXmlHandler extends AbstractHandler {
	
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		
		// 뷰를 열기
		try {
			IWorkbenchPage page = window.getActivePage();
			page.showView("com.xmlvalidator.views.xmlValidationView");
		} catch (PartInitException e) {
			MessageDialog.openError(window.getShell(), "Error", 
					"Failed to open STR XML Validator view: " + e.getMessage());
			return null;
		}
		
		return null;
	}
}
