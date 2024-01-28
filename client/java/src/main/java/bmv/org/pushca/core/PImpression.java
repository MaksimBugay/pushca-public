package bmv.org.pushca.core;

public class PImpression {
  public String resourceId;
  public ResourceType resourceType;
  public int code;

  public PImpression() {
  }

  public PImpression(String resourceId, ResourceType resourceType, int code) {
    this.resourceId = resourceId;
    this.resourceType = resourceType;
    this.code = code;
  }
}
