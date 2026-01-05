package io.kafbat.ui.serde.glue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.FloatNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.LongNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;

/**
 * Utility class for converting Avro to JSON with proper logical type handling.
 * Based on kafbat/kafka-ui JsonAvroConversion implementation.
 * 
 * @see <a href="https://github.com/kafbat/kafka-ui/blob/main/api/src/main/java/io/kafbat/ui/util/jsonschema/JsonAvroConversion.java">Original implementation</a>
 */
public class JsonAvroConversion {

  private static final JsonMapper MAPPER = new JsonMapper();
  private static final Schema NULL_SCHEMA = Schema.create(Schema.Type.NULL);

  private JsonAvroConversion() {
  }

  /**
   * Converts Avro object to JsonNode with proper logical type handling.
   */
  @SuppressWarnings("unchecked")
  public static JsonNode convertAvroToJson(Object obj, Schema avroSchema) {
    if (obj == null) {
      return NullNode.getInstance();
    }
    return switch (avroSchema.getType()) {
      case RECORD -> {
        var rec = (GenericData.Record) obj;
        ObjectNode node = MAPPER.createObjectNode();
        for (Schema.Field field : avroSchema.getFields()) {
          var fieldVal = rec.get(field.name());
          if (fieldVal != null) {
            node.set(field.name(), convertAvroToJson(fieldVal, field.schema()));
          }
        }
        yield node;
      }
      case MAP -> {
        ObjectNode node = MAPPER.createObjectNode();
        ((Map<?, ?>) obj).forEach((k, v) -> node.set(k.toString(), convertAvroToJson(v, avroSchema.getValueType())));
        yield node;
      }
      case ARRAY -> {
        var list = (List<Object>) obj;
        ArrayNode node = MAPPER.createArrayNode();
        list.forEach(e -> node.add(convertAvroToJson(e, avroSchema.getElementType())));
        yield node;
      }
      case ENUM -> new TextNode(obj.toString());
      case UNION -> {
        int unionIdx = GenericData.get().resolveUnion(avroSchema, obj);
        Schema selectedType = avroSchema.getTypes().get(unionIdx);
        
        // For nullable unions [null, type], flatten - return value directly
        if (avroSchema.getTypes().size() == 2 && avroSchema.getTypes().contains(NULL_SCHEMA)) {
          yield convertAvroToJson(obj, selectedType);
        }
        
        // For complex unions, wrap with type name
        ObjectNode node = MAPPER.createObjectNode();
        node.set(
            selectUnionTypeFieldName(avroSchema, selectedType, unionIdx),
            convertAvroToJson(obj, selectedType)
        );
        yield node;
      }
      case STRING -> {
        if (isLogicalType(avroSchema)) {
          yield processLogicalType(obj, avroSchema);
        }
        yield new TextNode(obj.toString());
      }
      case LONG -> {
        if (isLogicalType(avroSchema)) {
          yield processLogicalType(obj, avroSchema);
        }
        yield new LongNode((Long) obj);
      }
      case INT -> {
        if (isLogicalType(avroSchema)) {
          yield processLogicalType(obj, avroSchema);
        }
        yield new IntNode((Integer) obj);
      }
      case FLOAT -> new FloatNode((Float) obj);
      case DOUBLE -> new DoubleNode((Double) obj);
      case BOOLEAN -> BooleanNode.valueOf((Boolean) obj);
      case NULL -> NullNode.getInstance();
      case BYTES -> {
        if (isLogicalType(avroSchema)) {
          yield processLogicalType(obj, avroSchema);
        }
        ByteBuffer bytes = (ByteBuffer) obj;
        yield new TextNode(new String(bytes.array(), StandardCharsets.ISO_8859_1));
      }
      case FIXED -> {
        if (isLogicalType(avroSchema)) {
          yield processLogicalType(obj, avroSchema);
        }
        var fixed = (GenericData.Fixed) obj;
        yield new TextNode(new String(fixed.bytes(), StandardCharsets.ISO_8859_1));
      }
    };
  }

  private static String selectUnionTypeFieldName(Schema unionSchema,
                                                 Schema chosenType,
                                                 int chosenTypeIdx) {
    var types = unionSchema.getTypes();
    if (types.size() == 2 && types.contains(NULL_SCHEMA)) {
      return chosenType.getName();
    }
    for (int i = 0; i < types.size(); i++) {
      if (i != chosenTypeIdx && chosenType.getName().equals(types.get(i).getName())) {
        return chosenType.getFullName();
      }
    }
    return chosenType.getName();
  }

  private static JsonNode processLogicalType(Object obj, Schema schema) {
    return findConversion(schema)
        .map(c -> c.conversion.apply(obj, schema))
        .orElseGet(() -> new TextNode(obj.toString()));
  }

