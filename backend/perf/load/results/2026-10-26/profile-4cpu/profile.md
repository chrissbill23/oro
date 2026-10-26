Execution samples: 5,748

| Component (inclusive) | Samples | Share |
|---|---|---|
| Spring Security | 3,109 | 54.1 % |
| Tomcat (catalina, coyote) | 2,746 | 47.8 % |
| Tomcat | 2,337 | 40.7 % |
| Spring transactions | 1,768 | 30.8 % |
| Oro: live (streams) | 1,333 | 23.2 % |
| Hibernate / JPA | 1,223 | 21.3 % |
| Micrometer | 820 | 14.3 % |
| JWT (Nimbus) | 445 | 7.7 % |
| HikariCP (pool) | 383 | 6.7 % |
| PostgreSQL driver (JDBC) | 337 | 5.9 % |
| Oro: outbox | 317 | 5.5 % |
| Spring JDBC | 269 | 4.7 % |
| JDK crypto | 251 | 4.4 % |
| Jackson (JSON) | 224 | 3.9 % |
| Oro: identity resolution | 159 | 2.8 % |
| Oro: attachments | 58 | 1.0 % |
| Logging (logback) | 41 | 0.7 % |
| Oro: rate limits | 38 | 0.7 % |

| Self time, by component or package | Samples | Share |
|---|---|---|
| Hibernate / JPA | 447 | 7.8 % |
| java.util.concurrent | 424 | 7.4 % |
| org.apache.coyote | 297 | 5.2 % |
| java.util.HashMap | 293 | 5.1 % |
| Micrometer | 261 | 4.5 % |
| Tomcat (catalina, coyote) | 224 | 3.9 % |
| Tomcat | 219 | 3.8 % |
| org.springframework.web | 217 | 3.8 % |
| PostgreSQL driver (JDBC) | 163 | 2.8 % |
| java.util.Objects | 152 | 2.6 % |
| Jackson (JSON) | 150 | 2.6 % |
| java.util.Collections$UnmodifiableCollection$1 | 147 | 2.6 % |
| java.math.BigInteger | 145 | 2.5 % |
| jdk.internal.reflect | 128 | 2.2 % |
| java.util.stream | 123 | 2.1 % |
| JWT (Nimbus) | 113 | 2.0 % |
| java.util.regex | 113 | 2.0 % |
| java.lang.Thread | 110 | 1.9 % |
| java.lang.String | 108 | 1.9 % |
| org.springframework.util | 104 | 1.8 % |
| jakarta.servlet.http | 103 | 1.8 % |
| sun.nio.ch | 100 | 1.7 % |
| Spring Security | 90 | 1.6 % |
| org.springframework.core | 89 | 1.5 % |
| jakarta.servlet.ServletRequestWrapper | 73 | 1.3 % |

| LiveHub method in the stack (inclusive) | Samples | Share |
|---|---|---|
| `com.asata.org.oro.live.LiveHub.drain` | 1,022 | 17.8 % |
| `com.asata.org.oro.live.LiveHub.send` | 167 | 2.9 % |
| `com.asata.org.oro.live.LiveHub.write` | 112 | 1.9 % |
| `com.asata.org.oro.live.LiveHub.closeExpired` | 1 | 0.0 % |
| `com.asata.org.oro.live.LiveHub.lambda$subjects$1` | 1 | 0.0 % |

