/**
 * Created by ndrsh on 02.11.20
 */

package com.totec.parser

import org.openjdk.jmh.runner.Runner
import org.openjdk.jmh.runner.options.OptionsBuilder
import java.io.File
import kotlin.reflect.KClass

/**
 * Makes it easier to execute JMH benchmarks.
 * @sample main() = JsonBenchmark::class.bench()
 * @author ndrsh
 */
fun KClass<*>.bench() {
	val benchmarkFolder = "benchmarks"
	File(benchmarkFolder).mkdirs()
	val name = this.java.simpleName
	val path = benchmarkFolder + File.separator + name
	File(path).mkdir()
	val options = OptionsBuilder()
		.include(name)
		.output(path + File.separator + "benchmark.log")
		.build()
	Runner(options).run()
}
