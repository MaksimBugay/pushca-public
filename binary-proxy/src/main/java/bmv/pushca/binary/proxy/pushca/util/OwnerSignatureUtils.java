package bmv.pushca.binary.proxy.pushca.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.ArrayUtils;

public final class OwnerSignatureUtils {

  public static String convertHashToReadableSignature(String anyString) {
    String[] hexPairsAll;
    try {
      hexPairsAll = convertToHexPairs(calculateSpecialSHA256(anyString));
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    return convertToReadableSignature(hexPairsAll);
  }

  public static String convertToReadableSignature(String[] hexPairsAll) {
    int[] arrayOfIntegers = splitArrayAndExtractMiddle(
        hexPairsToSetOfIntegers(hexPairsAll)
    );

    List<String> words = new ArrayList<>();

    int d = 0;

    for (int i = 0; i < arrayOfIntegers.length; i++) {
      int value = arrayOfIntegers[i];
      d += value;

      if (i == 0) {
        words.add(ADJECTIVES[value % 256]);
      } else if (i == 1) {
        words.add(NOUNS[value % 256]);
        words.add("and");
      } else if (i == 2) {
        words.add(ADJECTIVES[value % 256]);
      } else if (i == 3) {
        words.add(NOUNS[value % 256]);
      } else if (i == 4) {
        words.add(VERBS[value % 256]);
        words.add(PREPOSITIONS[d % 64]);
      } else if (i == 5) {
        words.add(ADJECTIVES[value % 256]);
      } else if (i == 6) {
        words.add(NOUNS[value % 256]);
      } else if (i == 7) {
        words.add(ADVERBS[value % 256]);
      }
    }

    return String.join(" ", words);
  }

  public static String calculateSpecialSHA256(String inputString) throws NoSuchAlgorithmException {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] encodedHash = digest.digest(inputString.getBytes(StandardCharsets.UTF_8));
    try (Formatter formatter = new Formatter()) {
      for (byte b : encodedHash) {
        formatter.format("%02x", b);
      }
      return formatter.toString();
    }
  }

  public static String[] convertToHexPairs(String sha256String) {
    String[] hexPairs = new String[0];

    for (int i = 0; i < sha256String.length(); i += 2) {
      String pair = sha256String.substring(i, i + 2);
      hexPairs = ArrayUtils.addAll(hexPairs, pair);
    }

    return hexPairs;
  }


  public static int[] hexPairsToSetOfIntegers(String[] hexPairs) {
    Set<Integer> seen = new HashSet<>();
    int[] uniqueIntegers = new int[0];

    for (String hex : hexPairs) {
      int integer = Integer.parseInt(hex, 16);
      if (!seen.contains(integer)) {
        seen.add(integer);
        uniqueIntegers = ArrayUtils.addAll(uniqueIntegers, integer);
      }
    }

    return uniqueIntegers;
  }

  public static int[] splitArrayAndExtractMiddle(int[] array) {
    int blockSize = 4;
    int n = (int) Math.ceil(array.length / (blockSize * 1.0));
    int[] result = new int[n];

    for (int i = 0; i < n; i++) {
      int start = i * blockSize;
      int end = Math.min(start + blockSize, array.length);
      int[] part = new int[end - start];
      if (part.length == 0) {
        continue;
      }
      System.arraycopy(array, start, part, 0, end - start);
      int middleIndex = (int) Math.floor(part.length / 2.0);
      result[i] = part[middleIndex];
    }

    return result;
  }

  private static final String[] PREPOSITIONS =
      {
          "about", "above", "across", "after", "against", "along", "amid", "among", "around", "as",
          "at",
          "before", "behind", "below", "beneath", "beside", "besides", "between", "beyond", "but",
          "by",
          "concerning", "despite", "down", "during", "except", "for", "from", "in", "inside",
          "into", "like",
          "near", "of", "off", "on", "onto", "opposite", "out", "outside", "over", "past", "per",
          "regarding", "round", "save", "since", "than", "through", "till", "to", "toward",
          "towards", "under",
          "underneath", "unlike", "until", "up", "upon", "versus", "via", "with", "within",
          "without"
      };

