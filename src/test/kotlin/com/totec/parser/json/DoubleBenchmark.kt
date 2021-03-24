/**
 * Created by ndrsh on 02.11.20
 */

package com.totec.parser.json

import com.dslplatform.json.DslJson
import com.dslplatform.json.NumberConverter
import com.totec.parser.FastReader
import com.totec.parser.bench
import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.CharSequenceReader
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.TimeUnit
import kotlin.RuntimeException
import kotlin.random.Random

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
@Warmup(time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(time = 1000, timeUnit = TimeUnit.MILLISECONDS)
@Threads(1)
open class DoubleBenchmark {
	
	
	val fr = FastReader()
	val numsNumeric = (1..100_000).map { Random.nextDouble(-10.0, 10.0) }
	val sum = numsNumeric.sum()
	val numStrings = numsNumeric.map { it.toString() }
	var doubleByteArrays = numStrings.map { IOUtils.toByteArray(it) }
	var doubleReaders = numStrings.map {
		FastReader(CharSequenceReader(it))
	}
	val dsl = DslJson(DslJson.Settings<Any>().includeServiceLoader())
	val reader = dsl.newReader()
	
	
	@State(Scope.Benchmark)
	open class DoubleState {
		val dbl = 8500.1122
		val dblString = "8500.1122,X08222108213"
		val dblReader = BufferedReader(StringReader("8500.1122,X08222108213"))
	}
	
	@Benchmark
	@Fork(2)
	fun parseDoubleJava(state: DoubleState, bh: Blackhole): Double {
		state.dblReader.mark(256)
		fr.changeReader(state.dblReader)
		val charArray = state.dblString.toCharArray()
		val s = String(charArray, 0, 9)
		val d = java.lang.Double.parseDouble(s)
		state.dblReader.reset()
		bh.consume(state.dblReader)
		bh.consume(charArray)
		bh.consume(s)
		if (d != state.dbl) throw RuntimeException()
		return d
	}
	
	@Benchmark
	@Fork(2)
	fun scanDouble(state: DoubleState, bh: Blackhole) {
		state.dblReader.mark(256)
		fr.changeReader(state.dblReader)
		fr.scanDecimalNumber()
		state.dblReader.reset()
		bh.consume(state.dblReader)
	}
	
	@Benchmark
	@Fork(2)
	fun parseDoubleMine(state: DoubleState, bh: Blackhole): Double {
		state.dblReader.mark(256)
		fr.changeReader(state.dblReader)
		val d = fr.readDouble()
		state.dblReader.reset()
		bh.consume(state.dblReader)
		if (d != state.dbl) throw RuntimeException()
		return d
	}
	
	@Benchmark
	@Fork(2)
	fun parseDoubleMineJava(state: DoubleState, bh: Blackhole): Double {
		state.dblReader.mark(256)
		fr.changeReader(state.dblReader)
		val d = fr.readDoubleJava()
		state.dblReader.reset()
		bh.consume(state.dblReader)
		if (d != state.dbl) throw RuntimeException()
		return d
	}
	
	
	@Benchmark
	@Fork(2)
	fun parseDoubleDslJson(state: DoubleState, bh: Blackhole): Double {
		state.dblReader.mark(256)
		reader.process(state.dblString.byteInputStream())
		reader.nextToken
		val d = NumberConverter.deserializeDouble(reader)
		state.dblReader.reset()
		bh.consume(state.dblString)
		bh.consume(state.dblReader)
		if (d != state.dbl) throw RuntimeException()
		return d
	}
	
}

fun main() = DoubleBenchmark::class.bench()
