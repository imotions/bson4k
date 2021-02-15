package io.imotions.bson4k.encoder

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.modules.SerializersModule
import org.bson.BsonBinary
import org.bson.BsonDocument
import org.bson.BsonDocumentWriter

@ExperimentalSerializationApi
class BsonEncoder(
    override val serializersModule: SerializersModule
) : AbstractEncoder() {
    val classDiscriminator = "__type"

    private val writer: BsonDocumentWriter = BsonDocumentWriter(BsonDocument())
    private var state = State.ROOT

    val document: BsonDocument
        get() {
            writer.flush()
            return writer.document
        }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        when (descriptor.kind) {
            StructureKind.CLASS -> when (state) {
                State.POLYMORPHIC -> state = State.BEGIN
                else -> writer.writeStartDocument()
            }
            StructureKind.MAP -> {
                writer.writeStartDocument()
                state = State.MAP
            }
            StructureKind.OBJECT -> writer.writeStartDocument()
            StructureKind.LIST -> {
                if (state == State.ROOT) throw SerializationException("Top-level arrays are not supported")
                writer.writeStartArray()
            }
            is PolymorphicKind -> {
                writer.writeStartDocument()
                state = State.POLYMORPHIC
            }
            else -> throw SerializationException("Unsupported structure kind: ${descriptor.kind}")
        }
        if (state == State.ROOT) state = State.BEGIN
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        when (descriptor.kind) {
            StructureKind.LIST -> writer.writeEndArray()
            is StructureKind -> if (state != State.POLYMORPHIC) writer.writeEndDocument()
            is PolymorphicKind -> {
                if (state == State.POLYMORPHIC) {
                    writer.writeEndDocument()
                    state = State.BEGIN
                }
            }
            else -> throw SerializationException("Unsupported structure kind: ${descriptor.kind}")
        }
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        when {
            descriptor.kind is StructureKind.CLASS -> writer.writeName(descriptor.getElementName(index))
            descriptor.kind is StructureKind.MAP -> state = when (state) {
                State.MAP_KEY -> State.MAP_VALUE
                else -> State.MAP_KEY
            }
            descriptor.kind is StructureKind.OBJECT -> writer.writeName(descriptor.getElementName(index))
            descriptor.kind is PolymorphicKind && (descriptor.getElementName(index) == "type") ->
                writer.writeName(classDiscriminator)
        }
        return true
    }

    override fun encodeString(value: String) = encodeBsonElement(value, writer::writeString)

    override fun encodeInt(value: Int) = encodeBsonElement(value, writer::writeInt32)

    override fun encodeBoolean(value: Boolean) = encodeBsonElement(value, writer::writeBoolean)

    override fun encodeByte(value: Byte) = encodeInt(value.toInt())

    override fun encodeChar(value: Char) = encodeString(value.toString())

    override fun encodeDouble(value: Double) = encodeBsonElement(value, writer::writeDouble)

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) =
        encodeString(enumDescriptor.getElementName(index))

    override fun encodeFloat(value: Float) = encodeDouble(value.toDouble())

    override fun encodeLong(value: Long) = encodeBsonElement(value, writer::writeInt64)

    override fun encodeNull() = writer.writeNull()

    override fun encodeShort(value: Short) = encodeInt(value.toInt())

    fun encodeByteArray(value: ByteArray) = encodeBsonElement(BsonBinary(value), writer::writeBinaryData)

    /**
     * Encodes date from a value representing ms since epoch
     */
    fun encodeDate(value: Long) {
        encodeBsonElement(value, writer::writeDateTime)
    }

    private fun <T> encodeBsonElement(value: T, writeOps: (T) -> Unit) {
        when (state) {
            State.ROOT -> throw SerializationException("Top-level primitives are not allowed.")
            State.MAP_KEY -> writer.writeName(value.toString())
            else -> writeOps(value)
        }
    }

    internal enum class State {
        ROOT,
        BEGIN,
        POLYMORPHIC,
        MAP,
        MAP_KEY,
        MAP_VALUE
    }
}
