/**
 * Created by ndrsh on 02.11.20
 */

@file:Suppress("JAVA_MODULE_DOES_NOT_EXPORT_PACKAGE")

package com.totec.parser

import jdk.internal.math.DoubleConsts
import jdk.internal.math.FDBigInteger
import java.io.Reader
import kotlin.math.max
import kotlin.math.min

// Heavily based on dsl-json

open class FastReader(protected var reader: Reader = Reader.nullReader()) {
	companion object {
		const val MAX_DECIMAL_DIGITS = 15
		const val MAX_DECIMAL_EXPONENT = 308
		const val MIN_DECIMAL_EXPONENT = -324
		const val MAX_NDIGITS = 1100
		const val EXP_SHIFT = DoubleConsts.SIGNIFICAND_WIDTH - 1
		const val FRACT_HOB = 1L shl EXP_SHIFT // assumed High-Order bit
		
		val EXACT_POWS = doubleArrayOf(1.0e0,
		                               1.0e1, 1.0e2, 1.0e3, 1.0e4, 1.0e5,
		                               1.0e6, 1.0e7, 1.0e8, 1.0e9, 1.0e10,
		                               1.0e11, 1.0e12, 1.0e13, 1.0e14, 1.0e15,
		                               1.0e16, 1.0e17, 1.0e18, 1.0e19, 1.0e20,
		                               1.0e21, 1.0e22)
		const val MAX_EXACT_EXP = 22
		
		val BIG_10_POW = doubleArrayOf(1e16, 1e32, 1e64, 1e128, 1e256)
		val TINY_10_POW = doubleArrayOf(1e-16, 1e-32, 1e-64, 1e-128, 1e-256)
	}
	
	private val longPows = longArrayOf(1,
	                                   10,
	                                   100,
	                                   1000,
	                                   10000,
	                                   100_000,
	                                   1_000_000,
	                                   10_000_000,
	                                   100_000_000,
	                                   1_000_000_000,
	                                   10_000_000_000,
	                                   100_000_000_000,
	                                   1_000_000_000_000,
	                                   10_000_000_000_000,
	                                   100_000_000_000_000,
	                                   1_000_000_000_000_000,
	                                   10_000_000_000_000_000)
	
	private val intPows = intArrayOf(1,
	                                 10,
	                                 100,
	                                 1000,
	                                 10000,
	                                 100_000,
	                                 1_000_000,
	                                 10_000_000,
	                                 100_000_000,
	                                 1_000_000_000)
	
	private val doublePows = doubleArrayOf(1.0, 1e1, 1e2, 1e3, 1e4, 1e5, 1e6, 1e7, 1e8, 1e9,
	                                       1e10, 1e11, 1e12, 1e13, 1e14, 1e15, 1e16, 1e17, 1e18, 1e19,
	                                       1e20, 1e21, 1e22, 1e23, 1e24, 1e25, 1e26, 1e27, 1e28, 1e29,
	                                       1e30, 1e31, 1e32, 1e33, 1e34, 1e35, 1e36, 1e37, 1e38, 1e39,
	                                       1e40, 1e41, 1e42, 1e43, 1e44, 1e45, 1e46, 1e47, 1e48, 1e49,
	                                       1e50, 1e51, 1e52, 1e53, 1e54, 1e55, 1e56, 1e57, 1e58, 1e59,
	                                       1e60, 1e61, 1e62, 1e63, 1e64, 1e65, 1e66, 1e67, 1e68)
	
	
	val buff = ByteArray(256)
	var backTrack = false
	var last: Char = Char.START
	
	// digit counts
	var nBase = 0
	var nFrac = 0
	var nExp = 0
	var baseSign = 1
	var expSign = 1
	
	
	fun readString(): String {
		var index = 0
		readChar()
		if (last != '"') throw java.lang.RuntimeException("Could not read string: Got \'$last\', expected \'\"\'")
		do {
			buff[index++] = readChar().toByte()
		} while (last != '"')
		return String(buff, 0, index - 1)
	}
	
	/**
	 * Reads string and places the reader after the ':'
	 */
	fun readProperty(): String {
		val s = readString()
		readChar()
		if (last != ':') backTrack = true
		return s
	}
	