  private static Optional<LogicalTypeConversion> findConversion(Schema schema) {
    String logicalTypeName = schema.getLogicalType().getName();
    return Stream.of(LogicalTypeConversion.values())
        .filter(t -> t.name.equalsIgnoreCase(logicalTypeName))
        .findFirst();
  }

  private static boolean isLogicalType(Schema schema) {
    return schema.getLogicalType() != null;
  }

  enum LogicalTypeConversion {
    UUID("uuid", (obj, schema) -> new TextNode(obj.toString())),

    DECIMAL("decimal", (obj, schema) -> {
      BigDecimal decimal;
      if (obj instanceof BigDecimal) {
        decimal = (BigDecimal) obj;
      } else if (obj instanceof ByteBuffer) {
        ByteBuffer buffer = (ByteBuffer) obj;
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        int scale = (Integer) schema.getObjectProp("scale");
        decimal = new BigDecimal(new java.math.BigInteger(bytes), scale);
      } else if (obj instanceof GenericData.Fixed) {
        byte[] bytes = ((GenericData.Fixed) obj).bytes();
        int scale = (Integer) schema.getObjectProp("scale");
        decimal = new BigDecimal(new java.math.BigInteger(bytes), scale);
      } else {
        return new TextNode(obj.toString());
      }
      return new DecimalNode(decimal);
    }),

    DATE("date", (obj, schema) -> {
      if (obj instanceof LocalDate) {
        return new TextNode(obj.toString());
      }
      if (obj instanceof Integer) {
        return new TextNode(LocalDate.ofEpochDay((Integer) obj).toString());
      }
      return new TextNode(obj.toString());
    }),

    TIME_MILLIS("time-millis", (obj, schema) -> {
      if (obj instanceof LocalTime) {
        return new TextNode(obj.toString());
      }
      if (obj instanceof Integer) {
        return new TextNode(
            LocalTime.ofNanoOfDay(TimeUnit.MILLISECONDS.toNanos((Integer) obj)).toString());
      }
      return new TextNode(obj.toString());
    }),

    TIME_MICROS("time-micros", (obj, schema) -> {
      if (obj instanceof LocalTime) {
        return new TextNode(obj.toString());
      }
      if (obj instanceof Long) {
        return new TextNode(
            LocalTime.ofNanoOfDay(TimeUnit.MICROSECONDS.toNanos((Long) obj)).toString());
      }
      return new TextNode(obj.toString());
    }),

    TIMESTAMP_MILLIS("timestamp-millis", (obj, schema) -> {
      if (obj instanceof Instant) {
        return new TextNode(obj.toString());
      }
      if (obj instanceof Long) {
        return new TextNode(Instant.ofEpochMilli((Long) obj).toString());
      }
      return new TextNode(obj.toString());
    }),

    TIMESTAMP_MICROS("timestamp-micros", (obj, schema) -> {
      if (obj instanceof Instant) {
        return new TextNode(obj.toString());
      }
      if (obj instanceof Long) {
        long microsFromEpoch = (Long) obj;
        long epochSeconds = microsFromEpoch / 1_000_000L;
        long nanoAdjustment = (microsFromEpoch % 1_000_000L) * 1_000L;
        return new TextNode(Instant.ofEpochSecond(epochSeconds, nanoAdjustment).toString());
      }
      return new TextNode(obj.toString());
    }),

    LOCAL_TIMESTAMP_MILLIS("local-timestamp-millis", (obj, schema) -> {
      if (obj instanceof LocalDateTime) {
        return new TextNode(obj.toString());
      }
      if (obj instanceof Long) {
        Instant instant = Instant.ofEpochMilli((Long) obj);
        return new TextNode(LocalDateTime.ofInstant(instant, ZoneOffset.UTC).toString());
      }
      return new TextNode(obj.toString());
    }),

    LOCAL_TIMESTAMP_MICROS("local-timestamp-micros", (obj, schema) -> {
      if (obj instanceof LocalDateTime) {
        return new TextNode(obj.toString());
      }
      if (obj instanceof Long) {
        long microsFromEpoch = (Long) obj;
        long epochSeconds = microsFromEpoch / 1_000_000L;
        long nanoAdjustment = (microsFromEpoch % 1_000_000L) * 1_000L;
        Instant instant = Instant.ofEpochSecond(epochSeconds, nanoAdjustment);
        return new TextNode(LocalDateTime.ofInstant(instant, ZoneOffset.UTC).toString());
      }
      return new TextNode(obj.toString());
    });

    private final String name;
    private final BiFunction<Object, Schema, JsonNode> conversion;

    LogicalTypeConversion(String name, BiFunction<Object, Schema, JsonNode> conversion) {
      this.name = name;
      this.conversion = conversion;
    }
  }
}
