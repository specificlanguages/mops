package com.specificlanguages.mops.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtArrayAccessExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtUnaryExpression
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.parents

/**
 * Reports code in an `inner` class that changes a property of an enclosing class: an assignment (`=`, `+=`, ...), an
 * increment or decrement, an indexed assignment (`map[key] = value`), or a call to a mutating collection function such
 * as `add` or `put`.
 *
 * The enclosing class should own its state. The inner class can return what changed and let the owner apply it.
 */
class InnerClassChangesOuterState(config: Config) :
    Rule(config, "An inner class changes a property of its enclosing class."),
    RequiresAnalysisApi {

    override fun visitBinaryExpression(expression: KtBinaryExpression) {
        super.visitBinaryExpression(expression)
        if (expression.operationToken in KtTokens.ALL_ASSIGNMENTS) {
            check(expression, expression.left)
        }
    }

    override fun visitUnaryExpression(expression: KtUnaryExpression) {
        super.visitUnaryExpression(expression)
        if (expression.operationToken == KtTokens.PLUSPLUS || expression.operationToken == KtTokens.MINUSMINUS) {
            check(expression, expression.baseExpression)
        }
    }

    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val name = (expression.calleeExpression as? KtNameReferenceExpression)?.getReferencedName()
        val qualified = expression.parent as? KtDotQualifiedExpression
        if (name in MUTATING_FUNCTIONS && qualified != null && qualified.selectorExpression == expression) {
            check(expression, qualified.receiverExpression)
        }
    }

    private fun check(change: KtElement, target: KtExpression?) {
        val reference = propertyReference(target) ?: return
        val innerClass = change.parents.filterIsInstance<KtClass>().firstOrNull { it.isInner() } ?: return
        val owner = declaringClass(reference) ?: return
        if (owner in innerClass.parents) {
            report(
                Finding(
                    Entity.from(change),
                    "Inner class ${innerClass.name} changes ${reference.getReferencedName()} of ${owner.name}. " +
                        "Return the change and let ${owner.name} apply it.",
                ),
            )
        }
    }

    @OptIn(KaExperimentalApi::class)
    private fun declaringClass(reference: KtNameReferenceExpression): KtClassOrObject? {
        val declaration = analyze(reference) {
            (reference.resolveSymbol() as? KaPropertySymbol)?.psi
        } ?: return null
        return declaration.getStrictParentOfType<KtClassOrObject>()
    }

    /** The property reference that [target] changes: `x`, `this.x`, `this@Outer.x`, or `x[key]`. */
    private fun propertyReference(target: KtExpression?): KtNameReferenceExpression? = when (target) {
        is KtNameReferenceExpression -> target
        is KtParenthesizedExpression -> propertyReference(target.expression)
        is KtArrayAccessExpression -> propertyReference(target.arrayExpression)
        is KtDotQualifiedExpression ->
            if (target.receiverExpression is KtThisExpression) {
                target.selectorExpression as? KtNameReferenceExpression
            } else {
                null
            }
        else -> null
    }

    private companion object {
        val MUTATING_FUNCTIONS = setOf(
            "add", "addAll", "addFirst", "addLast",
            "remove", "removeAll", "removeAt", "removeIf", "removeFirst", "removeLast", "retainAll",
            "put", "putAll", "putIfAbsent", "getOrPut", "compute", "computeIfAbsent", "computeIfPresent", "merge",
            "set", "clear", "sort", "sortBy", "sortWith", "replaceAll",
        )
    }
}