  private static final String[] ADVERBS = {
      "abroad", "absolutely", "accidentally", "actually", "almost", "already", "always", "angrily",
      "anxiously",
      "anywhere", "approximately", "around", "awkwardly", "badly", "barely", "beautifully",
      "bitterly",
      "blindly", "boldly", "bravely", "briefly", "brightly", "briskly", "broadly", "busily",
      "calmly", "carefully",
      "carelessly", "certainly", "clearly", "cleverly", "closely", "coincidentally", "completely",
      "constantly",
      "correctly", "courageously", "curiously", "currently", "daily", "deliberately",
      "delightfully", "diligently",
      "directly", "doubtfully", "eagerly", "early", "easily", "effectively", "efficiently",
      "elsewhere", "enough",
      "entirely", "especially", "essentially", "even", "eventually", "exactly", "exceedingly",
      "exclusively",
      "extremely", "fairly", "faithfully", "far", "fast", "fatally", "fiercely", "finally",
      "firmly", "frequently",
      "fully", "furiously", "generally", "generously", "gently", "genuinely", "gradually",
      "greatly", "happily",
      "hard", "hardly", "hastily", "heartily", "heavily", "helpfully", "here", "highly", "honestly",
      "hopefully",
      "hourly", "however", "hungrily", "immediately", "incredibly", "indeed", "initially",
      "instantly",
      "intensely", "intentionally", "interestingly", "internally", "ironically", "joyfully", "just",
      "kindly",
      "late", "lately", "later", "lazily", "less", "likely", "literally", "little", "loudly",
      "lovingly", "low",
      "luckily", "mainly", "maybe", "meanwhile", "merely", "merrily", "mightily", "more",
      "moreover", "mostly",
      "much", "naturally", "nearly", "neatly", "necessarily", "never", "newly", "next", "nicely",
      "nightly",
      "noisily", "normally", "now", "nowhere", "obviously", "occasionally", "oddly", "often",
      "only", "openly",
      "particularly", "partly", "perhaps", "perfectly", "permanently", "personally", "physically",
      "plainly", "poorly", "possibly", "potentially", "precisely", "previously", "primarily",
      "probably",
      "promptly", "properly", "punctually", "purely", "quickly", "quietly", "quite", "randomly",
      "rarely",
      "really", "recently", "regularly", "relatively", "reluctantly", "remarkably", "repeatedly",
      "roughly", "rudely", "ruthlessly", "sadly", "safely", "seldom", "selfishly", "seriously",
      "sharply", "shortly", "significantly", "silently", "simply", "sincerely", "slightly",
      "slowly",
      "smoothly", "softly", "solely", "specially", "specifically", "spectacularly", "spontaneously",
      "steadily",
      "still", "strangely", "strictly", "strongly", "subsequently", "substantially", "successfully",
      "suddenly",
      "sufficiently", "surprisingly", "suspiciously", "swiftly", "sympathetically", "thoroughly",
      "timely",
      "together", "tomorrow", "too", "totally", "typically", "ultimately", "unconditionally",
      "undoubtedly",
      "unfortunately", "uniformly", "uniquely", "universally", "usually", "utterly", "vaguely",
      "vehemently", "verbally", "very", "viciously", "victoriously", "violently", "virtually",
      "visibly", "vividly", "voluntarily", "warmly", "weakly", "wearily", "well", "widely",
      "wildly",
      "willingly", "wisely", "wistfully", "wonderfully", "worriedly", "wrongly", "yearly",
      "youthfully", "zealously"
  };

