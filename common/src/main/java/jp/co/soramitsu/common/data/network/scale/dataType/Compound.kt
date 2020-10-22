@file:Suppress("ClassName")

package jp.co.soramitsu.common.data.network.scale.dataType

import io.emeraldpay.polkaj.scale.ScaleCodecReader
import io.emeraldpay.polkaj.scale.ScaleCodecWriter
import io.emeraldpay.polkaj.scale.writer.BoolWriter
import jp.co.soramitsu.common.data.network.scale.EncodableStruct
import jp.co.soramitsu.common.data.network.scale.Schema

class tuple<A, B>(
    private val a: DataType<A>,
    private val b: DataType<B>
) : DataType<Pair<A, B>>() {
    override fun read(reader: ScaleCodecReader): Pair<A, B> {
        val a = a.read(reader)
        val b = b.read(reader)

        return a to b
    }

    override fun write(writer: ScaleCodecWriter, value: Pair<A, B>) {
        a.write(writer, value.first)
        b.write(writer, value.second)
    }
}

@Suppress("UNCHECKED_CAST")
class optional<T>(private val dataType: DataType<T>) : DataType<T?>() {
    override fun read(reader: ScaleCodecReader): T? {
        if (dataType is boolean) {
            return when (reader.readByte().toInt()) {
                0 -> null
                1 -> false as T?
                2 -> true as T?
                else -> throw IllegalArgumentException("Not a optional boolean")
            }
        }

        val some: Boolean = reader.readBoolean()

        return if (some) dataType.read(reader) else null
    }

    override fun write(writer: ScaleCodecWriter, value: T?) {
        if (dataType is boolean) {
            writer.writeOptional(BoolWriter(), value as Boolean)
        } else {
            writer.writeOptional(dataType, value)
        }
    }
}

class list<T>(private val dataType: DataType<T>) : DataType<List<T>>() {
    override fun read(reader: ScaleCodecReader): List<T> {
        val size = compactInt.read(reader)
        val result = mutableListOf<T>()

        repeat(size.toInt()) {
            val element = dataType.read(reader)
            result.add(element)
        }

        return result
    }

    override fun write(writer: ScaleCodecWriter, value: List<T>) {
        val size = value.size.toBigInteger()
        compactInt.write(writer, size)

        value.forEach {
            dataType.write(writer, it)
        }
    }
}

@Suppress("UNCHECKED_CAST")
class scalable<S : Schema<S>>(private val schema: Schema<S>) : DataType<EncodableStruct<S>>() {
    override fun read(reader: ScaleCodecReader): EncodableStruct<S> {
        return schema.read(reader)
    }

    override fun write(writer: ScaleCodecWriter, struct: EncodableStruct<S>) {
        schema.write(writer, struct)
    }
}