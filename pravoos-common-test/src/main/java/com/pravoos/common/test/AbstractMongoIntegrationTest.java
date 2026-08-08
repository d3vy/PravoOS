package com.pravoos.common.test;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractMongoIntegrationTest {

  @Container @ServiceConnection
  static final MongoDBContainer MONGO = new MongoDBContainer(DockerImageName.parse("mongo:7"));
}