	fun readIntOrNull(): Int? {
		readChar()
		return if (scanNonDecimalNumber() == false) {
			backTrack = false
			when (completeNullOrNan()) {
				0 -> throw RuntimeException("Reader read nullable Int as NaN, but a Int can never be NaN")
				-1 -> null
				else -> throw RuntimeException("Reader tried to read nullable Int - scanNonDecimalNumber() is false (indicating null) but completeNullOrNan() returns 1 (indicating a normal Int)")
			}
		} else calculateInt()
	}
	
	fun readInt(): Int {
		readChar()
		if (scanNonDecimalNumber() == false) throw RuntimeException("Error - Tried to read primitive int in readInt(), but scanNonDecimalNumber() returned false.")
		return calculateInt()
	}
	
	private fun calculateInt(): Int {
		var res = baseSign * intFromBuff(0, nBase - 1)
		if (nExp > 0) {
			val exponent = intFromBuff(nBase, nBase + nExp - 1)
			nExp = 0
			res *= intPows[exponent]
		}
		nBase = 0
		baseSign = 1
		return res
	}
	
	fun readDoubleOrNull(): Double? {
		readChar()
		return if (scanDecimalNumber() == false) {
			backTrack = false
			when (completeNullOrNan()) {
				0 -> Double.NaN
				-1 -> null
				else -> throw RuntimeException("Reader tried to read nullable Double - scanDecimalNumber() is false (indicating null or NaN) but completeNullOrNan() returns 1 (indicating a normal double)")
			}
		} else {
			if (nBase <= 6 && nFrac <= 8 && nExp == 0) readDoubleFast()
			else readDoubleJavaStyle()
		}
	}
	
	fun readDouble(): Double {
		readChar()
		val res =  if (scanDecimalNumber() == false) Double.NaN
		else if (nBase <= 6 && nFrac <= 8 && nExp == 0) readDoubleFast()
		else readDoubleJavaStyle()
		resetNumberInfo()
		return res
	}
	
	private fun readDoubleFast(): Double {
		val base = intFromBuff(0, nBase - 1)
		val frac = intFromBuff(nBase, nBase + nFrac - 1)
		return baseSign * (base * longPows[nFrac] + frac) / doublePows[nFrac]
	}
	
	/**
	 * Returns 1 for success, 0 for NaN and -1 for null
	 */
	fun scanDecimalNumber(): Boolean {
		var success = false
		if (last == '-') {
			baseSign = -1
			readChar()
		}
		while (last.isDigitOrMinus()) {
			success = true
			buff[nBase++] = last.toByte()
			readChar()
			if (last == '.') addFractional()
			if (last == 'E' || last == 'e') addExponent()
		}
		backTrack = true
		return success
	}
	
	fun readLongOrNull(): Long? {
		readChar()
		return if (scanNonDecimalNumber() == false) {
			backTrack = false
			when (completeNullOrNan()) {
				0 -> throw RuntimeException("Reader read nullable Long as NaN, but a Long can never be NaN")
				-1 -> null
				else -> throw RuntimeException("Reader tried to read nullable Long - scanNonDecimalNumber() is false (indicating null) but completeNullOrNan() returns 1 (indicating a normal long)")
			}
		} else calculateLong()
	}
	
	fun readLong(): Long {
		readChar()
		if (scanNonDecimalNumber() == false) throw RuntimeException("Error - Tried to read primitive long in readLong(), but scanNonDecimalNumber() returned false.")
		return calculateLong()
	}
	
	private fun calculateLong(): Long {
		var res = baseSign * longFromBuff(0, nBase - 1)
		if (nExp > 0) {
			val exponent = intFromBuff(nBase, nBase + nExp - 1)
			nExp = 0
			res *= longPows[exponent]
		}
		nBase = 0
		baseSign = 1
		return res
	}
	
	/**
	 * Returns 1 for success, 0 for NaN and -1 for null
	 */
	fun scanNonDecimalNumber(): Boolean {
		var success = false
		if (last == '-') {
			baseSign = -1
			readChar()
		}
		while (last.isDigitOrMinus()) {
			success = true
			buff[nBase++] = last.toByte()
			readChar()
			if (last == 'E' || last == 'e') addExponent()
		}
		backTrack = true
		return success
	}
	
