package cn.local.automerge;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.util.*;

final class Tree {
    static class Doc {
        final String name, mime; final Uri uri;
        Doc(String n,String m,Uri u){name=n;mime=m;uri=u;}
        boolean directory(){return DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);}
    }
    static Uri root(Uri tree){return DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree));}
    static List<Doc> children(Context c,Uri dir) throws Exception {
        Uri list=DocumentsContract.buildChildDocumentsUriUsingTree(dir,DocumentsContract.getDocumentId(dir));
        List<Doc> docs=new ArrayList<>();
        try(Cursor cur=c.getContentResolver().query(list,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)) {
            if(cur==null) throw new Exception("无法读取文件夹，请重新选择");
            while(cur.moveToNext()) docs.add(new Doc(cur.getString(1),cur.getString(2),DocumentsContract.buildDocumentUriUsingTree(dir,cur.getString(0))));
        }
        return docs;
    }
    static void scan(Context c,Uri dir,String relative,boolean video,List<Pairing.Item> out,Set<String> seen,int depth) throws Exception {
        if(MergeService.cancelled) throw new InterruptedException();
        if(depth>64) throw new Exception("子文件夹层级过深");
        if(!seen.add(dir.toString())) return;
        for(Doc d:children(c,dir)) {
            if(MergeService.cancelled) throw new InterruptedException();
            if(d.directory()) scan(c,d.uri,relative.isEmpty()?d.name:relative+"/"+d.name,video,out,seen,depth+1);
            else if(Pairing.supported(d.name,video)) out.add(new Pairing.Item(d.name,relative,d.uri.toString()));
        }
    }
    static Doc find(Context c,Uri dir,String name) throws Exception {
        for(Doc d:children(c,dir)) if(d.name.equals(name)) return d;
        return null;
    }
    static Uri mkdirs(Context c,Uri dir,String relative) throws Exception {
        if(relative.isEmpty()) return dir;
        for(String part:relative.split("/")) {
            Doc existing=find(c,dir,part);
            if(existing!=null) {
                if(!existing.directory()) throw new Exception("输出路径被同名文件占用："+part);
                dir=existing.uri;
            } else {
                dir=DocumentsContract.createDocument(c.getContentResolver(),dir,DocumentsContract.Document.MIME_TYPE_DIR,part);
                if(dir==null) throw new Exception("不能创建输出子文件夹");
            }
        }
        return dir;
    }
    static String display(Context c,Uri tree) {
        try(Cursor cur=c.getContentResolver().query(root(tree),new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)) {
            if(cur!=null&&cur.moveToFirst()) return cur.getString(0)+"\n"+Uri.decode(DocumentsContract.getTreeDocumentId(tree));
        } catch(Exception ignored) {}
        return "授权已失效，请重新选择";
    }
}
