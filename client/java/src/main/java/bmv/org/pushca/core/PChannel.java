package bmv.org.pushca.core;

import bmv.org.pushca.client.utils.BmvObjectUtils;

import java.util.Objects;

public class PChannel {

    public String id;

    public String name;

    /**
     * true for a close private channel, the flag is assigned on creation and cannot be changed
     */
    public Boolean closePrivate;

    public PChannel() {
    }

    public PChannel(String id, String name) {
        this(id, name, null);
    }

    public PChannel(String id, String name, Boolean closePrivate) {
        this.id = id;
        this.name = name;
        this.closePrivate = closePrivate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PChannel pChannel)) {
            return false;
        }
        return Objects.equals(id, pChannel.id);
    }

    @Override
    public int hashCode() {
        return BmvObjectUtils.calculateStringHashCode(id);
    }
}