  private static final String[] VERBS = {
      "accept", "allow", "ask", "believe", "borrow", "break", "bring", "buy", "can", "cancel",
      "change",
      "clean", "comb", "complain", "cough", "count", "cut", "dance", "draw", "drink", "drive",
      "eat",
      "explain", "fall", "fill", "find", "finish", "fit", "fix", "fly", "forget", "give", "go",
      "have",
      "hear", "hurt", "know", "learn", "leave", "listen", "live", "look", "lose", "make", "need",
      "open",
      "close", "pay", "play", "put", "rain", "read", "reply", "run", "say", "see", "sell", "send",
      "sign",
      "sing", "sit", "sleep", "smoke", "speak", "spell", "spend", "stand", "start", "stop", "study",
      "succeed", "swim", "take", "talk", "teach", "tell", "think", "translate", "travel", "try",
      "turn",
      "type", "understand", "use", "wait", "wake", "walk", "want", "watch", "work", "worry",
      "write",
      "become", "begin", "call", "come", "do", "feel", "get", "help", "keep", "kill", "let", "like",
      "hate",
      "love", "move", "show", "shut", "sing", "think", "throw", "touch", "turn", "understand",
      "win",
      "admire", "admit", "advise", "afford", "agree", "alert", "alight", "amuse", "analyze",
      "announce",
      "annoy", "answer", "apologize", "appear", "applaud", "appreciate", "approve", "argue",
      "arrange",
      "arrest", "arrive", "ask", "attach", "attack", "attempt", "attend", "attract", "avoid",
      "back", "bake",
      "balance", "ban", "bang", "bare", "bat", "bathe", "battle", "beam", "beg", "behave", "belong",
      "bleach",
      "bless", "blind", "blink", "blot", "blush", "boast", "boil", "bolt", "bomb", "book", "bore",
      "borrow",
      "buzz", "calculate", "call", "camp", "care", "carry", "carve", "cause", "challenge", "change",
      "charge",
      "chase", "cheat", "check", "cheer", "chew", "choke", "chop", "claim", "clap", "clean",
      "clear", "clip",
      "close", "coach", "coil", "collect", "colour", "comb", "command", "communicate", "compare",
      "compete",
      "contain", "continue", "copy", "correct", "cough", "count", "cover", "crack", "crash",
      "crawl", "cross",
      "deceive", "decide", "decorate", "delay", "delight", "deliver", "depend", "describe",
      "desert", "deserve",
      "destroy", "detect", "develop", "disagree", "disappear", "disapprove", "disarm", "discover",
      "dislike",
      "divide", "double", "doubt", "drag", "drain", "dream", "dress", "drip", "drop", "drown",
      "drum", "dry",
      "dust", "earn", "eat", "echo", "educate", "embarrass", "employ", "empty", "encourage", "end",
      "enjoy"
  };

  private static final String[] NOUNS = {
      "apple", "baby", "car", "dog", "elephant", "flower", "garden", "house", "island", "jewel",
      "kite",
      "lemon", "mountain", "night", "ocean", "piano", "queen", "river", "star", "tree", "umbrella",
      "volcano", "water", "xylophone", "yacht", "zebra", "air", "ball", "cat", "door", "egg",
      "frog",
      "grass", "hat", "ice", "jacket", "key", "lamp", "moon", "net", "orange", "pen", "quilt",
      "rose",
      "sun", "tiger", "unicorn", "violin", "wolf", "box", "clock", "dragon", "forest", "hill",
      "igloo",
      "jar", "kangaroo", "ladder", "mushroom", "noodle", "owl", "parrot", "robot", "snake",
      "tomato",
      "universe", "village", "whale", "x-ray", "yard", "zipper", "book", "chair", "desk",
      "envelope",
      "fish", "globe", "hammer", "ink", "juice", "kiwi", "leaf", "mirror", "nose", "oyster",
      "peach",
      "question", "rabbit", "sandwich", "turtle", "vase", "window", "xenon", "yarn", "zero", "arm",
      "boot", "ceiling", "diamond", "escalator", "feather", "guitar", "hook", "ice-cream", "jet",
      "kettle", "lion", "map", "neck", "octopus", "popcorn", "quarter", "rock", "spoon", "tablet",
      "unicorn", "vacuum", "wall", "xbox", "yo-yo", "zoo", "angel", "brain", "cloud", "devil",
      "echo",
      "flame", "ghost", "heart", "iron", "judge", "king", "leaf", "mask", "needle", "opera",
      "pearl",
      "quest", "ring", "shadow", "torch", "umbrella", "victory", "window", "exile", "youth",
      "zealot",
      "arch", "bridge", "canyon", "desert", "eclipse", "flute", "glacier", "harbor", "island",
      "jungle",
      "knight", "library", "mountain", "novel", "ocean", "prairie", "queen", "river", "sunset",
      "tree",
      "university", "valley", "waterfall", "xylophone", "yacht", "zoo", "actor", "baker", "crafter",
      "dancer", "engineer", "farmer", "guard", "hero", "inventor", "jester", "king", "lawyer",
      "miner",
      "nurse", "officer", "painter", "queen", "rider", "singer", "teacher", "umpire", "vendor",
      "writer",
      "xenophobe", "yogi", "zealot", "alchemy", "breeze", "crescent", "dawn", "ember", "forest",
      "grove",
      "hollow", "iceberg", "jewel", "kaleidoscope", "labyrinth", "meadow", "nebula", "oracle",
      "pinnacle",
      "quartz", "reef", "silhouette", "tundra", "utopia", "vortex", "wisp", "xenolith", "yarrow",
      "zenith",
      "avalanche", "blizzard", "cyclone", "delta", "equinox", "fjord", "glade", "hurricane",
      "inlet", "jetty",
      "kelp", "lagoon", "monsoon", "nadir", "oasis", "plateau", "quagmire", "ridge", "strait",
      "tide",
      "updraft", "volcano", "watershed", "service", "yardarm", "zeppelin", "crocodile", "fighter",
      "looser",
      "mosquito", "boat"
  };

