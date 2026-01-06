package io.kafbat.ui.serde.glue;

import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.DatumReader;
import org.apache.avro.io.DecoderFactory;

class JsonUtil {

  private JsonUtil() {
  }

  static String avroRecordToJson(GenericRecord record) {
    return JsonAvroConversion.toJsonString(record, record.getSchema());
  }

  static String protoMsgToJson(DynamicMessage msg) {
    try {
      return JsonFormat.printer()
          .includingDefaultValueFields()
          .omittingInsignificantWhitespace()
          .print(msg);
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  static Object avroFromJson(String json, Schema avroSchema) {
    DatumReader<Object> reader = new GenericDatumReader<>(avroSchema);
    try {
      return reader.read(null, DecoderFactory.get().jsonDecoder(avroSchema, json));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static Object protoFromJson(String json, ProtobufSchema schema) {
    DynamicMessage.Builder message = schema.newMessageBuilder();
    try {
      JsonFormat.parser().merge(json, message);
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
    return message.build();
  }

}