	/**
	 * Checks if there is a number. If there isn't, it completes null or nan and returns false.
	 */
	fun isStringOrCompleteNull(): Boolean {
		return if (readChar() == '"') {
			backTrack = true
			true
		} else {
			completeNullOrNan()
			false
		}
	}
	
	/**
	 * Checks if there is a number. If there isn't, it completes null or nan and returns false.
	 */
	fun isNumberOrCompleteNull(): Boolean {
		return if (readChar().isDigitOrMinus()) {
			backTrack = true
			true
		} else {
			completeNullOrNan()
			false
		}
	}
	
	/**
	 * Returns 1 for success, 0 for NaN and -1 for null
	 */
	fun completeNullOrNan(): Int {
		if (last == 'n') {
			if (readChar() != 'u' || readChar() != 'l' || readChar() != 'l') {
				throw RuntimeException("Thought 'null' is present, but reader read something else.")
			}
			return -1
		} else if (last == 'N') {
			readChar()
			if (last == 'a') {
				if (readChar() != 'N') throw RuntimeException("Thought NaN is present, but reader read something else.")
				return 0
			} else if (last == 'u') {
				if (readChar() != 'l' || readChar() != 'l') throw RuntimeException("Thought 'Null' is present, but reader read something else.")
				return -1
			} else throw RuntimeException("Thought Null or NaN is present, but reader read something else.")
		}
		return 1
	}
	
	fun intFromBuff(start: Int, endInclusive: Int): Int {
		var res = 0
		val l = endInclusive - start
		if (l > 9) throw RuntimeException("Could not read Int from buffer because there are too many digits - digit size is ${l + 1}")
		for (i in start..endInclusive) {
			res = (res shl 3) + (res shl 1) + (buff[i] - 48)
		}
		return res
	}
	
	fun longFromBuff(start: Int, endInclusive: Int): Long {
		val intDigits = min(endInclusive - start + 1, Int.MAX_DIGITS)
		val iRes = intFromBuff(start, start + intDigits - 1)
		return if (start + intDigits <= endInclusive) longFromBuff(iRes, start + intDigits, endInclusive)
		else iRes.toLong()
	}
	
	fun longFromBuff(intInit: Int, start: Int, endInclusive: Int): Long {
		var res = intInit.toLong()
		for (i in start..endInclusive) {
			res = (res shl 3) + (res shl 1) + (buff[i] - 48)
		}
		return res
	}
	
	fun addFractional() {
		readChar()
		while (last.isDigitOrMinus()) {
			buff[nBase + nFrac++] = last.toByte()
			readChar()
		}
	}
	
	fun addExponent() {
		readChar()
		if (last == '-') {
			expSign = -1
			readChar()
		}
		while (last.isDigitOrMinus()) {
			buff[nBase + nFrac + nExp++] = last.toByte()
			readChar()
		}
	}
	
	fun jumpToNext(char: Char): Boolean {
		return if (jumpAfterNext(char)) {
			backTrack = true
			true
		} else false
	}
	
	fun jumpAfterNext(char: Char): Boolean {
		do {
			last = readChar()
			if (last == Char.END) return false
		} while (last != char)
		return true
	}
	
	open fun readChar(): Char {
		if (backTrack == false) {
			last = reader.read().toChar()
		} else {
			backTrack = false
		}
		return last
	}
	
	fun isOpen() = last != Char.END
	
	fun hasRead() = last != Char.START
	
	open fun changeReader(newReader: Reader) {
		reader = newReader
		resetProperties()
	}
	
	private fun resetProperties() {
		last = Char.START
		backTrack = false
		resetNumberInfo()
	}
	
	private fun resetNumberInfo() {
		nBase = 0
		nFrac = 0
		nExp = 0
		baseSign = 1
		expSign = 1
	}
	
	// just for benchmarking
	fun readDoubleJava(): Double {
		var len = 0
		readChar()
		while (last.isDigitOrMinus() || last == 'e' || last == '.' || last == 'E' || last == '-') {
			buff[len] = last.toByte()
			len++
			readChar()
		}
		val s = String(buff, 0, len)
		return java.lang.Double.parseDouble(s)
	}
	
