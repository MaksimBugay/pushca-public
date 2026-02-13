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

import java.util.HashSet;

import bmv.pushca.binary.proxy.pushca.model.WsGatewayRateLimitCheckData;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.context.annotation.Configuration;

@Configuration
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
}
