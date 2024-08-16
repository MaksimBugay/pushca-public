package bmv.pushca.binary.proxy.pushca.connection.model;

public interface WsResponse<T> {

  T body();

  String error();
}
