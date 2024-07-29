package bmv.pushca.binary.proxy.internal.api;

import bmv.pushca.binary.proxy.model.domain.BrowserProfile;
import java.util.UUID;

public interface BrowserProfileModule {

  BrowserProfile get(UUID id, BrowserProfile browserProfile);

  BrowserProfile init(UUID id, BrowserProfile browserProfile);
}