	// most code from FloatingDecimal.java
	fun readDoubleJavaStyle(): Double {
		val baseAndFrac = nBase + nFrac
		val exponent = if (nExp > 0) intFromBuff(baseAndFrac, baseAndFrac + nExp - 1) else 0
		val kDigits = min(baseAndFrac, MAX_DECIMAL_DIGITS + 1)
		
		val leftExp = nBase + expSign * exponent
		val intDigits = min(baseAndFrac, Int.MAX_DIGITS)
		val iRes = intFromBuff(0, intDigits - 1)
		val lngDigits = if (intDigits <= baseAndFrac - 1) longFromBuff(iRes, intDigits, min(baseAndFrac - 1, MAX_DECIMAL_DIGITS))
		else iRes.toLong()
		var dblDigits = lngDigits.toDouble()
		var totalExp = leftExp - kDigits
		var nDigits = baseAndFrac
		if (nDigits <= MAX_DECIMAL_DIGITS) {
			if (totalExp == 0 || dblDigits == 0.0) {
				return baseSign * dblDigits // small floating integer
			} else if (totalExp >= 0) {
				if (totalExp <= MAX_EXACT_EXP) {
					val rValue: Double = dblDigits * doublePows[totalExp]
					return baseSign * rValue
				}
				val slop = MAX_DECIMAL_DIGITS - kDigits
				if (totalExp <= MAX_EXACT_EXP + slop) {
					//
					// We can multiply dValue by 10^(slop)
					// and it is still "small" and exact.
					// Then we can multiply by 10^(exp-slop)
					// with one rounding.
					//
					dblDigits *= doublePows[slop]
					val rValue: Double = dblDigits * doublePows[totalExp - slop]
					return baseSign * rValue
				}
				//
				// Else we have a hard case with a positive exp.
				//
			} else {
				if (totalExp >= -MAX_EXACT_EXP) {
					val rValue: Double = dblDigits / doublePows[-totalExp]
					return baseSign * rValue
				}
				//
				// Else we have a hard case with a negative exp.
				//
			}
		}
		
		//
		// Harder cases:
		// The sum of digits plus exponent is greater than
		// what we think we can do with one error.
		//
		// Start by approximating the right answer by,
		// naively, scaling by powers of 10.
		//
		
		//
		// Harder cases:
		// The sum of digits plus exponent is greater than
		// what we think we can do with one error.
		//
		// Start by approximating the right answer by,
		// naively, scaling by powers of 10.
		//
		if (totalExp > 0) {
			if (leftExp > MAX_DECIMAL_EXPONENT + 1) {
				return if (baseSign == -1) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
			}
			if (totalExp and 15 != 0) {
				dblDigits *= doublePows[totalExp and 15]
			}
			totalExp = totalExp shr 4
			if (totalExp != 0) {
				var j = 0
				while (totalExp > 1) {
					if (totalExp and 1 != 0) {
						dblDigits *= BIG_10_POW[j]
					}
					j++
					totalExp = totalExp shr 1
				}
				//
				// The reason for the weird exp > 1 condition
				// in the above loop was so that the last multiply
				// would get unrolled. We handle it here.
				// It could overflow.
				//
				var t: Double = dblDigits * BIG_10_POW[j]
				if (t.isInfinite()) {
					//
					// It did overflow.
					// Look more closely at the result.
					// If the exponent is just one too large,
					// then use the maximum finite as our estimate
					// value. Else call the result infinity
					// and punt it.
					// ( I presume this could happen because
					// rounding forces the result here to be
					// an ULP or two larger than
					// Double.MAX_VALUE ).
					//
					t = dblDigits / 2.0
					t *= BIG_10_POW[j]
					if (t.isInfinite()) {
						return if (baseSign == -1) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
					}
					t = Double.MAX_VALUE
				}
				dblDigits = t
			}
		} else if (totalExp < 0) {
			totalExp = -totalExp
			if (leftExp < MIN_DECIMAL_EXPONENT - 1) {
				return if (baseSign == -1) -0.0 else 0.0
			}
			if (totalExp and 15 != 0) {
				dblDigits /= doublePows[totalExp and 15]
			}
			totalExp = totalExp shr 4
			if (totalExp != 0) {
				var j = 0
				while (totalExp > 1) {
					if (totalExp and 1 != 0) {
						dblDigits *= TINY_10_POW[j]
					}
					j++
					totalExp = totalExp shr 1
				}
				//
				// The reason for the weird exp > 1 condition
				// in the above loop was so that the last multiply
				// would get unrolled. We handle it here.
				// It could underflow.
				//
				var t: Double = dblDigits * TINY_10_POW[j]
				if (t == 0.0) {
					//
					// It did underflow.
					// Look more closely at the result.
					// If the exponent is just one too small,
					// then use the minimum finite as our estimate
					// value. Else call the result 0.0
					// and punt it.
					// ( I presume this could happen because
					// rounding forces the result here to be
					// an ULP or two less than
					// Double.MIN_VALUE ).
					//
					t = dblDigits * 2.0
					t *= TINY_10_POW[j]
					if (t == 0.0) {
						return if (baseSign == -1) -0.0 else 0.0
					}
					t = Double.MIN_VALUE
				}
				dblDigits = t
			}
		}
		
		//
		// dValue is now approximately the result.
		// The hard part is adjusting it, by comparison
		// with FDBigInteger arithmetic.
		// Formulate the EXACT big-number result as
		// bigD0 * 10^exp
		//
		
		//
		// dValue is now approximately the result.
		// The hard part is adjusting it, by comparison
		// with FDBigInteger arithmetic.
		// Formulate the EXACT big-number result as
		// bigD0 * 10^exp
		//
		if (nDigits > MAX_NDIGITS) {
			nDigits = MAX_NDIGITS + 1
			buff[MAX_NDIGITS] = '1'.toByte()
		}
		val charArr = CharArray(nDigits)
		for (i in 0 until nDigits) charArr[i] = buff[i].toChar()
		var bigD0 = FDBigInteger(lngDigits, charArr, kDigits, nDigits)
		totalExp = leftExp - nDigits
		
		var ieeeBits = java.lang.Double.doubleToRawLongBits(dblDigits) // IEEE-754 bits of double candidate
		val B5 = max(0, -totalExp) // powers of 5 in bigB, value is not modified inside correctionLoop
		val D5 = max(0, totalExp) // powers of 5 in bigD, value is not modified inside correctionLoop
		bigD0 = bigD0.multByPow52(D5, 0)
		bigD0.makeImmutable() // prevent bigD0 modification inside correctionLoop
		var bigD: FDBigInteger? = null
		var prevD2 = 0
		
		correctionLoop@ while (true) {
			// here ieeeBits can't be NaN, Infinity or zero
			var binexp = (ieeeBits ushr EXP_SHIFT).toInt()
			var bigBbits = ieeeBits and DoubleConsts.SIGNIF_BIT_MASK
			if (binexp > 0) {
				bigBbits = bigBbits or FRACT_HOB
			} else { // Normalize denormalized numbers.
				assert(bigBbits != 0L) {
					bigBbits // doubleToBigInt(0.0)
				}
				val leadingZeros = java.lang.Long.numberOfLeadingZeros(bigBbits)
				val shift = leadingZeros - (63 - EXP_SHIFT)
				bigBbits = bigBbits shl shift
				binexp = 1 - shift
			}
			binexp -= DoubleConsts.EXP_BIAS
			val lowOrderZeros = java.lang.Long.numberOfTrailingZeros(bigBbits)
			bigBbits = bigBbits ushr lowOrderZeros
			val bigIntExp = binexp - EXP_SHIFT + lowOrderZeros
			val bigIntNBits = EXP_SHIFT + 1 - lowOrderZeros
			
			//
			// Scale bigD, bigB appropriately for
			// big-integer operations.
			// Naively, we multiply by powers of ten
			// and powers of two. What we actually do
			// is keep track of the powers of 5 and
			// powers of 2 we would use, then factor out
			// common divisors before doing the work.
			//
			var B2 = B5 // powers of 2 in bigB
			var D2 = D5 // powers of 2 in bigD
			var Ulp2: Int // powers of 2 in halfUlp.
			if (bigIntExp >= 0) {
				B2 += bigIntExp
			} else {
				D2 -= bigIntExp
			}
			Ulp2 = B2
			// shift bigB and bigD left by a number s. t.
			// halfUlp is still an integer.
			var hulpbias: Int
			hulpbias = if (binexp <= -DoubleConsts.EXP_BIAS) {
				// This is going to be a denormalized number
				// (if not actually zero).
				// half an ULP is at 2^-(DoubleConsts.EXP_BIAS+EXP_SHIFT+1)
				binexp + lowOrderZeros + DoubleConsts.EXP_BIAS
			} else {
				1 + lowOrderZeros
			}
			B2 += hulpbias
			D2 += hulpbias
			// if there are common factors of 2, we might just as well
			// factor them out, as they add nothing useful.
			val common2 = Math.min(B2, Math.min(D2, Ulp2))
			B2 -= common2
			D2 -= common2
			Ulp2 -= common2
			// do multiplications by powers of 5 and 2
			val bigB = FDBigInteger.valueOfMulPow52(bigBbits, B5, B2)
			if (bigD == null || prevD2 != D2) {
				bigD = bigD0.leftShift(D2)
				prevD2 = D2
			}
			//
			// to recap:
			// bigB is the scaled-big-int version of our floating-point
			// candidate.
			// bigD is the scaled-big-int version of the exact value
			// as we understand it.
			// halfUlp is 1/2 an ulp of bigB, except for special cases
			// of exact powers of 2
			//
			// the plan is to compare bigB with bigD, and if the difference
			// is less than halfUlp, then we're satisfied. Otherwise,
			// use the ratio of difference to halfUlp to calculate a fudge
			// factor to add to the floating value, then go 'round again.
			//
			var diff: FDBigInteger
			var cmpResult: Int
			var overvalue: Boolean
			if (bigB.cmp(bigD).also { cmpResult = it } > 0) {
				overvalue = true // our candidate is too big.
				diff = bigB.leftInplaceSub(bigD) // bigB is not user further - reuse
				if (bigIntNBits == 1 && bigIntExp > -DoubleConsts.EXP_BIAS + 1) {
					// candidate is a normalized exact power of 2 and
					// is too big (larger than Double.MIN_NORMAL). We will be subtracting.
					// For our purposes, ulp is the ulp of the
					// next smaller range.
					Ulp2 -= 1
					if (Ulp2 < 0) {
						// rats. Cannot de-scale ulp this far.
						// must scale diff in other direction.
						Ulp2 = 0
						diff = diff.leftShift(1)
					}
				}
			} else if (cmpResult < 0) {
				overvalue = false // our candidate is too small.
				diff = bigD!!.rightInplaceSub(bigB) // bigB is not user further - reuse
			} else {
				// the candidate is exactly right!
				// this happens with surprising frequency
				break@correctionLoop
			}
			cmpResult = diff.cmpPow52(B5, Ulp2)
			if (cmpResult < 0) {
				// difference is small.
				// this is close enough
				break@correctionLoop
			} else if (cmpResult == 0) {
				// difference is exactly half an ULP
				// round to some other value maybe, then finish
				if (ieeeBits and 1 != 0L) { // half ties to even
					ieeeBits += if (overvalue) -1 else 1.toLong() // nextDown or nextUp
				}
				break@correctionLoop
			} else {
				// difference is non-trivial.
				// could scale addend by ratio of difference to
				// halfUlp here, if we bothered to compute that difference.
				// Most of the time ( I hope ) it is about 1 anyway.
				ieeeBits += if (overvalue) -1 else 1.toLong() // nextDown or nextUp
				if (ieeeBits == 0L || ieeeBits == DoubleConsts.EXP_BIT_MASK) { // 0.0 or Double.POSITIVE_INFINITY
					break@correctionLoop  // oops. Fell off end of range.
				}
				continue  // try again.
			}
		}
		if (baseSign == -1) {
			ieeeBits = ieeeBits or DoubleConsts.SIGN_BIT_MASK
		}
		return java.lang.Double.longBitsToDouble(ieeeBits)
	}
	
	val Int.Companion.MAX_DIGITS
		get() = 9
}

