@file:OptIn(ExperimentalSerializationApi::class)

package krill.zone.shared.ksp

import kotlinx.serialization.*


@Target(  AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
@MetaSerializable
annotation class Krill 

