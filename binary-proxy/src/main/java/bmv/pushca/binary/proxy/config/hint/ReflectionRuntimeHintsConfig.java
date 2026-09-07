package bmv.pushca.binary.proxy.config.hint;

import bmv.pushca.binary.proxy.api.request.CreatePrivateUrlSuffixRequest;
import bmv.pushca.binary.proxy.api.request.DecryptPageIdRequest;
import bmv.pushca.binary.proxy.api.request.DownloadProtectedBinaryRequest;
import bmv.pushca.binary.proxy.api.request.GatewayRequestHeader;
import bmv.pushca.binary.proxy.api.request.GeneratePageIdRequest;
import bmv.pushca.binary.proxy.api.request.GetPublicBinaryManifestRequest;
import bmv.pushca.binary.proxy.api.request.PublishRemoteStreamRequest;
import bmv.pushca.binary.proxy.api.request.ResolveIpRequest;
import bmv.pushca.binary.proxy.api.response.BooleanResponse;
import bmv.pushca.binary.proxy.api.response.GeoLookupResponse;
import bmv.pushca.binary.proxy.api.response.PageIdResponse;
import bmv.pushca.binary.proxy.api.response.PublishRemoteStreamResponse;
import bmv.pushca.binary.proxy.pushca.connection.model.OpenConnectionPoolRequest;
import bmv.pushca.binary.proxy.pushca.connection.model.OpenConnectionPoolResponse;
import bmv.pushca.binary.proxy.pushca.connection.model.PusherAddress;
import bmv.pushca.binary.proxy.pushca.connection.model.SimpleWsResponse;
import bmv.pushca.binary.proxy.pushca.model.BinaryManifest;
import bmv.pushca.binary.proxy.pushca.model.ClientSearchData;
import bmv.pushca.binary.proxy.pushca.model.Datagram;
import bmv.pushca.binary.proxy.pushca.model.GatewayRequestor;
import bmv.pushca.binary.proxy.pushca.model.PClient;
import bmv.pushca.binary.proxy.pushca.model.RateLimitCheckResult;
import bmv.pushca.binary.proxy.pushca.model.UploadBinaryAppeal;
import bmv.pushca.binary.proxy.pushca.model.WsGatewayRateLimitCheckData;
import java.util.HashSet;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration
@ImportRuntimeHints(ReflectionRuntimeHintsConfig.NettyRuntimeHints.class)
@RegisterReflectionForBinding(
    {
        BinaryManifest.class,
        BooleanResponse.class,
        ClientSearchData.class,
        CreatePrivateUrlSuffixRequest.class,
        Datagram.class,
        DecryptPageIdRequest.class,
        DownloadProtectedBinaryRequest.class,
        GatewayRequestHeader.class,
        GeneratePageIdRequest.class,
        GeoLookupResponse.class,
        GetPublicBinaryManifestRequest.class,
        HashSet.class,
        OpenConnectionPoolRequest.class,
        OpenConnectionPoolResponse.class,
        PageIdResponse.class,
        PClient.class,
        PublishRemoteStreamRequest.class,
        PublishRemoteStreamResponse.class,
        PusherAddress.class,
        ResolveIpRequest.class,
        SimpleWsResponse.class,
        UploadBinaryAppeal.class,
        GatewayRequestor.class,
        WsGatewayRateLimitCheckData.class,
        RateLimitCheckResult.class
    }
)
public class ReflectionRuntimeHintsConfig {

  static class NettyRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
      hints.reflection().registerType(
          TypeReference.of("io.netty.util.ReferenceCountUtil"),
          MemberCategory.INTROSPECT_DECLARED_METHODS);
      hints.reflection().registerType(
          TypeReference.of("io.netty.buffer.AbstractByteBufAllocator"),
          MemberCategory.INTROSPECT_DECLARED_METHODS);
      hints.reflection().registerType(
          TypeReference.of("io.netty.buffer.AdvancedLeakAwareByteBuf"),
          MemberCategory.INTROSPECT_DECLARED_METHODS);
    }
  }
}
