package com.specificlanguages.mops.detekt

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiWhiteSpace
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.psi.psiUtil.siblings

/**
 * Reports an `inner` class that has no comment directly above it. See the inner class bullet in
 * `docs/agents/kotlin-coding-guidelines.md`.
 */
class UnexplainedInnerClass(config: Config) :
    Rule(config, "An inner class has no comment that says why it is inner.") {

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        if (klass.isInner() && !hasLeadingComment(klass)) {
            report(
                Finding(
                    Entity.atName(klass),
                    "Inner class ${klass.name} has no comment that says why it is inner. Make it a nested or " +
                        "top-level class, or add the comment.",
                ),
            )
        }
    }

    // The Kotlin parser attaches comments directly above a declaration to the declaration itself, so they are its
    // first children. The previous siblings are checked too in case a comment is not attached.
    private fun hasLeadingComment(klass: KtClass): Boolean =
        klass.allChildren.takeWhile { it is PsiComment || it is PsiWhiteSpace }.any { it is PsiComment } ||
            klass.siblings(forward = false, withItself = false)
                .takeWhile { it is PsiComment || it is PsiWhiteSpace }
                .any { it is PsiComment }
}
