package cn.local.automerge;

import java.util.*;

/** Matching does not guess when multiple recordings share a name. */
public final class Pairing {
    public static final class Item {
        public final String name, relative, uri;
        public Item(String name, String relative, String uri) { this.name=name; this.relative=relative; this.uri=uri; }
        public String stem() { int i=name.lastIndexOf('.'); return i<0?name:name.substring(0,i); }
        public String key() { return (relative+"/"+stem()).toLowerCase(Locale.ROOT); }
    }
    public static final class Pair {
        public final Item video, audio;
        public Pair(Item v, Item a) { video=v; audio=a; }
    }
    public static final class Result {
        public final List<Pair> pairs=new ArrayList<>();
        public final List<String> warnings=new ArrayList<>();
    }
    public static Result match(List<Item> videos, List<Item> audios) {
        Map<String,List<Item>> byPath=new HashMap<>(), byName=new HashMap<>();
        for(Item a:audios) {
            byPath.computeIfAbsent(a.key(), k->new ArrayList<>()).add(a);
            byName.computeIfAbsent(a.stem().toLowerCase(Locale.ROOT), k->new ArrayList<>()).add(a);
        }
        videos.sort(Comparator.comparing(Item::key).thenComparing(i->i.name));
        Result result=new Result();
        for(Item v:videos) {
            List<Item> candidates=byPath.get(v.key());
            if(candidates==null) candidates=byName.get(v.stem().toLowerCase(Locale.ROOT));
            if(candidates==null) result.warnings.add("缺少音频："+v.relative+"/"+v.name);
            else if(candidates.size()!=1) result.warnings.add("音频重名，跳过："+v.relative+"/"+v.name);
            else result.pairs.add(new Pair(v,candidates.get(0)));
        }
        return result;
    }
    public static boolean supported(String name, boolean video) {
        String s=name.toLowerCase(Locale.ROOT);
        if(s.startsWith(".pending-") || s.startsWith(".trashed-")) return false;
        if(s.endsWith("__merged.mkv") || s.contains(".merging-")) return false;
        int p=s.lastIndexOf('.'); if(p<0) return false;
        String ext=s.substring(p+1);
        return (video?Arrays.asList("mov","mp4","mkv","m4v","avi","webm","mts","m2ts"):Arrays.asList("wav","flac","m4a","mp3","aac","ogg","opus","aiff","aif")).contains(ext);
    }
}
