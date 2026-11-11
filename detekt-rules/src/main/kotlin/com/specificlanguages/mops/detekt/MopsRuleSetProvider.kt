package com.specificlanguages.mops.detekt

import dev.detekt.api.RuleName
import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/** The project's own detekt rules, configured under `mops:` in `config/detekt/detekt.yml`. */
class MopsRuleSetProvider : RuleSetProvider {
    override val ruleSetId = RuleSetId("mops")

    override fun instance() = RuleSet(
        ruleSetId,
        mapOf(RuleName("InnerClassChangesOuterState") to ::InnerClassChangesOuterState),
    )
}
