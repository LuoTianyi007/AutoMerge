package cn.local.automerge;
import java.util.*;

/** A source shared with any unverified/skipped/failed pair must be retained. */
final class CleanupPlan {
    static List<Pairing.Item> create(boolean enabled,boolean cancelled,List<Pairing.Pair> all,Set<Pairing.Pair> verified) {
        if(!enabled||cancelled)return new ArrayList<>();
        Set<String> retain=new HashSet<>();
        Map<String,Pairing.Item> remove=new LinkedHashMap<>();
        for(Pairing.Pair pair:all) {
            if(verified.contains(pair)) {
                remove.put(pair.video.uri,pair.video);remove.put(pair.audio.uri,pair.audio);
            } else {
                retain.add(pair.video.uri);retain.add(pair.audio.uri);
            }
        }
        for(String uri:retain)remove.remove(uri);
        return new ArrayList<>(remove.values());
    }
}
