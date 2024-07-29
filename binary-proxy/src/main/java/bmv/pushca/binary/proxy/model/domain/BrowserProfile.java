package bmv.pushca.binary.proxy.model.domain;

import java.util.UUID;

public record BrowserProfile(Long id, UUID uuid, String name, String metaData, Boolean persistent) {

}