| Top frame (self) | Samples | Share |
|---|---|---|
| `org.apache.tomcat.util.net.SocketWrapperBase.writeBlocking` | 147 | 2.6 % |
| `java.util.Collections$UnmodifiableCollection$1.<init>` | 141 | 2.5 % |
| `java.util.Objects.equals` | 136 | 2.4 % |
| `org.apache.coyote.http11.filters.ChunkedOutputFilter.calculateChunkHeader` | 112 | 1.9 % |
| `java.lang.Thread.interrupted` | 110 | 1.9 % |
| `java.math.BigInteger.oddModPow` | 104 | 1.8 % |
| `org.apache.coyote.AbstractProtocol.lambda$startAsyncTimeout$0` | 87 | 1.5 % |
| `java.util.HashMap.getNode` | 83 | 1.4 % |
| `org.apache.catalina.connector.Response.getBufferSize` | 76 | 1.3 % |
| `java.util.HashMap.putVal` | 73 | 1.3 % |
| `java.util.concurrent.ConcurrentHashMap$Traverser.advance` | 67 | 1.2 % |
| `java.util.concurrent.ConcurrentHashMap.get` | 66 | 1.1 % |
| `java.util.concurrent.ThreadPerTaskExecutor.execute` | 65 | 1.1 % |
| `jakarta.servlet.ServletResponseWrapper.getBufferSize` | 62 | 1.1 % |
| `jdk.internal.reflect.MethodHandleObjectFieldAccessorImpl.set` | 56 | 1.0 % |
| `org.apache.catalina.connector.OutputBuffer.isBlocking` | 48 | 0.8 % |
| `org.springframework.web.context.request.async.StandardServletAsyncWebRequest$LifecycleHttpServletResponse.getOutputStream` | 47 | 0.8 % |
| `java.math.MutableBigInteger.mulsub` | 47 | 0.8 % |
| `java.nio.ByteBuffer.mark` | 45 | 0.8 % |
| `java.util.HashMap.removeNode` | 45 | 0.8 % |
| `java.util.stream.AbstractPipeline.wrapSink` | 44 | 0.8 % |
| `java.util.HashMap.hash` | 43 | 0.7 % |
| `jakarta.servlet.http.HttpServletRequestWrapper.getMethod` | 40 | 0.7 % |
| `java.util.Locale.equals` | 34 | 0.6 % |
| `tools.jackson.core.json.UTF8JsonGenerator.writeString` | 33 | 0.6 % |

| First Oro frame in the stack | Samples | Share |
|---|---|---|
| `com.asata.org.oro.live.LiveHub.drain` | 1,022 | 17.8 % |
| `com.asata.org.oro.config.web.RequestSizeFilter.doFilterInternal` | 375 | 6.5 % |
| `com.asata.org.oro.message.service.MessageService.extras` | 322 | 5.6 % |
| `com.asata.org.oro.identity.service.IdentityService.externalIds` | 298 | 5.2 % |
| `com.asata.org.oro.message.service.MessageService.after` | 209 | 3.6 % |
| `com.asata.org.oro.conversation.service.ConversationAccess.requireActiveMember` | 177 | 3.1 % |
| `com.asata.org.oro.context.DbSession.enter` | 135 | 2.3 % |
| `com.asata.org.oro.live.LiveHub$Scope.equals` | 114 | 2.0 % |
| `com.asata.org.oro.live.LiveHub.write` | 112 | 1.9 % |
| `com.asata.org.oro.config.web.RequestIdFilter.doFilterInternal` | 105 | 1.8 % |
| `com.asata.org.oro.message.service.MessageService.send` | 91 | 1.6 % |
| `com.asata.org.oro.identity.service.IdentityService$$SpringCGLIB$$0.externalIds` | 56 | 1.0 % |
| `com.asata.org.oro.conversation.service.ConversationAccess.requireConversation` | 55 | 1.0 % |
| `com.asata.org.oro.live.LiveHub.send` | 53 | 0.9 % |
| `com.asata.org.oro.message.service.MessageService$$SpringCGLIB$$0.extras` | 43 | 0.7 % |
| `com.asata.org.oro.enums.CodedEnum.of` | 43 | 0.7 % |
| `com.asata.org.oro.identity.service.IdentityService$$SpringCGLIB$$0.resolveOrProvision` | 38 | 0.7 % |
| `com.asata.org.oro.conversation.service.ConversationAccess.lambda$lockConversation$0` | 25 | 0.4 % |
| `com.asata.org.oro.config.ratelimit.RateLimitInterceptor.preHandle` | 25 | 0.4 % |
| `com.asata.org.oro.message.service.MessageService$$SpringCGLIB$$0.get` | 22 | 0.4 % |
