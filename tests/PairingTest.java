package cn.local.automerge;
import java.util.*;
public class PairingTest {
    static Pairing.Item item(String n,String p){return new Pairing.Item(n,p,p+"/"+n);}
    static List<Pairing.Item> list(Pairing.Item... a){return new ArrayList<>(Arrays.asList(a));}
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
    public static void main(String[] args){
        Pairing.Result r=Pairing.match(list(item("clip.mov","2026-10-01")),list(item("clip.wav","2026-10-01")));
        check(r.pairs.size()==1,"same date and name");
        r=Pairing.match(list(item("clip.mov","day1")),list(item("clip.wav","day1"),item("clip.wav","day2")));
        check(r.pairs.size()==1&&r.pairs.get(0).audio.relative.equals("day1"),"prefer exact subfolder");
        r=Pairing.match(list(item("CLIP.MOV","day1")),list(item("clip.WAV","other")));
        check(r.pairs.size()==1,"unique basename fallback and case folding");
        r=Pairing.match(list(item("clip.mov","day1")),list(item("clip.wav","x"),item("clip.flac","y")));
        check(r.pairs.isEmpty()&&r.warnings.size()==1,"ambiguous names must not merge");
        r=Pairing.match(list(item("clip.mov","day1")),list(item("clip.wav","day1"),item("clip.flac","day1")));
        check(r.pairs.isEmpty(),"ambiguous same-subfolder audio must not merge");
        r=Pairing.match(list(item("clip.mov","day1")),list(item("different.wav","day1")));
        check(r.pairs.isEmpty()&&r.warnings.size()==1,"missing audio");
        check(!Pairing.supported("clip__merged.mkv",true),"ignore generated output");
        check(!Pairing.supported("clip.merging-123.mkv",true),"ignore partial output");
        check(Pairing.supported("clip.mov",true)&&Pairing.supported("clip.wav",false),"MOV WAV supported");
        check(!Pairing.supported(".pending-1791435970-clip.mov",true),"ignore unfinished recording");
        check(!Pairing.supported(".trashed-1791435970-clip.wav",false),"ignore trashed recording");
        System.out.println("PASS: 11 pairing and input-filter checks");
    }
}
