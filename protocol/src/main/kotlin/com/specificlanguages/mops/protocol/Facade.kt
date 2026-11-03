package com.specificlanguages.mops.protocol

/**
 * Marks an interface or object that exposes one function per operation it offers, such as the MPS operations of the
 * daemon or the requests of the daemon protocol. A facade grows with every operation added, so detekt does not apply
 * its function count limit to it (`ignoreAnnotated` in `config/detekt/detekt.yml`).
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class Facade
