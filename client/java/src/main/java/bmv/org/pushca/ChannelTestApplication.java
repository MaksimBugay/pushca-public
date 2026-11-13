package bmv.org.pushca;

import static bmv.org.pushca.client.model.ClientFilter.fromClientWithoutDeviceId;
import static bmv.org.pushca.client.serialization.json.JsonUtility.fromJson;
import static bmv.org.pushca.client.serialization.json.JsonUtility.toJson;
import static bmv.org.pushca.client.utils.BmvObjectUtils.delay;

import bmv.org.pushca.client.PushcaWebSocket;
import bmv.org.pushca.client.PushcaWebSocketBuilder;
import bmv.org.pushca.client.exception.PushcaCommandException;
import bmv.org.pushca.client.model.ClientFilter;
import bmv.org.pushca.client.model.PClient;
import bmv.org.pushca.core.ChannelHistoryPage;
import bmv.org.pushca.core.ChannelMessage;
import bmv.org.pushca.core.ChannelWithInfo;
import bmv.org.pushca.core.PChannel;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * End-to-end check of close private channels: 4 accounts, the first one is the channel owner,
 * member1 account is connected from two devices.
 * Usage: ChannelTestApplication [pushcaApiUrl]
 */
@SuppressWarnings("resource")
public class ChannelTestApplication {

  // error messages reported by Pushca server for close private channels
  private static final String NO_ACCESS = "Client has no access to channel";
  private static final String OWNER_ONLY = "Only owner can add members to close private channel";
  private static final String CHANNEL_EXISTS =
      "Close private channel can be created only with a new id";
  private static final String REMOVE_OWNER_ONLY = "Only owner can remove close private channel";

  private static final Duration PROPAGATION_DELAY = Duration.ofMillis(500);
  private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(10);
  private static final int MESSAGES_PER_MEMBER = 5;

  private static final List<String> failures = new ArrayList<>();
  private static int passed;

  private record RegisterApiRequest(String organizationName, String authenticationEndpointUrl) {

  }

  private record RegisterApiResponse(String apiKey) {

  }

  private record Participant(String role, PClient client, PushcaWebSocket ws) {

    ClientFilter filter() {
      return fromClientWithoutDeviceId(client);
    }
  }

