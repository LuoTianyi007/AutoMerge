package cn.local.automerge;
import java.util.*;
public final class CleanupTest {
    static Pairing.Pair pair(String video,String audio){return new Pairing.Pair(new Pairing.Item(video,"",video),new Pairing.Item(audio,"",audio));}
    static Set<Pairing.Pair> verified(Pairing.Pair... pairs){return new HashSet<>(Arrays.asList(pairs));}
    static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
    public static void main(String[] args){
        Pairing.Pair a=pair("a.mov","a.wav"),b=pair("b.mov","b.wav");List<Pairing.Pair> all=Arrays.asList(a,b);
        check(CleanupPlan.create(false,false,all,verified(a,b)).isEmpty(),"disabled must preserve sources");
        check(CleanupPlan.create(true,true,all,verified(a,b)).isEmpty(),"cancelled batch must preserve sources");
        check(CleanupPlan.create(true,false,all,verified()).isEmpty(),"failed/skipped/unverified must preserve sources");
        List<Pairing.Item> clean=CleanupPlan.create(true,false,all,verified(a));
        check(clean.size()==2&&clean.stream().allMatch(i->i.uri.startsWith("a.")),"only successful verified pair eligible");
        Pairing.Pair shared=pair("another.mov","a.wav");
        clean=CleanupPlan.create(true,false,Arrays.asList(a,shared),verified(a));
        check(clean.size()==1&&clean.get(0).uri.equals("a.mov"),"audio needed by skipped or failed pair retained");
        clean=CleanupPlan.create(true,false,Arrays.asList(a,shared),verified(a,shared));
        check(clean.size()==3,"shared audio deleted only once after both outputs verified");
        System.out.println("PASS: 6 source-deletion safety checks");
    }
}
