/*
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

package com.google.devtools.j2objc.translate;

import com.google.devtools.j2objc.ast.Assignment;
import com.google.devtools.j2objc.ast.Expression;
import com.google.devtools.j2objc.ast.FieldAccess;
import com.google.devtools.j2objc.ast.MethodInvocation;
import com.google.devtools.j2objc.ast.Name;
import com.google.devtools.j2objc.ast.PostfixExpression;
import com.google.devtools.j2objc.ast.PrefixExpression;
import com.google.devtools.j2objc.ast.QualifiedName;
import com.google.devtools.j2objc.ast.SimpleName;
import com.google.devtools.j2objc.ast.SwitchCase;
import com.google.devtools.j2objc.ast.TreeNode;
import com.google.devtools.j2objc.ast.TreeUtil;
import com.google.devtools.j2objc.ast.TreeVisitor;
import com.google.devtools.j2objc.types.IOSMethodBinding;
import com.google.devtools.j2objc.types.PointerTypeBinding;
import com.google.devtools.j2objc.util.BindingUtil;
import com.google.devtools.j2objc.util.NameTable;

import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;

/**
 * Converts static variable access to static method calls where necessary.
 *
 * @author Keith Stanger
 */
public class StaticVarRewriter extends TreeVisitor {

  private boolean useAccessor(TreeNode currentNode, IVariableBinding var) {
    return BindingUtil.isStatic(var) && !BindingUtil.isPrimitiveConstant(var)
        && !TreeUtil.getOwningType(currentNode).getTypeBinding().getTypeDeclaration().isEqualTo(
            var.getDeclaringClass().getTypeDeclaration());
  }

  @Override
  public boolean visit(Assignment node) {
    Expression lhs = node.getLeftHandSide();
    IVariableBinding lhsVar = TreeUtil.getVariableBinding(lhs);
    if (lhsVar != null && useAccessor(node, lhsVar)) {
      boolean isPrimitive = lhsVar.getType().isPrimitive();
      if (node.getOperator() == Assignment.Operator.ASSIGN && !isPrimitive) {
        Expression rhs = node.getRightHandSide();
        node.replaceWith(newSetterInvocation(lhsVar, TreeUtil.remove(rhs)));
        rhs.accept(this);
        return false;
      } else if (isPrimitive) {
        lhs.replaceWith(newGetterInvocation(lhs, true));
      }
    }
    return true;
  }

  @Override
  public boolean visit(SimpleName node) {
    return visitName(node);
  }

  @Override
  public boolean visit(QualifiedName node) {
    return visitName(node);
  }

  private boolean visitName(Name node) {
    IVariableBinding var = TreeUtil.getVariableBinding(node);
    if (var != null && useAccessor(node, var)) {
      TreeNode parent = node.getParent();
      if (parent instanceof QualifiedName && node == ((QualifiedName) parent).getQualifier()) {
        // QualifiedName nodes can only have qualifier children of type Name, so
        // we must convert QualifiedName parents to FieldAccess nodes.
        FieldAccess newParent = TreeUtil.convertToFieldAccess((QualifiedName) parent);
        node = (Name) newParent.getExpression();
      }
      node.replaceWith(newGetterInvocation(node, false));
      return false;
    }
    return true;
  }

  @Override
  public boolean visit(SwitchCase node) {
    // Avoid using an accessor method for enums in a switch case.
    return false;
  }

  @Override
  public boolean visit(PostfixExpression node) {
    Expression operand = node.getOperand();
    IVariableBinding operandVar = TreeUtil.getVariableBinding(operand);
    PostfixExpression.Operator op = node.getOperator();
    boolean isIncOrDec = op == PostfixExpression.Operator.INCREMENT
        || op == PostfixExpression.Operator.DECREMENT;
    if (isIncOrDec && operandVar != null && useAccessor(node, operandVar)) {
      node.setOperand(newGetterInvocation(operand, true));
      return false;
    }
    return true;
  }

  @Override
  public boolean visit(PrefixExpression node) {
    Expression operand = node.getOperand();
    IVariableBinding operandVar = TreeUtil.getVariableBinding(operand);
    PrefixExpression.Operator op = node.getOperator();
    boolean isIncOrDec = op == PrefixExpression.Operator.INCREMENT
        || op == PrefixExpression.Operator.DECREMENT;
    if (isIncOrDec && operandVar != null && useAccessor(node, operandVar)) {
      node.setOperand(newGetterInvocation(operand, true));
      return false;
    }
    return true;
  }

  private MethodInvocation newGetterInvocation(Expression variable, boolean assignable) {
    IVariableBinding var = TreeUtil.getVariableBinding(variable);
    ITypeBinding declaringType = var.getDeclaringClass().getTypeDeclaration();
    String varName = NameTable.getStaticVarName(var);
    String getterName = "get";
    ITypeBinding returnType = var.getType();
    if (assignable) {
      getterName += "Ref";
      returnType = new PointerTypeBinding(returnType);
    }
    IOSMethodBinding binding = IOSMethodBinding.newFunction(
        NameTable.getFullName(declaringType) + "_" + getterName + "_" + varName, returnType,
        declaringType);
    MethodInvocation invocation = new MethodInvocation(binding, null);
    if (assignable) {
      invocation = MethodInvocation.newDereference(invocation);
    }
    if (variable.hasNilCheck()) {
      invocation.setHasNilCheck(true);
    }
    return invocation;
  }

  private MethodInvocation newSetterInvocation(IVariableBinding var, Expression value) {
    ITypeBinding varType = var.getType();
    ITypeBinding declaringType = var.getDeclaringClass();
    IOSMethodBinding binding = IOSMethodBinding.newFunction(
        NameTable.getFullName(declaringType) + "_set_" + NameTable.getStaticVarName(var), varType,
        declaringType, varType);
    MethodInvocation invocation = new MethodInvocation(binding, null);
    invocation.getArguments().add(value);
    return invocation;
  }
}
