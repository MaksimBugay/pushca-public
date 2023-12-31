package bmv.org.pushcaverifier.pushca;

public interface WsResponse<T> {

  T body();

  String error();
}
