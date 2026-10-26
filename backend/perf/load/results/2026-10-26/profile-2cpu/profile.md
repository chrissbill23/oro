Execution samples: 3,828

| Component (inclusive) | Samples | Share |
|---|---|---|
| Spring Security | 2,018 | 52.7 % |
| Tomcat (catalina, coyote) | 1,761 | 46.0 % |
| Tomcat | 1,562 | 40.8 % |
| Spring transactions | 1,178 | 30.8 % |
| Oro: live (streams) | 854 | 22.3 % |
| Hibernate / JPA | 802 | 21.0 % |
| Micrometer | 602 | 15.7 % |
| JWT (Nimbus) | 305 | 8.0 % |
| Oro: outbox | 249 | 6.5 % |
| HikariCP (pool) | 234 | 6.1 % |
| PostgreSQL driver (JDBC) | 203 | 5.3 % |
| JDK crypto | 180 | 4.7 % |
| Spring JDBC | 172 | 4.5 % |
| Jackson (JSON) | 160 | 4.2 % |
| Oro: identity resolution | 112 | 2.9 % |
| Logging (logback) | 42 | 1.1 % |
| Oro: attachments | 38 | 1.0 % |
| Oro: rate limits | 33 | 0.9 % |

| Self time, by component or package | Samples | Share |
|---|---|---|
| Hibernate / JPA | 307 | 8.0 % |
| java.util.concurrent | 305 | 8.0 % |
| Tomcat | 238 | 6.2 % |
| org.apache.coyote | 236 | 6.2 % |
| java.util.HashMap | 210 | 5.5 % |
| Micrometer | 187 | 4.9 % |
| org.springframework.web | 135 | 3.5 % |
| Tomcat (catalina, coyote) | 127 | 3.3 % |
| Jackson (JSON) | 110 | 2.9 % |
| jdk.internal.reflect | 95 | 2.5 % |
| java.math.BigInteger | 92 | 2.4 % |
| PostgreSQL driver (JDBC) | 91 | 2.4 % |
| JWT (Nimbus) | 77 | 2.0 % |
| java.lang.String | 73 | 1.9 % |
| java.util.regex | 70 | 1.8 % |
| org.springframework.core | 68 | 1.8 % |
| org.springframework.util | 67 | 1.8 % |
| java.lang.Thread | 66 | 1.7 % |
| java.util.stream | 66 | 1.7 % |
| java.util.UUID | 57 | 1.5 % |
| Spring Security | 56 | 1.5 % |
| jakarta.servlet.ServletRequestWrapper | 54 | 1.4 % |
| sun.nio.ch | 52 | 1.4 % |
| java.util.Objects | 50 | 1.3 % |
| jakarta.servlet.ServletResponseWrapper | 50 | 1.3 % |

| LiveHub method in the stack (inclusive) | Samples | Share |
|---|---|---|
| `com.asata.org.oro.live.LiveHub.drain` | 619 | 16.2 % |
| `com.asata.org.oro.live.LiveHub.send` | 129 | 3.4 % |
| `com.asata.org.oro.live.LiveHub.write` | 77 | 2.0 % |
| `com.asata.org.oro.live.LiveHub.lambda$write$0` | 2 | 0.1 % |

