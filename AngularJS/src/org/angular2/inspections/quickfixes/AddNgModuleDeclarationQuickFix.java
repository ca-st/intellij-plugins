// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.angular2.inspections.quickfixes;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.lang.javascript.psi.JSElement;
import com.intellij.lang.javascript.psi.ecma6.ES6Decorator;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.Queue;
import org.angular2.codeInsight.Angular2DeclarationsScope;
import org.angular2.entities.Angular2Declaration;
import org.angular2.entities.Angular2Entity;
import org.angular2.entities.Angular2Module;
import org.angular2.entities.source.Angular2SourceDeclaration;
import org.angular2.inspections.actions.AddNgModuleDeclarationAction;
import org.angular2.inspections.actions.Angular2ActionFactory;
import org.angular2.lang.Angular2Bundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AddNgModuleDeclarationQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement {

  public static void add(@NotNull PsiElement context,
                         @NotNull Angular2Declaration declaration,
                         @NotNull List<LocalQuickFix> fixes) {
    if (declaration instanceof Angular2SourceDeclaration
        && ((Angular2SourceDeclaration)declaration).getTypeScriptClass().getName() != null) {
      fixes.add(new AddNgModuleDeclarationQuickFix(context, (Angular2SourceDeclaration)declaration));
    }
  }

  @NotNull
  private final String myDeclarationName;
  @NotNull
  private final SmartPsiElementPointer<ES6Decorator> myDeclarationDecorator;
  @Nullable
  private final String myModuleName;

  private AddNgModuleDeclarationQuickFix(@NotNull PsiElement context,
                                         @NotNull Angular2SourceDeclaration declaration) {
    super(context);
    myDeclarationName = Objects.requireNonNull(declaration.getTypeScriptClass().getName());
    myDeclarationDecorator = SmartPointerManager.createPointer(declaration.getDecorator());

    List<Angular2Module> candidates = getCandidates(context);
    if (candidates.size() == 1) {
      myModuleName = candidates.get(0).getName();
    }
    else {
      myModuleName = null;
    }
  }

  @NotNull
  @Override
  public String getText() {
    return Angular2Bundle.message(myModuleName == null ? "angular.quickfix.ngmodule.declare.name.choice"
                                                       : "angular.quickfix.ngmodule.declare.name",
                                  myDeclarationName, myModuleName);
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return Angular2Bundle.message("angular.quickfix.ngmodule.declare.family");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (myDeclarationDecorator.getElement() == null) return;
    AddNgModuleDeclarationAction action = Angular2ActionFactory.createAddNgModuleDeclarationAction(
      editor, startElement, myDeclarationDecorator, myDeclarationName, getText());
    List<JSElement> candidates = action.getCandidates();
    if (candidates.size() == 1 || editor != null) {
      action.execute();
    }
  }

  @NotNull
  public static List<Angular2Module> getCandidates(@NotNull PsiElement context) {
    Queue<Angular2Module> processingQueue = new Queue<>(20);
    Angular2DeclarationsScope scope = new Angular2DeclarationsScope(context);
    Angular2Module contextModule = scope.getModule();
    if (contextModule == null || !scope.isInSource(contextModule)) {
      return Collections.emptyList();
    }
    processingQueue.addLast(contextModule);
    Set<Angular2Module> processed = new HashSet<>();
    List<Angular2Module> result = new ArrayList<>();
    while (!processingQueue.isEmpty()) {
      Angular2Module module = processingQueue.pullFirst();
      if (processed.add(module) && scope.isInSource(module)) {
        result.add(module);
        module.getImports().forEach(processingQueue::addLast);
        for (Angular2Entity entity : module.getExports()) {
          if (entity instanceof Angular2Module) {
            processingQueue.addLast((Angular2Module)entity);
          }
        }
      }
    }
    return result;
  }
}
