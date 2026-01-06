package io.kafbat.ui.serde.glue;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecordBuilder;
import org.junit.jupiter.api.Test;

class JsonAvroConversionTest {

  @Test
  void testDecimalFormatting() {
    var schema = new Schema.Parser().parse(
        """
            {
              "type": "record",
              "name": "TestDecimalRecord",
              "fields": [
                {
                  "name": "amount",
                  "type": { "type": "bytes", "logicalType": "decimal", "precision": 20, "scale": 10 }
                },
                {
                  "name": "zero_amount",
                  "type": { "type": "bytes", "logicalType": "decimal", "precision": 20, "scale": 10 }
                },
                {
                  "name": "whole_number",
                  "type": { "type": "bytes", "logicalType": "decimal", "precision": 10, "scale": 2 }
                },
                {
                  "name": "round_number",
                  "type": { "type": "bytes", "logicalType": "decimal", "precision": 10, "scale": 2 }
                }
              ]
            }
            """);

    var record = new GenericRecordBuilder(schema)
        .set("amount", new BigDecimal("1368.5000000000"))
        .set("zero_amount", new BigDecimal("0.0000000000"))
        .set("whole_number", new BigDecimal("42.00"))
        .set("round_number", new BigDecimal("10"))
        .build();

    String json = JsonUtil.avroRecordToJson(record);

    // Verify decimals are output as numbers (not strings or binary)
    assertTrue(json.contains("\"amount\":1368.5"),
        "Decimal should be formatted as number: " + json);

    assertTrue(json.contains("\"zero_amount\":0"),
        "Zero decimal should be formatted as number: " + json);

    assertTrue(json.contains("\"whole_number\":42"),
        "Whole number decimal should be formatted as number: " + json);

    assertTrue(json.contains("\"round_number\":10"),
        "Round number decimal should be formatted as number: " + json);
  }

  @Test
  void testNullableUnionFlattening() {
    var schema = new Schema.Parser().parse(
        """
            {
              "type": "record",
              "name": "TestNullableRecord",
              "fields": [
                {
                  "name": "nullable_int",
                  "default": null,
                  "type": ["null", "int"]
                },
                {
                  "name": "nullable_string",
                  "default": null,
                  "type": ["null", "string"]
                },
                {
                  "name": "nullable_null",
                  "default": null,
                  "type": ["null", "long"]
                }
              ]
            }
            """);

    var record = new GenericRecordBuilder(schema)
        .set("nullable_int", 42)
        .set("nullable_string", "hello")
        .set("nullable_null", null)
        .build();

    String json = JsonUtil.avroRecordToJson(record);

    // Verify nullable int is flattened (not wrapped in {"int": 42})
    assertTrue(json.contains("\"nullable_int\":42"),
        "Nullable int should be flattened to direct value: " + json);
    assertFalse(json.contains("\"int\":42"),
        "Nullable int should not be wrapped with type name: " + json);

    // Verify nullable string is flattened
    assertTrue(json.contains("\"nullable_string\":\"hello\""),
        "Nullable string should be flattened to direct value: " + json);
    assertFalse(json.contains("\"string\":\"hello\""),
        "Nullable string should not be wrapped with type name: " + json);

    // Verify null value is not present in output (nulls are omitted)
    assertFalse(json.contains("nullable_null"),
        "Null values should be omitted from output: " + json);
  }

  @Test
  void testComplexUnionNotFlattened() {
    // Complex unions (more than 2 types, or 2 non-null types) should still be wrapped
    var schema = new Schema.Parser().parse(
        """
            {
              "type": "record",
              "name": "TestComplexUnionRecord",
              "fields": [
                {
                  "name": "multi_type",
                  "type": ["null", "int", "string"]
                }
              ]
            }
            """);

    var record = new GenericRecordBuilder(schema)
        .set("multi_type", 42)
        .build();

    String json = JsonUtil.avroRecordToJson(record);

    // Complex unions should still be wrapped with type name
    assertTrue(json.contains("\"int\":42"),
        "Complex union should be wrapped with type name: " + json);
  }

