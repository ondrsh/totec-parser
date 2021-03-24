package com.totec.parser.json

import com.totec.parser.FastReader
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.ints.shouldBeExactly
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import org.apache.commons.io.IOUtils
import java.io.StringReader
import kotlin.RuntimeException
import kotlin.random.Random

class FastReaderTest : StringSpec(
	{
		
		
		val miniString = """X"table"X"""
		val fr = FastReader()
		
		"readString()" {
			fr.changeReader(StringReader(miniString))
			fr.readChar()
			fr.readString() shouldBe "table"
		}
		
		"readInt()" {
			(1..30_000).map { Random.nextInt() }.forEach {
				fr.changeReader(StringReader(it.toString()))
				fr.readInt() shouldBeExactly it
			}
			
			fr.changeReader(StringReader("""{"size":0}"""))
			fr.jumpAfterNext(':')
			fr.readLong() shouldBeExactly 0
			
			fr.changeReader(StringReader("""{"size":-100000}"""))
			fr.jumpAfterNext(':')
			fr.readLong() shouldBeExactly -100_000
			
			fr.changeReader(StringReader("""{"size":3150}"""))
			fr.jumpAfterNext(':')
			fr.readLong() shouldBeExactly 3150
			
			fr.changeReader(StringReader("""{"size":314E6}"""))
			fr.jumpAfterNext(':')
			fr.readLong() shouldBeExactly 314_000_000
			
			fr.changeReader(StringReader("""{"size":-314E6}"""))
			fr.jumpAfterNext(':')
			fr.readLong() shouldBeExactly -314_000_000
			
			fr.changeReader(StringReader("""{"size":-1E2}"""))
			fr.jumpAfterNext(':')
			fr.readLong() shouldBeExactly -100
			
			fr.changeReader(StringReader("""{"size":1E2}"""))
			fr.jumpAfterNext(':')
			fr.readLong() shouldBeExactly 100
		}
		
		"readLong()" {
			val someLarge = 5924683853693125761
			val someLargeString = someLarge.toString()
			fr.changeReader(StringReader(someLargeString))
			fr.readLong() shouldBeExactly someLarge
			
			(1..30_000).map { Random.nextLong() }.forEach {
				fr.changeReader(StringReader(it.toString()))
				fr.readLong() shouldBeExactly it
			}
			
			fr.changeReader(StringReader("""{"size":0}"""))
			fr.jumpAfterNext(':')
			fr.readLong() shouldBeExactly 0L
			
			fr.changeReader(StringReader("""{"size":3150}"""))
			fr.jumpAfterNext(':')
			fr.readLong() shouldBeExactly 3150L
			
			fr.changeReader(StringReader("""{"size":31504E6}"""))
			fr.jumpAfterNext(':')
			fr.readLong() shouldBeExactly 31504_000_000L
			
			fr.changeReader(StringReader("""{"size":3504E15}"""))
			fr.jumpAfterNext(':')
			fr.readLong() shouldBeExactly 3504_000_000_000_000_000L
		}
		
		"readDouble()" {
			val nums = (1..10_000).map { Random.nextDouble(-10.0, 10.0).toString() }.toMutableList().apply {
				addAll((1..10_000).map { Random.nextDouble(Double.MIN_VALUE, Double.MAX_VALUE).toString() }.toList())
			}
			
			for (num in nums) {
				fr.changeReader(StringReader(num))
				val myDouble = fr.readDouble()
				fr.changeReader(StringReader(num))
				val myDoubleJava = fr.readDoubleJava()
				
				val byteArray = IOUtils.toByteArray(num)
				val javaDouble = java.lang.Double.parseDouble(String(byteArray, 0, byteArray.size))
				
				myDouble shouldBeExactly javaDouble
				myDoubleJava shouldBeExactly javaDouble
			}
			
			fr.changeReader(StringReader("""{"price":NaN}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBe Double.NaN
			
			fr.changeReader(StringReader("""{"price":8.25601356958059E19}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 8.25601356958059E19
			
			fr.changeReader(StringReader("""{"price":-2.12469000191E59}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly -2.12469000191E59
			
			fr.changeReader(StringReader("""{"price":1.112285925815E8}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 1.112285925815E8
			
			fr.changeReader(StringReader("""{"price":2.4356550281647877E39}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 2.4356550281647877E39
			
			fr.changeReader(StringReader("""{"price":2.3694861734514803E19}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 2.3694861734514803E19
			
			fr.changeReader(StringReader("""{"price":5.4E-2}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 0.054
			
			fr.changeReader(StringReader("""{"price":5.4E-3}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 0.0054
			
			fr.changeReader(StringReader("""{"price":54.93}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 54.93
			
			fr.changeReader(StringReader("""{"price":-54.93}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly -54.93
			
			fr.changeReader(StringReader("""{"price":159.120200E3}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 159120.2
			
			fr.changeReader(StringReader("""{"price":159.1202E13}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 1591202_000_000_000.0
			
			fr.changeReader(StringReader("""{"price":159.120200E-3}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 0.1591202
			
			fr.changeReader(StringReader("""{"price":159.120200E-8}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 0.000001591202
			
			fr.changeReader(StringReader("""{"price":9.92895612E-9}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 0.00000000992895612
			
			fr.changeReader(StringReader("""{"price":1.22895612E-10}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 1.22895612E-10
			
			
			fr.changeReader(StringReader("""{"price":1.228956128293E-5}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 0.00001228956128293
			
			fr.changeReader(StringReader("""{"price":1.228956128293E-6}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 1.228956128293E-6
		}
		
		"readDoubleFast()" {
			fr.changeReader(StringReader("300.62303"))
			fr.readDouble() shouldBeExactly 300.62303
			
			repeat(100_000) {
				val base = Random.nextInt(-999_999, 999_999)
				val fractional = Random.nextLong(0, 99_999_999L)
				val string = "$base.$fractional"
				fr.changeReader(StringReader(string))
				val d = fr.readDouble()
				d shouldBeExactly java.lang.Double.parseDouble(string)
			}
		}
		
		val miniJson = """{"table":"orderBookL2","action":"insert"}"""
		
		"jumpToNext()" {
			fr.changeReader(StringReader(miniJson))
			fr.jumpToNext(':') shouldBe true
			fr.last shouldBe ':'
			fr.readChar() shouldBe ':'
			fr.readChar() shouldBe '"'
			fr.readChar() shouldBe 'o'
			fr.readChar() shouldBe 'r'
			fr.jumpToNext('*') shouldBe false
		}
		
		"jumpAfterNext()" {
			fr.changeReader(StringReader(miniJson))
			fr.hasRead() shouldBe false
			fr.jumpAfterNext(':') shouldBe true
			fr.hasRead() shouldBe true
			fr.last shouldBe ':'
			fr.readChar() shouldBe '"'
			fr.readChar() shouldBe 'o'
			fr.jumpAfterNext('L') shouldBe true
			fr.readChar() shouldBe '2'
			fr.jumpAfterNext('*') shouldBe false
			fr.isOpen() shouldBe false
			fr.hasRead() shouldBe true
		}
		
	})
