/**
 * Created by ndrsh on 02.11.20
 */

package com.totec.parser.json

import com.totec.parser.END
import com.totec.parser.FastReader
import com.totec.parser.digitRange
import java.io.Reader
import java.time.Instant

// Heavily based on dsl-json

class FastJsonReader(reader: Reader = Reader.nullReader()) : FastReader(reader) {
	
	var openArrays = 0
	var openObjects = 0
	
	fun startObject() {
		if (readChar() != '{') throw RuntimeException("Could not start JSON object - expected '{' but got $last")
	}
	
	fun endObject() {
		if (readChar() != '}') throw RuntimeException("Could not end JSON object - expected '}' but got $last")
	}
	
	fun checkObjectEnd() {
		if (last != '}') throw RuntimeException("Could not end JSON object - expected '}' but got $last")
	}
	
	fun startArray() {
		if (readChar() != '[') throw RuntimeException("Could not start JSON array - expected '[' but got $last")
	}
	
	fun endArray() {
		if (readChar() != ']') throw RuntimeException("Could not end JSON array - expected ']' but got $last")
	}
	
	fun checkArrayEnd() {
		if (last != ']') throw RuntimeException("Could not end JSON array - expected ']' but got $last")
	}
	
	/**
	 * Jumps to property, reads its value as string and returns it. Returns null if no such property is found.
	 */
	fun readPropertyString(prop: String): String? = if(jumpToProperty(prop)) readString() else null
	
	/**
	 * Reader has to be placed at beginning of a property at a certain hierarchy.
	 * Returns **true** if the property exists and sets reader right after : char.
	 * Returns **false** if property does not exist in current hierarchy
	 */
	fun jumpToProperty(prop: String): Boolean {
		var next = readString()
		while (next != prop && last != Char.END) {
			if (toNextProperty() == false) return false
			next = readString()
		}
		readChar() // :
		return true
	}
	
	/**
	 * Returns **true** if we managed to get to the next property in this hierarchy.
	 * Will skip lower hierarchies.
	 */
	fun toNextProperty(): Boolean {
		readChar() // :
		readChar()
		val success = when (last) {
			'[' -> skipToArrayEnd()
			'{' -> skipToObjectEnd()
			in digitRange -> skipToDigitEnd()
			'"' -> skipToStringEnd()
			else          -> false
		}
		return success && readChar() == ','
	}
	
	fun skipToArrayEnd(): Boolean {
		val arrayGoal = openArrays - 1
		while (openArrays != arrayGoal) {
			if (readChar() == Char.END) return false
		}
		return true
	}
	
	/**
	 * If true, next invocation of [readChar()] will be '}'
	 */
	fun skipBeforeObjectEnd(): Boolean {
		val res = skipToObjectEnd()
		backTrack = true
		return res
	}
	
	/**
	 * If true, next invocation of [readChar()] will be the first character after '}'
	 */
	fun skipToObjectEnd(): Boolean {
		val objectGoal = openObjects - 1
		while (openObjects != objectGoal) {
			if (readChar() == Char.END) return false
		}
		return true
	}
	
	fun skipToDigitEnd(): Boolean {
		while (last != ',') {
			readChar()
			if (last.endsHierarchy()) return false
		}
		backTrack = true
		return true
	}
	
	fun skipToStringEnd(): Boolean {
		var escape = true
		while (last != '"' || escape) {
			escape = last == '\\'
			readChar()
			if (last == Char.END) return false
		}
		return true
	}
	
	fun comma() {
		if (readChar() != ',') throw RuntimeException("Wanted to read comma, but read '$last' instead.")
	}
	
	/**
	 * Places reader so that [last] will equal the last comma or hierarchy end.
	 */
	fun toNextCommaOrClose() {
		while (last.notCommaOrHierarchyEnd() && last != Char.END) readChar()
	}
	
	fun readTimestamp(): Long {
		val s = readString()
		val instant = Instant.parse(s)
		return instant.toEpochMilli()
	}
	
	override fun readChar(): Char {
		val c = super.readChar()
		when (c) {
			'{' -> openObjects++
			'}' -> openObjects--
			'[' -> openArrays++
			']' -> openArrays--
			' ' -> readChar()
		}
		return c
	}
	
	override fun changeReader(newReader: Reader) {
		super.changeReader(newReader)
		openArrays = 0
		openObjects = 0
	}
	
	fun Char.notCommaOrHierarchyEnd() = this.endsHierarchy() == false && this != ','
	
	fun Char.endsHierarchy() = this == ']' || this == '}' || this == Char.END
}