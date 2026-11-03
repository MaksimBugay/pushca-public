package bmv.pushca.binary.proxy.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.config.PushcaConfig;
import bmv.pushca.binary.proxy.pushca.connection.PushcaWsClientFactory;
import bmv.pushca.binary.proxy.pushca.exception.DownloadLimitExceededException;
import bmv.pushca.binary.proxy.pushca.exception.BinaryNotFoundException;
import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class BinaryProxyServiceTest {
    @Test
    void missingManifestFailsImmediatelyAndSurvivesHumanOnlyCheck() {
        withManifestService((service, pool) -> {
            doAnswer(invocation -> {
                String commandId = invocation.getArgument(0);
                ReflectionTestUtils.invokeMethod(pool, "wsConnectionMessageWasReceivedHandler",
                        commandId + "@@RESPONSE@@{\"error\":\"binary-not-found\"}");
                return commandId;
            }).when(pool).sendCommand(anyString(), any(), any());

            var future = service.requestBinaryManifest("workspace", "missing");
            assertTrue(future.isCompletedExceptionally());
            assertInstanceOf(BinaryNotFoundException.class,
                    assertThrows(CompletionException.class, future::join).getCause());

            var status = new java.util.concurrent.atomic.AtomicReference<HttpStatus>();
            StepVerifier.create(service.requestBinaryManifestWithHumanOnlyCheck(
                            "workspace", "missing", null, null, status::set))
                    .expectError(BinaryNotFoundException.class).verify();
            assertEquals(HttpStatus.NOT_FOUND, status.get());
        });
    }

    @Test
    void commandAcknowledgementDoesNotCompleteManifestWaiter() {
        withManifestService((service, pool) -> {
            doAnswer(invocation -> {
                String commandId = invocation.getArgument(0);
                ReflectionTestUtils.invokeMethod(pool, "wsConnectionMessageWasReceivedHandler",
                        commandId + "@@RESPONSE");
                return commandId;
            }).when(pool).sendCommand(anyString(), any(), any());
            var future = service.requestBinaryManifest("workspace", "binary");
            assertFalse(future.isDone());
            var manifest = new BinaryManifest("binary", "file", "text/plain", null,
                    java.util.List.of(), null, null, "session");
            pool.completeWithResponse("binary", manifest);
            assertSame(manifest, future.join());
        });
    }

    @Test
    void manifestCanArriveBeforeSendReturns() {
        withManifestService((service, pool) -> {
            var manifest = new BinaryManifest("binary", "file", "text/plain", null,
                    java.util.List.of(), null, null, "session");
            doAnswer(invocation -> {
                pool.completeWithResponse("binary", manifest);
                return invocation.getArgument(0, String.class);
            }).when(pool).sendCommand(anyString(), any(), any());
            assertSame(manifest, service.requestBinaryManifest("workspace", "binary").join());
            verify(pool).removeResponseWaiter(anyString());
        });
    }

    private void withManifestService(java.util.function.BiConsumer<BinaryProxyService, WebsocketPool> test) {
        var configuration = new MicroserviceConfiguration("test", "127.0.0.1");
        configuration.responseTimeoutMs = 1000;
        configuration.websocketExecutorPoolSize = 1;
        configuration.delayedExecutorPoolSize = 1;
        var factory = new PushcaWsClientFactory(mock(PushcaConfig.class), configuration);
        var pool = spy(new WebsocketPool(configuration, mock(PushcaConfig.class), factory) {
            @Override public void createWebsocketPool() { }
        });
        try {
            test.accept(new BinaryProxyService(pool, factory, configuration, null), pool);
        } finally {
            pool.destroy();
        }
    }

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
