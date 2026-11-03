package bmv.pushca.binary.proxy.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.config.PushcaConfig;
import bmv.pushca.binary.proxy.pushca.connection.PushcaWsClientFactory;
import bmv.pushca.binary.proxy.pushca.exception.DownloadLimitExceededException;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BinaryProxyServiceTest {
    @Test
    void serverRejectionFailsChunkImmediatelyAndDoesNotRequestNextChunk() {
        var configuration = new MicroserviceConfiguration("test", "127.0.0.1");
        configuration.responseTimeoutMs = 1000;
        configuration.websocketExecutorPoolSize = 1;
        configuration.delayedExecutorPoolSize = 1;
        var factory = new PushcaWsClientFactory(mock(PushcaConfig.class), configuration);
        // Suppress connection creation; retain the real waiter registry and response parsing.
        var pool = spy(new WebsocketPool(configuration, mock(PushcaConfig.class), factory) {
            @Override public void createWebsocketPool() { }
        });
        try {
            doAnswer(invocation -> {
                String commandId = invocation.getArgument(0);
                ReflectionTestUtils.invokeMethod(pool, "wsConnectionMessageWasReceivedHandler",
                        commandId + "@@RESPONSE@@{\"error\":\"number-of-downloads-exceeded\"}");
                return commandId;
            }).when(pool).sendCommand(anyString(), any(), any());
            var service = new BinaryProxyService(pool, factory, configuration, null);
            var pending = new ConcurrentLinkedQueue<String>();
            var future = service.requestBinaryChunk("workspace", "session", "binary",
                    new Datagram(3, "hash", null, 0), 1, pending);

            assertTrue(future.isCompletedExceptionally());
            var exception = assertThrows(CompletionException.class, future::join);
            assertInstanceOf(DownloadLimitExceededException.class, exception.getCause());
            verify(pool, times(1)).sendCommand(anyString(), any(), any());
        } finally {
            pool.destroy();
        }
    }
}
