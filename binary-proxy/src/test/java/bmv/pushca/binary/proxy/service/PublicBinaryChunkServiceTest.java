package bmv.pushca.binary.proxy.service;

import static bmv.pushca.binary.proxy.pushca.model.Command.SEND_UPLOAD_BINARY_APPEAL;
import static bmv.pushca.binary.proxy.pushca.model.Datagram.buildDatagramId;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.calculateSha256;
import static bmv.pushca.binary.proxy.pushca.util.BmvObjectUtils.concatParts;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import bmv.pushca.binary.proxy.config.MicroserviceConfiguration;
import bmv.pushca.binary.proxy.config.PushcaConfig;
import bmv.pushca.binary.proxy.pushca.connection.PushcaWsClientFactory;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import bmv.pushca.binary.proxy.pushca.model.ResponseWaiter;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class PublicBinaryChunkServiceTest {
  @Test
  void activatesNonzeroChunkBeforeSendingAndDoesNotRequestTheNextChunk() {
    var pool = mock(WebsocketPool.class);
    var configuration = new MicroserviceConfiguration("test", "127.0.0.1");
    configuration.responseTimeoutMs = 1000;
    var factory = new PushcaWsClientFactory(mock(PushcaConfig.class), configuration);
    var proxyService = new BinaryProxyService(pool, factory, configuration, null);
    var service = new PublicBinaryChunkService(pool, proxyService, configuration, factory);
    byte[] bytes = {1, 2, 3};
    var datagram = new Datagram(bytes.length, calculateSha256(bytes), null, 42);
    var pending = new ConcurrentLinkedQueue<String>();
    String waiterId = concatParts(buildDatagramId("video", 42, factory.pushcaClient.hashCode()), "session");

    var future = service.requestBinaryChunk("workspace", "session", "video", datagram, pending);

    var order = inOrder(pool);
    order.verify(pool).registerResponseWaiter(
        eq(waiterId), ArgumentMatchers.<ResponseWaiter<byte[]>>any());
    order.verify(pool).registerDownloadSession("video", "session");
    order.verify(pool).activateResponseWaiter(waiterId);
    order.verify(pool).sendCommand(isNull(), eq(SEND_UPLOAD_BINARY_APPEAL),
        argThat(metadata -> List.of(42).equals(metadata.get("requestedChunks"))));
    assertTrue(pending.contains(waiterId));
    var waiter = (ResponseWaiter<byte[]>) future;
    assertTrue(waiter.isResponseValid(bytes));
    assertFalse(waiter.isResponseValid(new byte[] {1, 2, 4}));

    future.complete(bytes);

    assertTrue(pending.isEmpty());
    verify(pool).removeResponseWaiter(waiterId);
    verify(pool, times(1)).sendCommand(any(), any(), any());
  }
}