| Top frame (self) | Samples | Share |
|---|---|---|
| `org.apache.tomcat.util.net.NioEndpoint$Poller.timeout` | 102 | 2.7 % |
| `java.util.HashMap.getNode` | 83 | 2.2 % |
| `java.util.concurrent.ConcurrentHashMap.get` | 81 | 2.1 % |
| `java.math.BigInteger.oddModPow` | 73 | 1.9 % |
| `org.apache.tomcat.util.net.SocketWrapperBase.writeBlocking` | 70 | 1.8 % |
| `org.apache.coyote.AbstractProtocol.lambda$startAsyncTimeout$0` | 68 | 1.8 % |
| `java.lang.Thread.interrupted` | 66 | 1.7 % |
| `org.apache.coyote.http11.Http11OutputBuffer.doWrite` | 63 | 1.6 % |
| `java.util.UUID.equals` | 57 | 1.5 % |
| `java.util.HashMap.putVal` | 54 | 1.4 % |
| `org.apache.catalina.connector.Response.getBufferSize` | 49 | 1.3 % |
| `java.util.Objects.equals` | 46 | 1.2 % |
| `java.util.concurrent.ConcurrentHashMap$Traverser.advance` | 44 | 1.1 % |
| `jakarta.servlet.ServletResponseWrapper.getBufferSize` | 41 | 1.1 % |
| `java.util.concurrent.ThreadPerTaskExecutor.execute` | 39 | 1.0 % |
| `java.math.MutableBigInteger.mulsub` | 33 | 0.9 % |
| `ch.qos.logback.classic.LoggerContext.getTurboFilterChainDecision_0_3OrMore` | 33 | 0.9 % |
| `org.springframework.web.context.request.async.StandardServletAsyncWebRequest$LifecycleHttpServletResponse.getOutputStream` | 33 | 0.9 % |
| `org.apache.coyote.AbstractProtocol$ConnectionHandler.process` | 30 | 0.8 % |
| `java.util.HashMap.hash` | 30 | 0.8 % |
| `jdk.internal.reflect.MethodHandleObjectFieldAccessorImpl.set` | 29 | 0.8 % |
| `java.lang.String.<init>` | 28 | 0.7 % |
| `java.util.stream.AbstractPipeline.wrapSink` | 25 | 0.7 % |
| `tools.jackson.core.json.UTF8JsonGenerator.writeString` | 24 | 0.6 % |
| `jakarta.servlet.ServletRequestWrapper.getAttribute` | 23 | 0.6 % |

| First Oro frame in the stack | Samples | Share |
|---|---|---|
| `com.asata.org.oro.live.LiveHub.drain` | 619 | 16.2 % |
| `com.asata.org.oro.config.web.RequestSizeFilter.doFilterInternal` | 245 | 6.4 % |
| `com.asata.org.oro.identity.service.IdentityService.externalIds` | 195 | 5.1 % |
| `com.asata.org.oro.message.service.MessageService.extras` | 189 | 4.9 % |
| `com.asata.org.oro.conversation.service.ConversationAccess.requireActiveMember` | 121 | 3.2 % |
| `com.asata.org.oro.message.service.MessageService.after` | 111 | 2.9 % |
| `com.asata.org.oro.context.DbSession.enter` | 96 | 2.5 % |
| `com.asata.org.oro.message.service.MessageService.send` | 89 | 2.3 % |
| `com.asata.org.oro.live.LiveHub.send` | 86 | 2.2 % |
| `com.asata.org.oro.live.LiveHub.write` | 77 | 2.0 % |
| `com.asata.org.oro.config.web.RequestIdFilter.doFilterInternal` | 56 | 1.5 % |
| `com.asata.org.oro.live.LiveHub$Scope.equals` | 43 | 1.1 % |
| `com.asata.org.oro.conversation.service.ConversationAccess.requireConversation` | 33 | 0.9 % |
| `com.asata.org.oro.enums.CodedEnum.of` | 32 | 0.8 % |
| `com.asata.org.oro.conversation.service.ConversationAccess.lambda$lockConversation$0` | 32 | 0.8 % |
| `com.asata.org.oro.config.ratelimit.RateLimitInterceptor.preHandle` | 27 | 0.7 % |
| `com.asata.org.oro.identity.service.IdentityService$$SpringCGLIB$$0.externalIds` | 22 | 0.6 % |
| `com.asata.org.oro.identity.service.IdentityService$$SpringCGLIB$$0.resolveOrProvision` | 21 | 0.5 % |
| `com.asata.org.oro.conversation.service.ConversationService.movePositions` | 18 | 0.5 % |
| `com.asata.org.oro.identity.service.OrganizationAccessService$$SpringCGLIB$$0.isGranted` | 18 | 0.5 % |
