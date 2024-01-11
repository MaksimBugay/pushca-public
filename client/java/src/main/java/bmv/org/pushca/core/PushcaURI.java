package bmv.org.pushca.core;

import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.client.model.UploadBinaryAppeal;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class PushcaURI {

  public static final String SCHEMA = "pshc";

  public static final String PATTERN =
      "{0}://{1}/{2}/{3}/{4}/{5}/{6}?name={7}&chunk-size={8}&chunk-total={9}";

  private final String pusherInstanceId;

  private final int numberOfChunks;
  private final UploadBinaryAppeal uploadBinaryAppeal;

  public PushcaURI(String pusherInstanceId, int numberOfChunks,
      UploadBinaryAppeal uploadBinaryAppeal) {
    this.pusherInstanceId = pusherInstanceId;
    this.numberOfChunks = numberOfChunks;
    this.uploadBinaryAppeal = uploadBinaryAppeal;
  }

  public PushcaURI(String pushcaURIStr) {
    URI uri;
    try {
      uri = new URI(pushcaURIStr);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("String cannot be converted to pushca uri", e);
    }

    if (!SCHEMA.equals(uri.getScheme())) {
      throw new IllegalArgumentException("Invalid scheme in URI, pshc is expected");
    }

    String[] pathSegments = uri.getPath().split("/");
    try {
      PClient owner = new PClient(
          pathSegments[1],
          pathSegments[2],
          pathSegments[3],
          pathSegments[4]
      );
      Map<String, String> queryParams = Arrays.stream(uri.getQuery().split("&"))
          .map(p -> p.split("="))
          .collect(Collectors.toMap(pa -> pa[0], pa -> pa[1]));
      this.pusherInstanceId = uri.getHost();
      this.numberOfChunks = Integer.parseInt(queryParams.get("chunk-total"));
      this.uploadBinaryAppeal = new UploadBinaryAppeal(
          owner,
          pathSegments[5],
          queryParams.get("name"),
          Integer.parseInt(queryParams.get("chunk-size"))
      );
    } catch (Exception ex) {
      throw new IllegalArgumentException("String cannot be converted to pushca uri", ex);
    }
  }

  public UploadBinaryAppeal getUploadBinaryAppeal() {
    return uploadBinaryAppeal;
  }

  public String getPusherInstanceId() {
    return pusherInstanceId;
  }

  public int getNumberOfChunks() {
    return numberOfChunks;
  }

  @Override
  public String toString() {
    return MessageFormat.format(PATTERN, SCHEMA,
        pusherInstanceId,
        uploadBinaryAppeal.owner.workSpaceId,
        uploadBinaryAppeal.owner.accountId,
        uploadBinaryAppeal.owner.deviceId,
        uploadBinaryAppeal.owner.applicationId,
        uploadBinaryAppeal.binaryId,
        uploadBinaryAppeal.binaryName,
        String.valueOf(uploadBinaryAppeal.chunkSize),
        String.valueOf(numberOfChunks)
    );
  }
}
