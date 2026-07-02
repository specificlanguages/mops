package com.specificlanguages.mops.daemon

import com.specificlanguages.mops.daemon.core.MpsResult
import kotlin.test.fail

fun <T> assertOk(result: MpsResult<T>): T =
    when (result) {
        is MpsResult.Ok -> result.value
        is MpsResult.Error -> fail("expected Ok, got Error(${result.code}): ${result.message}")
        is MpsResult.ProtocolError -> fail("expected Ok, got ProtocolError(${result.code}): ${result.message}")
    }

fun <T> assertError(result: MpsResult<T>): MpsResult.Error =
    when (result) {
        is MpsResult.Ok -> fail("expected Error, got Ok: ${result.value}")
        is MpsResult.Error -> result
        is MpsResult.ProtocolError -> fail("expected Error, got ProtocolError(${result.code}): ${result.message}")
    }

fun <T> assertProtocolError(result: MpsResult<T>): MpsResult.ProtocolError =
    when (result) {
        is MpsResult.Ok -> fail("expected ProtocolError, got Ok: ${result.value}")
        is MpsResult.Error -> fail("expected ProtocolError, got Error(${result.code}): ${result.message}")
        is MpsResult.ProtocolError -> result
    }
