/**
 * Created by ndrsh on 09.11.20
 */

package com.totec.parser.json

// Heavily based on dsl-json

interface ObjectReader<T> {
	/**
	 * You don't have to start the object with [startObject], but you have to close it with [endObject]
	 */
	fun FastJsonReader.readObject(): T?
	
	fun FastJsonReader.readSet(): MutableSet<T>? = mutableSetOf<T>().also { if (addAllToCollection(it)) return it else return@readSet null }
	fun FastJsonReader.readList(): MutableList<T>? = mutableListOf<T>().also { if (addAllToCollection(it)) return it else return@readList null }
	
	/**
	 * Returns true if something was added to the collection and false otherwise
	 */
	fun FastJsonReader.addAllToCollection(collection: MutableCollection<T>): Boolean {
		if (last != '[') throw RuntimeException("Tried to start reading Objects into collection, but last char was $last instead of '['.")
		var somethingAdded = false
		if (readChar() == ']') return somethingAdded // if it is not ']', it has to be '{'
		val first = readObject()
		if (first != null) {
			collection.add(first)
			somethingAdded = true
		}
		while (readChar() == ',') { // if it's not ',', it is assumed to be ']'
			startObject()
			val another = readObject()
			if (another != null) {
				collection.add(another)
				somethingAdded = true
			}
		}
		checkArrayEnd()
		return somethingAdded
	}
}
