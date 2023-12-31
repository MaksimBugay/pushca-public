package bmv.org.pushcaverifier.pushca;


import bmv.org.pushcaverifier.client.PClient;

public record ChannelMessage(PClient sender, String channelId, String messageId, Long sendTime,
                             String body) {
}
