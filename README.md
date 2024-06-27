# Glue Schema Registry serde for kafka-ui

This is pluggable serde implementation for [kafka-ui](https://github.com/kafbat/kafka-ui/).

You can read about Glue Schema [registry](https://docs.aws.amazon.com/glue/latest/dg/schema-registry.html) and how it can be applied for [Kafka usage](https://docs.aws.amazon.com/glue/latest/dg/schema-registry.html).


For properties description and configuration example please see [docker-compose file](docker-compose/setup-example.yaml).

### Building locally

We use `DefaultCredentialsProvider` in tests, so should can configure env as described in its [documentation](https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/auth/credentials/DefaultCredentialsProvider.html). Authorized user should be able to create and delete Glue Schema registries and schemas.

Example:
```
mvn clean test -Daws.accessKeyId="..." -Daws.secretAccessKey="..."
```

### AWS permissions

In order for the serde to serialize and desrialize messages it needs the necessary permissions granting on the role / credentials being used.

- [GetSchema](https://docs.aws.amazon.com/glue/latest/webapi/API_GetSchema.html)
- [GetSchemaVersion](https://docs.aws.amazon.com/glue/latest/webapi/API_GetSchemaVersion.html)


These permissions can be granted against a wildcard resource allow access to all registries and schemas in your account.

Example:
```
{
    "Action": [
        "glue:GetSchema",
        "glue:GetSchemaVersion"
    ],
    "Resource": [
        "*"
    ],
    "Effect": "Allow"
}
```

Or limit to a specific resources using their ARNs.
- registry: `arn:${Partition}:glue:${Region}:${Account}:registry/${RegistryName}` 
- schema: `arn:${Partition}:glue:${Region}:${Account}:schema/${SchemaName}`

Note schema names in the ARN are prefixed with registry name, so to grant permission for all schemas in a registry you can use `${RegistryName}/*`

Example: for account `012345678` in `eu-west-1` to grant access to all schemas in the registry `myGlueRegistry` the following policy is needed.
```
{
    "Action": [
        "glue:GetSchema",
        "glue:GetSchemaVersion"
    ],
    "Resource": [
        "arn:aws:glue:eu-west-1:012345678:schema/myGlueRegistry/*",
        "arn:aws:glue:eu-west-1:012345678:registry/myGlueRegistry/*",
        "arn:aws:glue:eu-west-1:012345678:registry/myGlueRegistry"
    ],
    "Effect": "Allow"
}
```