  private static final String[] ADJECTIVES = {
      "able", "bad", "best", "better", "big", "black", "certain", "clear", "different", "early",
      "easy",
      "economic", "federal", "free", "full", "good", "great", "hard", "high", "human", "important",
      "international", "large", "late", "little", "local", "long", "low", "major", "military",
      "national",
      "new", "old", "only", "other", "political", "possible", "public", "real", "recent", "right",
      "small",
      "social", "special", "strong", "sure", "true", "white", "whole", "young", "active", "actual",
      "amazing",
      "angry", "annual", "available", "basic", "beautiful", "blue", "broad", "brown", "cheap",
      "clean", "cold",
      "commercial", "common", "complete", "complex", "concerned", "cool", "corporate", "critical",
      "current",
      "dark", "deep", "democratic", "different", "difficult", "due", "early", "economic",
      "effective",
      "efficient", "entire", "excellent", "exciting", "existing", "expensive", "familiar", "famous",
      "far",
      "fast", "favorite", "federal", "final", "financial", "fine", "foreign", "formal", "former",
      "free",
      "frequent", "friendly", "front", "full", "funny", "future", "general", "glad", "global",
      "good", "great",
      "green", "growing", "happy", "hard", "healthy", "heavy", "helpful", "high", "historical",
      "hot", "huge",
      "human", "ideal", "immediate", "important", "impossible", "impressive", "independent",
      "individual",
      "industrial", "inevitable", "informal", "inner", "interested", "interesting", "international",
      "joint",
      "large", "late", "leading", "legal", "likely", "limited", "local", "long", "loose", "lost",
      "low",
      "lucky", "major", "male", "massive", "medical", "military", "minor", "modern", "moral",
      "mutual",
      "naked", "national", "natural", "necessary", "negative", "new", "nice", "normal", "obvious",
      "official",
      "old", "only", "open", "operational", "original", "other", "overall", "particular",
      "personal",
      "physical", "political", "poor", "popular", "positive", "possible", "powerful", "practical",
      "present",
      "private", "probable", "quick", "quiet", "rapid", "rare", "ready", "real", "recent", "red",
      "regular",
      "related", "relative", "relevant", "reliable", "religious", "responsible", "rich", "right",
      "rough",
      "short", "sick", "significant", "similar", "simple", "single", "slight", "slow", "small",
      "smart",
      "social", "soft", "solid", "special", "specific", "stable", "standard", "strange", "strong",
      "successful",
      "sudden", "sufficient", "suitable", "surprised", "tall", "technical", "terrible", "thick",
      "thin", "tight",
      "unknown", "unlikely", "usual", "valuable", "various", "vast", "visual", "vital", "warm",
      "weak",
      "wealthy", "weird", "wide", "willing", "wise", "wonderful", "wrong", "young"
  };

  private OwnerSignatureUtils() {
  }


}
