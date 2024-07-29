package bmv.pushca.binary.proxy.internal.api;

import bmv.pushca.binary.proxy.model.domain.BrowserProfile;
import java.util.UUID;

public interface MonitoringModule {
  BrowserProfile registerEvent(UUID id, BrowserProfile browserProfile);
}