  @Test
  void testAvroLogicalTypesJsonConversion() {
    // Schema with multiple logical types
    var schema = new Schema.Parser().parse(
        """
            {
              "type": "record",
              "name": "TestLogicalTypesRecord",
              "fields": [
                {
                  "name": "lt_date",
                  "type": { "type": "int", "logicalType": "date" }
                },
                {
                  "name": "lt_uuid",
                  "type": { "type": "string", "logicalType": "uuid" }
                },
                {
                  "name": "lt_decimal",
                  "type": { "type": "bytes", "logicalType": "decimal", "precision": 10, "scale": 2 }
                },
                {
                  "name": "lt_time_millis",
                  "type": { "type": "int", "logicalType": "time-millis" }
                },
                {
                  "name": "lt_time_micros",
                  "type": { "type": "long", "logicalType": "time-micros" }
                },
                {
                  "name": "lt_timestamp_millis",
                  "type": { "type": "long", "logicalType": "timestamp-millis" }
                },
                {
                  "name": "lt_timestamp_micros",
                  "type": { "type": "long", "logicalType": "timestamp-micros" }
                },
                {
                  "name": "lt_local_timestamp_millis",
                  "type": { "type": "long", "logicalType": "local-timestamp-millis" }
                },
                {
                  "name": "lt_local_timestamp_micros",
                  "type": { "type": "long", "logicalType": "local-timestamp-micros" }
                }
              ]
            }
            """);

    // Test with already-converted logical type values (e.g., LocalDate, Instant)
    var recordWithConvertedTypes = new GenericRecordBuilder(schema)
        .set("lt_date", LocalDate.of(1991, 8, 14))
        .set("lt_uuid", UUID.fromString("a37b75ca-097c-5d46-6119-f0637922e908"))
        .set("lt_decimal", new BigDecimal("123.45"))
        .set("lt_time_millis", LocalTime.of(10, 15, 30, 1_000_000)) // 10:15:30.001
        .set("lt_time_micros", LocalTime.of(10, 15, 30, 123_456_000)) // 10:15:30.123456
        .set("lt_timestamp_millis", Instant.parse("2007-12-03T10:15:30.123Z"))
        .set("lt_timestamp_micros", Instant.parse("2007-12-13T10:15:30.123456Z"))
        .set("lt_local_timestamp_millis", LocalDateTime.of(2017, 12, 3, 10, 15, 30, 123_000_000))
        .set("lt_local_timestamp_micros", LocalDateTime.of(2017, 12, 13, 10, 15, 30, 123_456_000))
        .build();

    String json = JsonUtil.avroRecordToJson(recordWithConvertedTypes);

    // Verify date is formatted as ISO date string, not as integer
    assertTrue(json.contains("\"lt_date\":\"1991-08-14\""),
        "Date should be formatted as ISO date string: " + json);

    // Verify UUID is formatted as string
    assertTrue(json.contains("\"lt_uuid\":\"a37b75ca-097c-5d46-6119-f0637922e908\""),
        "UUID should be formatted as string: " + json);

    // Verify decimal is formatted as number, not binary
    assertTrue(json.contains("\"lt_decimal\":123.45"),
        "Decimal should be formatted as number: " + json);

    // Verify time-millis is formatted as ISO time string
    assertTrue(json.contains("\"lt_time_millis\":\"10:15:30.001\""),
        "Time-millis should be formatted as ISO time string: " + json);

    // Verify time-micros is formatted as ISO time string
    assertTrue(json.contains("\"lt_time_micros\":\"10:15:30.123456\""),
        "Time-micros should be formatted as ISO time string: " + json);

    // Verify timestamp-millis is formatted as ISO instant string
    assertTrue(json.contains("\"lt_timestamp_millis\":\"2007-12-03T10:15:30.123Z\""),
        "Timestamp-millis should be formatted as ISO instant string: " + json);

    // Verify timestamp-micros is formatted as ISO instant string
    assertTrue(json.contains("\"lt_timestamp_micros\":\"2007-12-13T10:15:30.123456Z\""),
        "Timestamp-micros should be formatted as ISO instant string: " + json);

    // Verify local-timestamp-millis is formatted as ISO local datetime string
    assertTrue(json.contains("\"lt_local_timestamp_millis\":\"2017-12-03T10:15:30.123\""),
        "Local-timestamp-millis should be formatted as ISO local datetime string: " + json);

    // Verify local-timestamp-micros is formatted as ISO local datetime string
    assertTrue(json.contains("\"lt_local_timestamp_micros\":\"2017-12-13T10:15:30.123456\""),
        "Local-timestamp-micros should be formatted as ISO local datetime string: " + json);
  }

  @Test
  void testAvroLogicalTypesWithRawValues() {
    // Schema with logical types that may come as raw values
    var schema = new Schema.Parser().parse(
        """
            {
              "type": "record",
              "name": "TestRawLogicalTypesRecord",
              "fields": [
                {
                  "name": "lt_date",
                  "type": { "type": "int", "logicalType": "date" }
                },
                {
                  "name": "lt_time_millis",
                  "type": { "type": "int", "logicalType": "time-millis" }
                },
                {
                  "name": "lt_timestamp_millis",
                  "type": { "type": "long", "logicalType": "timestamp-millis" }
                }
              ]
            }
            """);

    // Test with raw numeric values (days since epoch, millis, etc.)
    // Use Java to compute correct values to avoid calculation errors
    LocalDate testDate = LocalDate.of(1991, 8, 14);
    LocalTime testTime = LocalTime.of(10, 15, 30, 1_000_000);
    Instant testInstant = Instant.parse("2007-12-03T10:15:30.123Z");

    var recordWithRawValues = new GenericRecordBuilder(schema)
        .set("lt_date", (int) testDate.toEpochDay())
        .set("lt_time_millis", (int) (testTime.toNanoOfDay() / 1_000_000))
        .set("lt_timestamp_millis", testInstant.toEpochMilli())
        .build();

    String json = JsonUtil.avroRecordToJson(recordWithRawValues);

    // Verify date is converted from days since epoch to ISO date string
    assertTrue(json.contains("\"lt_date\":\"1991-08-14\""),
        "Date from raw int should be formatted as ISO date string: " + json);

    // Verify time-millis is converted from millis to ISO time string
    assertTrue(json.contains("\"lt_time_millis\":\"10:15:30.001\""),
        "Time-millis from raw int should be formatted as ISO time string: " + json);

    // Verify timestamp-millis is converted from millis to ISO instant string
    assertTrue(json.contains("\"lt_timestamp_millis\":\"2007-12-03T10:15:30.123Z\""),
        "Timestamp-millis from raw long should be formatted as ISO instant string: " + json);
  }
}
