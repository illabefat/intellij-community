package com.intellij.refactoring.memberPushDown;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.classMembers.MemberInfoStorage;

import java.util.List;

/**
 * @author dsl
 */
public class PushDownHandler implements RefactoringActionHandler {
  public static final String REFACTORING_NAME = "Push Members Down";
  private PsiClass myClass;

  public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
    int offset = editor.getCaretModel().getOffset();
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    PsiElement element = file.findElementAt(offset);

    while (true) {
      if (element == null || element instanceof PsiFile) {
        String message =
                "Cannot perform the refactoring.\n" +
                "The caret should be positioned inside a class to push members from";
        RefactoringMessageUtil.showErrorMessage(REFACTORING_NAME, message, null/*HelpID.MEMBERS_PUSH_DOWN*/, project);
        return;
      }

      if (!element.isWritable()) {
        if (!RefactoringMessageUtil.checkReadOnlyStatus(project, element)) return;
      }

      if (element instanceof PsiClass || element instanceof PsiField || element instanceof PsiMethod) {
        invoke(project, new PsiElement[]{element}, dataContext);
        return;
      }
      element = element.getParent();
    }
  }

  public void invoke(final Project project, PsiElement[] elements, DataContext dataContext) {
    if (elements.length != 1) return;

    PsiElement element = elements[0];
    PsiClass aClass;
    PsiElement aMember = null;

    if (element instanceof PsiClass) {
      aClass = (PsiClass) element;
    } else if (element instanceof PsiMethod) {
      aClass = ((PsiMethod) element).getContainingClass();
      aMember = element;
    } else if (element instanceof PsiField) {
      aClass = ((PsiField) element).getContainingClass();
      aMember = element;
    } else
      return;

    myClass = aClass;
    if (!myClass.isWritable()) {
      if (!RefactoringMessageUtil.checkReadOnlyStatus(project, aClass)) return;
    }
    MemberInfoStorage memberInfoStorage = new MemberInfoStorage(myClass, new MemberInfo.Filter() {
      public boolean includeMember(PsiMember element) {
        return true;
      }
    });
    List<MemberInfo> members = memberInfoStorage.getClassMemberInfos(myClass);
    PsiManager manager = myClass.getManager();

    for (int i = 0; i < members.size(); i++) {
      MemberInfo member = members.get(i);

      if (manager.areElementsEquivalent(member.getMember(), aMember)) {
        member.setChecked(true);
        break;
      }
    }
    PushDownDialog dialog = new PushDownDialog(
            project,
            members.toArray(new MemberInfo[members.size()]),
            myClass);
    dialog.show();
  }
}