package com.specificlanguages.mops.daemon

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class CodeModeExtension(val access: MpsAccessLevel)
