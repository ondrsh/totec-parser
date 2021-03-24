package com.totec.parser.json

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import java.io.StringReader
import java.time.Instant
import kotlin.random.Random

class FastJsonReaderTest : StringSpec(
	{
		
		val fr = FastJsonReader()
		val json = """{"table":"orderBookL2","action":"insert","data":[{"symbol":"LINKUSDTZ20","id":43299976601,"side":"Sell","size":192,"price":11.6995},{"symbol":"LINKUSDTZ20","id":43299977621,"side":"Buy","size":192,"price":11.1895}]}"""
		
		"readProperty()" {
			fr.changeReader(StringReader("""{"price":601356958}"""))
			fr.startObject()
			fr.readProperty() shouldBe "price"
			fr.readChar() shouldBe '6'
			
			fr.changeReader(StringReader("""{"price"}"""))
			fr.startObject()
			fr.readProperty() shouldBe "price"
			fr.readChar() shouldBe '}'
		}
		
		"readStringProperty()" {
			fr.changeReader(StringReader("""{"price":123.123123,"name":"lala1213"}"""))
			fr.startObject()
			fr.readPropertyString("name") shouldMatch "lala1213"
			
			fr.changeReader(StringReader("""{"price":123.123123,"name":"lala1213"}"""))
			fr.startObject()
			fr.readPropertyString("alter") shouldBe null
		}
		
		"readTimestamp()" {
			val timestampsToLongs = mutableListOf<Pair<String, Long>>()
			repeat(100) {
				val ms = 1604882936985 - Random.nextLong(10_000, 100_000_000)
				val ts = Instant.ofEpochMilli(ms)
				timestampsToLongs.add(""""$ts"""" to ms)
			}
			timestampsToLongs.forEach {
				fr.changeReader(StringReader(it.first))
				fr.readTimestamp() shouldBeExactly  it.second
			}
		}
		
		"readIntOrNull()" {
			fr.changeReader(StringReader("""{"price":601356958}"""))
			fr.jumpAfterNext(':')
			fr.readIntOrNull() shouldBe 601356958
			
			fr.changeReader(StringReader("""{"price":null}"""))
			fr.jumpAfterNext(':')
			fr.readIntOrNull() shouldBe null
			
			fr.changeReader(StringReader("""{"price":Null}"""))
			fr.jumpAfterNext(':')
			fr.readIntOrNull() shouldBe null
			
			fr.changeReader(StringReader("""{"price":NaN}"""))
			fr.jumpAfterNext(':')
			shouldThrow<Exception> {
				fr.readIntOrNull() shouldBe Double.NaN
			}.message shouldBe "Reader read nullable Int as NaN, but a Int can never be NaN"
			
			fr.changeReader(StringReader("""{"price":Nal}"""))
			fr.jumpAfterNext(':')
			shouldThrow<RuntimeException> {
				fr.readIntOrNull()
			}.message shouldBe "Thought NaN is present, but reader read something else."
			
			fr.changeReader(StringReader("""{"price":Nelli}"""))
			fr.jumpAfterNext(':')
			shouldThrow<RuntimeException> {
				fr.readIntOrNull()
			}.message shouldBe "Thought Null or NaN is present, but reader read something else."
		}
		
		"readLongOrNull()" {
			fr.changeReader(StringReader("""{"size":3150}"""))
			fr.jumpAfterNext(':')
			fr.readLongOrNull() shouldBe 3150L
			
			fr.changeReader(StringReader("""{"size":null}"""))
			fr.jumpAfterNext(':')
			fr.readLongOrNull() shouldBe null
			
			fr.changeReader(StringReader("""{"size":Null}"""))
			fr.jumpAfterNext(':')
			fr.readLongOrNull() shouldBe null
			
			fr.changeReader(StringReader("""{"size":NaN}"""))
			fr.jumpAfterNext(':')
			shouldThrow<RuntimeException> {
				fr.readLongOrNull() shouldBe null
			}.message shouldBe "Reader read nullable Long as NaN, but a Long can never be NaN"
			
			fr.changeReader(StringReader("""{"size":Niel}"""))
			fr.jumpAfterNext(':')
			shouldThrow<RuntimeException> {
				fr.readLongOrNull() shouldBe null
			}.message shouldBe "Thought Null or NaN is present, but reader read something else."
		}
		
		"readDoubleOrNull" {
			fr.changeReader(StringReader("""{"price":8.25601356958059E19}"""))
			fr.jumpAfterNext(':')
			fr.readDouble() shouldBeExactly 8.25601356958059E19
			
			fr.changeReader(StringReader("""{"price":-0.12}"""))
			fr.jumpAfterNext(':')
			fr.isNumberOrCompleteNull() shouldBe true
			fr.readDouble() shouldBeExactly -0.12
			
			
			fr.changeReader(StringReader("""{"price":null}"""))
			fr.jumpAfterNext(':')
			fr.readDoubleOrNull() shouldBe null
			
			fr.changeReader(StringReader("""{"price":Null}"""))
			fr.jumpAfterNext(':')
			fr.readDoubleOrNull() shouldBe null
			
			fr.changeReader(StringReader("""{"price":NaN}"""))
			fr.jumpAfterNext(':')
			fr.readDoubleOrNull() shouldBe Double.NaN
			
			fr.changeReader(StringReader("""{"price":Nal}"""))
			fr.jumpAfterNext(':')
			shouldThrow<RuntimeException> {
				fr.readDoubleOrNull() shouldBe Double.NaN
			}.message shouldBe "Thought NaN is present, but reader read something else."
			
			fr.changeReader(StringReader("""{"price":Nelli}"""))
			fr.jumpAfterNext(':')
			shouldThrow<RuntimeException> {
				fr.readDoubleOrNull() shouldBe Double.NaN
			}.message shouldBe "Thought Null or NaN is present, but reader read something else."
			
			fr.changeReader(StringReader("""{"price":Nuli}"""))
			fr.jumpAfterNext(':')
			shouldThrow<RuntimeException> {
				fr.readDoubleOrNull() shouldBe null
			}.message shouldBe "Thought 'Null' is present, but reader read something else."
		}
		
		"skip whitespace" {
			fr.changeReader(StringReader("""{"table":"orderBookL2", "action":"insert"}"""))
			fr.jumpAfterNext(':')
			fr.readString() shouldMatch "orderBookL2"
			fr.readChar() shouldBe ','
			fr.readString() shouldMatch "action"
		}
		
		"skipToObjectEnd()" {
			fr.changeReader(StringReader("""{"table":"orderBookL2", "action":"insert"}x"""))
			fr.readChar() // should open object
			fr.skipToObjectEnd() shouldBe true
			fr.readChar() shouldBe 'x'
			
			fr.changeReader(StringReader("""{"table":"orderBookL2", "action":{"lyly":"haha"}x}y"""))
			fr.jumpToNext('2')
			fr.skipToObjectEnd() shouldBe true
			fr.readChar() shouldBe 'y'
			
			fr.changeReader(StringReader("""{"table":"orderBookL2", "action":{"lyly":"haha"}x}y"""))
			fr.jumpToNext('y')
			fr.skipToObjectEnd() shouldBe true
			fr.readChar() shouldBe 'x'
			
			fr.changeReader(StringReader("""{"table":"orderBookL2", "action":{"lyly":"haha"}xy"""))
			fr.jumpToNext('2')
			fr.skipToObjectEnd() shouldBe false
		}
		
		"skipToArrayEnd()" {
			fr.changeReader(StringReader("""{"table":"orderBookL2", "data":[{"symbol":"LINKUSDTZ20","id":43299976601,"side":"Sell","size":192,"price":11.6995}]y}"""))
			fr.readChar() // should open object
			fr.skipToArrayEnd() shouldBe false
			
			fr.changeReader(StringReader("""{"table":"orderBookL2", "data":[{"symbol":"LINKUSDTZ20","id":43299976601,"side":"Sell","size":192,"price":11.6995}]y}"""))
			fr.jumpToNext('y')
			fr.skipToArrayEnd() shouldBe true
			fr.readChar() shouldBe 'y'
			
			fr.changeReader(StringReader("""{"table":"orderBookL2", "data":[{"symbol":"LINKUSDTZ20","data":[43299976601]i,"side":"Sell","size":192,"price":11.6995}]y}"""))
			fr.jumpToNext('y')
			fr.skipToArrayEnd() shouldBe true
			fr.readChar() shouldBe 'y'
			
			fr.changeReader(StringReader("""{"table":"orderBookL2", "data":[{"symbol":"LINKUSDTZ20","data":[43299976601]i,"side":"Sell","size":192,"price":11.6995}]y}"""))
			fr.jumpToNext('4')
			fr.skipToArrayEnd() shouldBe true
			fr.readChar() shouldBe 'i'
		}
		
		"skipToDigitEnd()" {
			fr.changeReader(StringReader("""{"symbol":"LINKUSDTZ20","id":43299976601,"side":"Sell","""))
			fr.startObject()
			fr.jumpToProperty("id")
			fr.readChar() shouldBe '4'
			fr.skipToDigitEnd()
			fr.readChar() shouldBe ','
			fr.readProperty() shouldBe "side"
		}
		
		"skipToStringEnd()" {
			fr.changeReader(StringReader("""{"table":"Das ist ein \"Table\" der tricky ist"}"""))
			fr.startObject() // {
			fr.readChar() // "
			fr.skipToStringEnd() shouldBe true
			fr.readChar() // :
			fr.readChar() // "
			fr.readChar() shouldBe 'D'
			
			fr.changeReader(StringReader("""{"table":"Das ist ein \"Table\" der tricky ist"}"""))
			fr.jumpToNext('D')
			fr.skipToStringEnd() shouldBe true
			fr.readChar() shouldBe '}'
			
			fr.changeReader(StringReader("""{"table":"Das ist ein \"Table\" der tricky ist}"""))
			fr.jumpToNext('D')
			fr.skipToStringEnd() shouldBe false
		}
		
		"jumpToProperty()" {
			fr.changeReader(StringReader("""{"table":"orderBookL2", "data":[{"symbol":"LINKUSDTZ20"}], "action":"insert"}"""))
			fr.startObject()
			fr.jumpToProperty("action") shouldBe true
			fr.readString() shouldMatch "insert"
			
			fr.changeReader(StringReader("""{"table":"orderBookL2", "data":[{"symbol":"LINKUSDTZ20"}], "action":"insert"}"""))
			fr.startObject()
			fr.jumpToProperty("erfunden") shouldBe false
		}
		
		"comma()" {
			fr.changeReader(StringReader("le,a"))
			fr.readChar() shouldBe 'l'
			fr.readChar() shouldBe 'e'
			fr.comma()
			fr.readChar() shouldBe 'a'
			
			fr.changeReader(StringReader("le,a"))
			fr.readChar() shouldBe 'l'
			shouldThrow<RuntimeException> {
				fr.comma()
			}.message shouldMatch "Wanted to read comma, but read 'e' instead."
			
		}
		
		val actionLast = """{"table":"orderBookL2","data":[{"symbol":"LINKUSDTZ20","id":43299976601,"side":"Sell","size":192,"price":11.6995},{"symbol":"LINKUSDTZ20","id":43299977621,"side":"Buy","size":192,"price":11.1895}]},"action":"insert""""
		
	})
