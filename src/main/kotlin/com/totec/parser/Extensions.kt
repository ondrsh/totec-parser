/**
 * Created by ndrsh on 02.11.20
 */
package com.totec.parser

val Char.Companion.END
	get() = MAX_VALUE

val Char.Companion.START
	get() = MIN_VALUE

val digitRange = '0'..'9'

fun Char.isDigitOrMinus() = this in digitRange || this == '-'
