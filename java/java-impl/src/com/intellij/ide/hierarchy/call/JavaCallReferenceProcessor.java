/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.hierarchy.call;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightMemberReference;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

public class JavaCallReferenceProcessor implements CallReferenceProcessor {
  @Override
  public boolean process(@NotNull PsiReference reference, @NotNull JavaCallHierarchyData data) {
    PsiClass originalClass = data.getOriginalClass();
    PsiMethod method = data.getMethod();
    Set<PsiMethod> methodsToFind = data.getMethodsToFind();
    PsiMethod methodToFind = data.getMethodToFind();
    PsiClassType originalType = data.getOriginalType();
    Map<Pair<PsiMember, PsiType>, NodeDescriptor> methodToDescriptorMap = data.getResultMap();
    Project myProject = data.getProject();

    CallHierarchyNodeDescriptor parentDescriptor = (CallHierarchyNodeDescriptor) data.getNodeDescriptor();
    boolean passInstanceCallInfo = false;
    if (reference instanceof PsiReferenceExpression) {
      final PsiExpression qualifier = ((PsiReferenceExpression)reference).getQualifierExpression();
      if (qualifier instanceof PsiSuperExpression) { // filter super.foo() call inside foo() and similar cases (bug 8411)
        final PsiClass superClass = PsiUtil.resolveClassInType(qualifier.getType());
        if (superClass == null || originalClass.isInheritor(superClass, true)) {
          return false;
        }
      }
      if (qualifier != null && !methodToFind.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiType qualifierType = qualifier.getType();
        if (qualifierType instanceof PsiClassType &&
            !TypeConversionUtil.isAssignable(qualifierType, originalType) &&
            methodToFind != method) {
          final PsiClass psiClass = ((PsiClassType)qualifierType).resolve();
          if (psiClass != null) {
            final PsiMethod callee = psiClass.findMethodBySignature(methodToFind, true);
            if (callee != null && !methodsToFind.contains(callee)) {
              // skip sibling methods
              return false;
            }
          }
        }
        if (parentDescriptor.dataFromInstanceCall != null) {
          if (parentDescriptor.dataFromInstanceCall.getOriginalClass().isInheritor(originalClass, true)) {
            if (qualifierType instanceof PsiClassType &&
            !TypeConversionUtil.isAssignable(qualifierType, parentDescriptor.dataFromInstanceCall.getOriginalType())) {
              return false;
            }
          }
        }
      }
      if (qualifier == null || qualifier instanceof PsiThisExpression) {
        passInstanceCallInfo = true;
        if (parentDescriptor.dataFromInstanceCall != null) {
          PsiClass instance = InheritanceUtil.findEnclosingInstanceInScope(method.getContainingClass(), (PsiReferenceExpression)reference, Conditions.alwaysTrue(), false);

          if (instance != null) {
            if (!instance.equals(parentDescriptor.dataFromInstanceCall.getOriginalClass()) &&
                !instance.isInheritor(parentDescriptor.dataFromInstanceCall.getOriginalClass(), true) &&
                !parentDescriptor.dataFromInstanceCall.getOriginalClass().isInheritor(instance, true)) {
              return false;
            }
          }
        }
      }
    }
    else {
      if (!(reference instanceof PsiElement)) {
        return true;
      }

      final PsiElement parent = ((PsiElement)reference).getParent();
      if (parent instanceof PsiNewExpression) {
        if (((PsiNewExpression)parent).getClassReference() != reference) {
          return false;
        }
      }
      else if (parent instanceof PsiAnonymousClass) {
        if (((PsiAnonymousClass)parent).getBaseClassReference() != reference) {
          return false;
        }
      }
      else if (!(reference instanceof LightMemberReference)) {
        return true;
      }
    }

    PsiType instanceType;
    if (passInstanceCallInfo) {
      if (parentDescriptor.dataFromInstanceCall != null) {
        instanceType = parentDescriptor.dataFromInstanceCall.getOriginalType();
      } else {
        instanceType = originalType;
      }
    } else {
      instanceType = null;
    }

    final PsiElement element = reference.getElement();

    // Within the same *node*,
    // calls to the same method get one child-node per key
    // it's possible that the same method is reachable via different known instance types

    // This version makes separate nodes for each method for each instance type
    //   (Note: Very limited based on identity of "instanceDataToPass", could be enhanced further)
    // final Pair<PsiMember, PsiType> key = Pair.pair(CallHierarchyNodeDescriptor.getEnclosingElement(element), instanceType);
    // This version will only prune call-targets if all call-sites are reached "via the same instanceDataToPass"
    final Pair<PsiMember, PsiType> key = Pair.pair(CallHierarchyNodeDescriptor.getEnclosingElement(element), null);

    {
      // detect recursion
      // the current call-site calls *method*
      // Thus, we already have a node that represents *method*
      // Check whether we have any other node along the parent-chain that represents that same method

      NodeDescriptor ancestorDescriptor = parentDescriptor;
      // Start check on grandparent
      while ((ancestorDescriptor = ancestorDescriptor.getParentDescriptor()) != null) {

        if (ancestorDescriptor instanceof CallHierarchyNodeDescriptor) {
          PsiMember ancestorCallSite = ((CallHierarchyNodeDescriptor)ancestorDescriptor).getEnclosingElement();
          if (ancestorCallSite == method) {
            // We have at least two occurrences in the parent chain of method already
            // Don't search any deeper
            return false;
          }
        }
      }
    }

    JavaCallHierarchyData instanceDataToPass;
    if (!passInstanceCallInfo) {
      instanceDataToPass = null;
    } else if (parentDescriptor.dataFromInstanceCall != null) {
      instanceDataToPass = parentDescriptor.dataFromInstanceCall;
    } else {
      instanceDataToPass = data;
    }

    boolean nodeExistsAlready = true;
    synchronized (methodToDescriptorMap) {
      CallHierarchyNodeDescriptor d = (CallHierarchyNodeDescriptor)methodToDescriptorMap.get(key);
      if (d == null) {
        nodeExistsAlready = false;
        d = new CallHierarchyNodeDescriptor(myProject, parentDescriptor, element, false, true);
        methodToDescriptorMap.put(key, d);
        d.dataFromInstanceCall = instanceDataToPass;

      }
      else if (!d.hasReference(reference)) {
        d.incrementUsageCount();
      }
      d.addReference(reference);

      if (nodeExistsAlready) {
        // node existed already, unless exact same data is already assigned, we need to clear the data
        // because the same node is invoked from different contexts with respect to the instance data
        if (d.dataFromInstanceCall != instanceDataToPass) {
          d.dataFromInstanceCall = null;
        }
      }
    }
    return false;
  }
}
