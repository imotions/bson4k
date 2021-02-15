package io.imotions.bson4k

import io.imotions.bson4k.decoder.BsonDecoder
import io.imotions.bson4k.encoder.BsonEncoder
import kotlinx.serialization.*
import kotlinx.serialization.modules.SerializersModule
import org.bson.BsonDocument
import org.bson.BsonDocumentReader

@ExperimentalSerializationApi
class Bson(val configuration: BsonConf) : SerialFormat, StringFormat {

    override val serializersModule: SerializersModule
        get() = configuration.serializersModule

    fun <T> encodeToBsonDocument(serializer: SerializationStrategy<T>, value: T): BsonDocument {
        val encoder = BsonEncoder(serializersModule)
        encoder.encodeSerializableValue(serializer, value)
        return encoder.document
    }

    inline fun <reified T> encodeToBsonDocument(value: T): BsonDocument = encodeToBsonDocument(serializer(), value)

    fun <T> decodeFromBsonDocument(deserializer: DeserializationStrategy<T>, document: BsonDocument): T {
        val reader = BsonDocumentReader(document)
        val decoder = BsonDecoder(reader, serializersModule)
        return decoder.decodeSerializableValue(deserializer)
    }

    inline fun <reified T> decodeFromBsonDocument(document: BsonDocument): T =
        decodeFromBsonDocument(serializer(), document)

    override fun <T> decodeFromString(deserializer: DeserializationStrategy<T>, string: String): T {
        return decodeFromBsonDocument(deserializer, BsonDocument.parse(string))
    }

    override fun <T> encodeToString(serializer: SerializationStrategy<T>, value: T): String {
        return encodeToBsonDocument(serializer, value).toJson()
    }
}

@ExperimentalSerializationApi
fun Bson(builderAction: BsonBuilder.() -> Unit): Bson {
    val builder = BsonBuilder(BsonConf())
    builder.builderAction()
    return Bson(builder.build())
}

@ExperimentalSerializationApi
class BsonBuilder internal constructor(conf: BsonConf) {
    var classDiscriminator = conf.classDiscriminator
    var serializersModule = conf.serializersModule

    fun build(): BsonConf {
        require(!classDiscriminator.contains("""[$.]""".toRegex())) {
            "Class discriminator cannot contain illegal BSON field characters: [$.]"
        }
        return BsonConf(
            classDiscriminator = classDiscriminator,
            serializersModule = serializersModule
        )
    }
}