  public static void main(String[] args) {
    String pushcaApiUrl = args.length > 0 ? args[0] : "https://secure.fileshare.ovh/pushca";
    String runId = UUID.randomUUID().toString().substring(0, 8);
    String applicationId = "PUSHCA_CHANNEL_TEST_" + runId;
    String workSpaceId = "channelTest-" + runId;
    System.out.println("Channel test was started: " + pushcaApiUrl + ", run " + runId);

    Map<String, List<ChannelMessage>> received = new ConcurrentHashMap<>();
    List<Participant> participants = new ArrayList<>();
    try {
      String apiKey = registerWorkspace(pushcaApiUrl, workSpaceId);
      System.out.println("Workspace " + workSpaceId + " was registered, api key "
          + apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
      for (String role : List.of("owner", "member1", "member2", "outsider")) {
        participants.add(connect(pushcaApiUrl, apiKey, role, new PClient(
            workSpaceId,
            role + "-" + runId + "@test.ee",
            UUID.randomUUID().toString(),
            applicationId
        ), received));
      }
      // the same account as member1 signed in from another device
      PClient member1 = participants.get(1).client();
      participants.add(connect(pushcaApiUrl, apiKey, "member1-device2", new PClient(
          member1.workSpaceId,
          member1.accountId,
          UUID.randomUUID().toString(),
          member1.applicationId
      ), received));
      delay(Duration.ofSeconds(1));
      runScenario(participants.get(0), participants.get(1), participants.get(2),
          participants.get(3), received, runId);
      runMultiDeviceScenario(participants.get(0), participants.get(1), participants.get(4),
          participants.get(2), participants.get(3), received, runId);
    } catch (Exception ex) {
      fail("scenario was aborted: " + ex);
    } finally {
      participants.forEach(p -> p.ws().close());
    }

    System.out.println();
    System.out.println(
        "Channel test finished: " + passed + " passed, " + failures.size() + " failed");
    failures.forEach(f -> System.out.println("  FAILED: " + f));
    System.exit(failures.isEmpty() ? 0 : 1);
  }

  private static Participant connect(String pushcaApiUrl, String apiKey, String role,
      PClient client, Map<String, List<ChannelMessage>> received) {
    PushcaWebSocket ws = new PushcaWebSocketBuilder(pushcaApiUrl, apiKey, client, null)
        .withChannelMessageConsumer((socket, message) -> received
            .computeIfAbsent(role, k -> new CopyOnWriteArrayList<>()).add(message))
        .build();
    Participant participant = new Participant(role, client, ws);
    if (ws.getPusherInstanceId() == null) {
      ws.close();
      throw new IllegalStateException("Cannot open connection for " + role);
    }
    return participant;
  }

  /**
   * Registers a new workspace (organization) and returns its API key. Clients signing in with the
   * key are authenticated by Pushca built-in gateway sign-in endpoint
   */
  private static String registerWorkspace(String pushcaApiUrl, String workSpaceId)
      throws Exception {
    HttpRequest request = HttpRequest.newBuilder(URI.create(pushcaApiUrl + "/register-api"))
        .timeout(Duration.ofSeconds(30))
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(toJson(
            new RegisterApiRequest(workSpaceId, pushcaApiUrl + "/gateway/sign-in"))))
        .build();
    HttpResponse<String> response = HttpClient.newHttpClient()
        .send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new IllegalStateException(
          "Cannot register workspace " + workSpaceId + ": HTTP " + response.statusCode());
    }
    String apiKey = fromJson(response.body(), RegisterApiResponse.class).apiKey();
    if (apiKey == null || apiKey.isEmpty()) {
      throw new IllegalStateException("Empty api key for workspace " + workSpaceId);
    }
    return apiKey;
  }

  private static void runScenario(Participant owner, Participant member1, Participant member2,
      Participant outsider, Map<String, List<ChannelMessage>> received, String runId) {
    //---------------------------create close private channel with one member----------------------
    PChannel channel = owner.ws().createChannel(null, "closePrivateTest-" + runId, true,
        member1.filter());
    System.out.println("Close private channel was created: " + channel.id);
    delay(PROPAGATION_DELAY);
    List<ChannelWithInfo> publicInfo = owner.ws().getChannels(List.of(channel.id));
    check("channel is marked as close private",
        publicInfo.size() == 1 && Boolean.TRUE.equals(publicInfo.get(0).channel.closePrivate));
    checkMembers(owner, channel, Set.of(owner, member1), Set.of(member2, outsider));

    verifyAccess("one member", channel, List.of(owner, member1), List.of(member2, outsider),
        received);

    //---------------------------channel id cannot be reused--------------------------------------
    expectError("owner cannot create second channel with the same id", CHANNEL_EXISTS,
        () -> owner.ws().createChannel(channel.id, channel.name, true, member2.filter()));
    expectError("outsider cannot create second channel with the same id", CHANNEL_EXISTS,
        () -> outsider.ws().createChannel(channel.id, channel.name, true, outsider.filter()));
    expectError("outsider cannot reuse the id for an open channel", OWNER_ONLY,
        () -> outsider.ws().createChannel(channel.id, channel.name, outsider.filter()));
    delay(PROPAGATION_DELAY);
    checkMembers(owner, channel, Set.of(owner, member1), Set.of(member2, outsider));

    //---------------------------only owner can add the second member-----------------------------
    expectError("member cannot add members", OWNER_ONLY,
        () -> member1.ws().addMembersToChannel(channel, member2.filter()));
    expectError("non member cannot add itself", OWNER_ONLY,
        () -> member2.ws().addMembersToChannel(channel, member2.filter()));
    expectError("outsider cannot add itself", OWNER_ONLY,
        () -> outsider.ws().addMembersToChannel(channel, outsider.filter()));
    expectSuccess("owner adds second member", () -> {
      owner.ws().addMembersToChannel(channel, member2.filter());
      return null;
    });
    delay(PROPAGATION_DELAY);
    checkMembers(owner, channel, Set.of(owner, member1, member2), Set.of(outsider));

    verifyAccess("two members", channel, List.of(owner, member1, member2), List.of(outsider),
        received);

    check("outsider did not receive channel messages",
        received.getOrDefault(outsider.role(), List.of()).stream()
            .noneMatch(m -> channel.id.equals(m.channelId)));

    //---------------------------only owner can remove channel------------------------------------
    for (Participant p : List.of(member1, member2, outsider)) {
      expectError(p.role() + " cannot remove channel", REMOVE_OWNER_ONLY,
          () -> p.ws().removeChannel(channel));
    }
    delay(PROPAGATION_DELAY);
    check("channel still exists after rejected removals", channelExists(owner, channel));
    expectSuccess("owner removes channel", () -> {
      owner.ws().removeChannel(channel);
      return null;
    });
    delay(PROPAGATION_DELAY);
    check("channel is gone after owner removal", !channelExists(owner, channel));
  }

  /**
   * a member added by account id without device id has access from every device of the account
   */
  private static void runMultiDeviceScenario(Participant owner, Participant device1,
      Participant device2, Participant otherMember, Participant outsider,
      Map<String, List<ChannelMessage>> received, String runId) {
    //---------------------------owner adds member by account id only------------------------------
    PChannel channel = expectSuccess("owner creates close private channel without members",
        () -> owner.ws().createChannel(null, "closePrivateMultiDeviceTest-" + runId, true));
    if (channel == null) {
      return;
    }
    System.out.println("Close private channel was created: " + channel.id);
    PClient member = device1.client();
    expectSuccess("owner adds member by account id without device id", () -> {
      owner.ws().addMembersToChannel(channel, new ClientFilter(
          member.workSpaceId, member.accountId, null, member.applicationId));
      return null;
    });
    delay(PROPAGATION_DELAY);
    checkMembers(owner, channel, Set.of(owner, device1), Set.of(otherMember, outsider));

    // both devices of the member account send, receive and read history,
    // each device receives messages of the other one
    verifyAccess("two devices", channel, List.of(owner, device1, device2),
        List.of(otherMember, outsider), received);

    expectSuccess("owner removes multi device channel", () -> {
      owner.ws().removeChannel(channel);
      return null;
    });
  }

  private static boolean channelExists(Participant viewer, PChannel channel) {
    List<ChannelWithInfo> channels = expectSuccess(viewer.role() + " loads channel public info",
        () -> viewer.ws().getChannels(List.of(channel.id)));
    return channels != null && !channels.isEmpty();
  }

  private static void verifyAccess(String stage, PChannel channel, List<Participant> allowed,
      List<Participant> denied, Map<String, List<ChannelMessage>> received) {
    Map<Participant, List<String>> sentBy = new LinkedHashMap<>();
    for (Participant p : allowed) {
      List<String> sent = new ArrayList<>();
      for (int i = 1; i <= MESSAGES_PER_MEMBER; i++) {
        final int n = i;
        String messageId = expectSuccess(
            "[" + stage + "] " + p.role() + " sends message #" + n,
            () -> p.ws().sendMessageToChannel(channel, null,
                stage + ": hello #" + n + " from " + p.role())
        );
        if (messageId != null) {
          sent.add(messageId);
        }
      }
      sentBy.put(p, sent);
    }
    List<String> messageIds = sentBy.values().stream().flatMap(List::stream)
        .collect(Collectors.toList());
    for (Participant p : denied) {
      expectError("[" + stage + "] " + p.role() + " cannot send message", NO_ACCESS,
          () -> p.ws().sendMessageToChannel(channel, null, stage + ": hello from " + p.role()));
    }

    verifyDelivery(stage, channel, sentBy, denied, messageIds, received);

    // messages are stored to history asynchronously
    delay(PROPAGATION_DELAY);

    for (Participant p : allowed) {
      ChannelHistoryPage page = expectSuccess(
          "[" + stage + "] " + p.role() + " reads history",
          () -> p.ws().getChannelHistory(channel, null)
      );
      if (page == null) {
        continue;
      }
      List<ChannelMessage> messages = page.messages == null ? List.of() : page.messages;
      Set<String> historyIds = messages.stream().map(m -> m.messageId)
          .collect(Collectors.toSet());
      check("[" + stage + "] " + p.role() + " sees all member messages in history",
          historyIds.containsAll(messageIds));
      check("[" + stage + "] history of " + p.role() + " has no messages of non members",
          messages.stream().noneMatch(m -> m.body != null && denied.stream()
              .anyMatch(d -> m.body.endsWith("hello from " + d.role()))));
    }
    for (Participant p : denied) {
      expectError("[" + stage + "] " + p.role() + " cannot read history", NO_ACCESS,
          () -> p.ws().getChannelHistory(channel, null));
      expectError("[" + stage + "] " + p.role() + " cannot list members", NO_ACCESS,
          () -> p.ws().getChannelMembers(channel));
    }
  }

  /**
   * every member receives every message of the other members, the sender connection is excluded
   * by the server, non members receive nothing
   */
  private static void verifyDelivery(String stage, PChannel channel,
      Map<Participant, List<String>> sentBy, List<Participant> denied, List<String> messageIds,
      Map<String, List<ChannelMessage>> received) {
    Map<Participant, Set<String>> expected = new LinkedHashMap<>();
    for (Participant recipient : sentBy.keySet()) {
      expected.put(recipient, sentBy.entrySet().stream()
          .filter(e -> !e.getKey().equals(recipient))
          .flatMap(e -> e.getValue().stream())
          .collect(Collectors.toSet()));
    }
    long deadline = System.currentTimeMillis() + DELIVERY_TIMEOUT.toMillis();
    while (System.currentTimeMillis() < deadline && expected.entrySet().stream()
        .anyMatch(e -> !receivedIds(received, e.getKey(), channel).containsAll(e.getValue()))) {
      delay(Duration.ofMillis(200));
    }

    for (Map.Entry<Participant, Set<String>> e : expected.entrySet()) {
      Participant recipient = e.getKey();
      List<String> ids = receivedIdList(received, recipient, channel);
      Set<String> missing = new HashSet<>(e.getValue());
      missing.removeAll(ids);
      String name = "[" + stage + "] " + recipient.role() + " receives all "
          + e.getValue().size() + " messages of other members";
      if (missing.isEmpty()) {
        pass(name);
      } else {
        fail(name + ": " + missing.size() + " missing");
      }
      List<String> own = sentBy.get(recipient);
      check("[" + stage + "] " + recipient.role() + " does not receive own messages",
          own.stream().noneMatch(ids::contains));
      long duplicates = ids.stream().filter(e.getValue()::contains).count()
          - ids.stream().filter(e.getValue()::contains).distinct().count();
      if (duplicates > 0) {
        System.out.println("INFO [" + stage + "] " + recipient.role() + " received "
            + duplicates + " duplicate messages");
      }
    }
    for (Participant p : denied) {
      check("[" + stage + "] " + p.role() + " receives no channel messages",
          receivedIdList(received, p, channel).stream().noneMatch(messageIds::contains));
    }
  }

  private static List<String> receivedIdList(Map<String, List<ChannelMessage>> received,
      Participant participant, PChannel channel) {
    return received.getOrDefault(participant.role(), List.of()).stream()
        .filter(m -> channel.id.equals(m.channelId))
        .map(m -> m.messageId)
        .collect(Collectors.toList());
  }

  private static Set<String> receivedIds(Map<String, List<ChannelMessage>> received,
      Participant participant, PChannel channel) {
    return new HashSet<>(receivedIdList(received, participant, channel));
  }

  private static void checkMembers(Participant viewer, PChannel channel,
      Set<Participant> expected, Set<Participant> notExpected) {
    Set<String> accounts = expectSuccess(viewer.role() + " lists members",
        () -> viewer.ws().getChannelMembers(channel).stream().map(f -> f.accountId)
            .collect(Collectors.toSet()));
    if (accounts == null) {
      return;
    }
    check("members are " + roles(expected), accounts.size() == expected.size()
        && expected.stream().allMatch(p -> accounts.contains(p.client().accountId))
        && notExpected.stream().noneMatch(p -> accounts.contains(p.client().accountId)));
  }

  private static String roles(Set<Participant> participants) {
    List<String> roles = participants.stream().map(Participant::role).sorted().collect(Collectors.toCollection(ArrayList::new));
      return String.join(", ", roles);
  }

  private static <T> T expectSuccess(String name, Supplier<T> action) {
    try {
      T result = action.get();
      pass(name);
      return result;
    } catch (Exception ex) {
      fail(name + ": " + ex.getMessage());
      return null;
    }
  }

  private static void expectError(String name, String expectedError, Runnable action) {
    try {
      action.run();
      fail(name + ": command was accepted, expected error '" + expectedError + "'");
    } catch (PushcaCommandException ex) {
      if (expectedError.equals(ex.getError())) {
        pass(name);
      } else {
        fail(name + ": expected error '" + expectedError + "', got '" + ex.getError() + "'");
      }
    } catch (Exception ex) {
      fail(name + ": expected error '" + expectedError + "', got " + ex);
    }
  }

  private static void check(String name, boolean condition) {
    if (condition) {
      pass(name);
    } else {
      fail(name);
    }
  }

  private static void pass(String name) {
    passed++;
    System.out.println("PASS " + name);
  }

  private static void fail(String name) {
    failures.add(name);
    System.out.println("FAIL " + name);
  }
}
