package com.logu.videoreport;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    static final int REQ_RECORD = 11, REQ_PICK = 12, REQ_FOLDER = 13;

    EditText empNo, empName, unit, line, remarks;
    Spinner operation;
    TextView machine, videoLabel, driveLabel;
    Uri videoUri, cameraUri;
    boolean videoStoredInDrive=false;
    SharedPreferences prefs;

    final String[] operations = {
      "ELASTIC READY / BACK TACK","LABEL READY / RUNSTITCH OPERATIONS","NECK TAPE CLOSE","BADGE ATTACH / PATCH PKT ATT","V-RIB TACK","SLIT ATTACH / CLOSE","VELT POCKET MAKE","ZIP ATTACH/ TOPSTITCH","SIDE POCKET ATTACH","COLLAR ATTACH / CLOSE",
      "RISE /SHOULDER ATTACH","SERGING / JOIN STRAIGHT SEAMS","SLEEVE ATTACH","SIDE / INSEAM ATTACH","ROUND SLEEVE","BOTTOM RIB / SLEEVE CUFF","NECK RIB ATTACH / ELASTIC ATTACH",
      "DECORATION/ TOPSTITCH","NECK BINDING","SLEEVE HEM","BOTTOM HEM","WAIST TOPSTITCH","BELT ELASTIC ATTACH",
      "BT/BH/BU","HEAT TRANSFER","FLAT SEAMER","TOP ELASTIC","MULTI NEEDLE/ NET FOLDING"
    };

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        prefs=getSharedPreferences("reports",0);
        showForm();
    }

    TextView title(String s,int size){
        TextView v=new TextView(this);
        v.setText(s); v.setTextSize(size); v.setTextColor(Color.rgb(25,35,50));
        v.setPadding(0,12,0,8);
        return v;
    }

    EditText field(String hint){
        EditText e=new EditText(this);
        e.setHint(hint); e.setSingleLine(true);
        return e;
    }

    Button button(String t){
        Button b=new Button(this);
        b.setText(t);
        return b;
    }

    LinearLayout root(){
        LinearLayout r=new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        r.setPadding(30,24,30,30);
        return r;
    }

    ScrollView scroll(LinearLayout r){
        ScrollView s=new ScrollView(this);
        s.addView(r);
        return s;
    }

    void showForm(){
        videoUri=null;
        cameraUri=null;
        videoStoredInDrive=false;

        LinearLayout r=root();
        TextView h=title("VIDEO REPORT - DRIVE VERSION",24);
        h.setTextColor(Color.rgb(31,111,235));
        r.addView(h);

        r.addView(title("Google Drive Storage",18));
        driveLabel=title(prefs.contains("driveFolder") ? "Google Drive folder selected ✓" : "No Google Drive folder selected",14);
        r.addView(driveLabel);
        Button chooseFolder=button("SELECT GOOGLE DRIVE FOLDER");
        chooseFolder.setOnClickListener(v->selectDriveFolder());
        r.addView(chooseFolder);

        r.addView(title("Employee Details",18));
        empNo=field("Emp No");
        empName=field("Employee Name");
        unit=field("Unit");
        line=field("Line No");
        r.addView(empNo); r.addView(empName); r.addView(unit); r.addView(line);

        r.addView(title("Operation Details",18));
        operation=new Spinner(this);
        ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,operations);
        operation.setAdapter(a);
        r.addView(operation);

        machine=title("Machine: SINGLE NEEDLE LOCK STITCH",16);
        r.addView(machine);
        operation.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                machine.setText("Machine: "+machineFor(pos));
            }
            public void onNothingSelected(android.widget.AdapterView<?> p){}
        });

        remarks=field("Remarks");
        remarks.setSingleLine(false);
        remarks.setMinLines(3);
        r.addView(remarks);

        r.addView(title("Video",18));
        videoLabel=title("No video selected",14);
        r.addView(videoLabel);

        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button record=button("RECORD VIDEO");
        Button pick=button("CHOOSE VIDEO");
        row.addView(record,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(pick,new LinearLayout.LayoutParams(0,-2,1));
        r.addView(row);

        record.setOnClickListener(v->recordVideo());
        pick.setOnClickListener(v->pickVideo());

        Button submit=button("SUBMIT REPORT");
        submit.setOnClickListener(v->uploadAndSave());
        r.addView(submit);

        int count=0;
        try{ count=new JSONArray(prefs.getString("list","[]")).length(); }catch(Exception ignored){}
        Button history=button("VIEW REPORT HISTORY ("+count+")");
        history.setOnClickListener(v->showHistory());
        r.addView(history);

        setContentView(scroll(r));
    }

    String machineFor(int pos){
        if(pos<=9) return "SINGLE NEEDLE LOCK STITCH";
        if(pos<=16) return "OVER LOCK";
        if(pos<=22) return "FLAT LOCK";
        return "SPECIAL MACHINE";
    }

    void selectDriveFolder(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                   Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                   Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION |
                   Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(i,REQ_FOLDER);
    }

    void pickVideo(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("video/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,REQ_PICK);
    }

    void recordVideo(){
        String folder=prefs.getString("driveFolder","");
        if(folder.isEmpty()){
            Toast.makeText(this,"First select your Google Drive folder",Toast.LENGTH_LONG).show();
            selectDriveFolder();
            return;
        }
        try{
            ContentValues values=new ContentValues();
            values.put(MediaStore.Video.Media.DISPLAY_NAME,"VideoReport_"+System.currentTimeMillis()+".mp4");
            values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
            if(Build.VERSION.SDK_INT>=29) values.put(MediaStore.Video.Media.RELATIVE_PATH,"Movies/VideoReport");
            cameraUri=getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);
            if(cameraUri==null) throw new Exception("Could not create video file");

            Intent i=new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i,REQ_RECORD);
        }catch(Exception e){
            Toast.makeText(this,"Unable to open camera: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);

        if(req==REQ_FOLDER && res==RESULT_OK && data!=null && data.getData()!=null){
            Uri tree=data.getData();
            int flags=data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try{ getContentResolver().takePersistableUriPermission(tree,flags); }catch(Exception ignored){}
            prefs.edit().putString("driveFolder",tree.toString()).apply();
            if(driveLabel!=null) driveLabel.setText("Google Drive folder selected ✓");
            Toast.makeText(this,"Drive folder saved",Toast.LENGTH_SHORT).show();
            return;
        }

        if(req==REQ_PICK && res==RESULT_OK && data!=null && data.getData()!=null){
            videoUri=data.getData();
            videoStoredInDrive=false;
            try{ getContentResolver().takePersistableUriPermission(videoUri,Intent.FLAG_GRANT_READ_URI_PERMISSION); }catch(Exception ignored){}
            if(videoLabel!=null) videoLabel.setText("Video selected - will upload on Submit");
            return;
        }

        if(req==REQ_RECORD){
            if(res==RESULT_OK && cameraUri!=null){
                uploadRecordedVideoToDrive(cameraUri);
            }else if(cameraUri!=null){
                try{ getContentResolver().delete(cameraUri,null,null); }catch(Exception ignored){}
                cameraUri=null;
            }
        }
    }

    void uploadRecordedVideoToDrive(Uri localVideo){
        final String folder=prefs.getString("driveFolder","");
        if(folder.isEmpty()){
            Toast.makeText(this,"Drive folder not selected",Toast.LENGTH_LONG).show();
            return;
        }

        final ProgressDialog pd=new ProgressDialog(this);
        pd.setTitle("Saving recording to Google Drive");
        pd.setMessage("Uploading recorded video...");
        pd.setCancelable(false);
        pd.show();

        new Thread(()->{
            try{
                String eno=empNo==null ? "" : empNo.getText().toString().trim();
                String ename=empName==null ? "" : empName.getText().toString().trim();
                String prefix=(eno.isEmpty() ? "Recorded" : eno);
                if(!ename.isEmpty()) prefix+="_"+ename.replaceAll("[^a-zA-Z0-9_-]","_");
                String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.getDefault()).format(new Date());
                Uri driveUri=copyVideoToDrive(localVideo,Uri.parse(folder),prefix+"_"+stamp+".mp4");

                try{ getContentResolver().delete(localVideo,null,null); }catch(Exception ignored){}

                videoUri=driveUri;
                videoStoredInDrive=true;
                cameraUri=null;

                runOnUiThread(()->{
                    pd.dismiss();
                    if(videoLabel!=null) videoLabel.setText("Recorded video saved to Google Drive ✓");
                    Toast.makeText(this,"Recording saved to Google Drive ✓",Toast.LENGTH_LONG).show();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    pd.dismiss();
                    videoUri=localVideo;
                    videoStoredInDrive=false;
                    if(videoLabel!=null) videoLabel.setText("Drive upload failed - local recording kept");
                    Toast.makeText(this,"Drive upload failed: "+e.getMessage(),Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    void uploadAndSave(){
        final String eno=empNo.getText().toString().trim();
        final String ename=empName.getText().toString().trim();
        final String eunit=unit.getText().toString().trim();
        final String eline=line.getText().toString().trim();
        final String eremarks=remarks.getText().toString().trim();
        final int op=operation.getSelectedItemPosition();

        if(eno.isEmpty() || ename.isEmpty()){
            Toast.makeText(this,"Enter Emp No and Employee Name",Toast.LENGTH_SHORT).show();
            return;
        }
        if(videoUri==null){
            Toast.makeText(this,"Please record or choose a video",Toast.LENGTH_SHORT).show();
            return;
        }

        final String folder=prefs.getString("driveFolder","");
        if(folder.isEmpty()){
            Toast.makeText(this,"First select your Google Drive folder",Toast.LENGTH_LONG).show();
            selectDriveFolder();
            return;
        }

        if(videoStoredInDrive){
            try{
                saveReportRecord(eno,ename,eunit,eline,eremarks,op,videoUri);
                Toast.makeText(this,"Report saved ✓",Toast.LENGTH_LONG).show();
                showHistory();
            }catch(Exception e){
                Toast.makeText(this,"Could not save report: "+e.getMessage(),Toast.LENGTH_LONG).show();
            }
            return;
        }

        final Uri src=videoUri;
        final ProgressDialog pd=new ProgressDialog(this);
        pd.setTitle("Uploading to Google Drive");
        pd.setMessage("Uploading selected video...");
        pd.setCancelable(false);
        pd.show();

        new Thread(()->{
            try{
                String safeName=ename.replaceAll("[^a-zA-Z0-9_-]","_");
                String stamp=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.getDefault()).format(new Date());
                String fileName=eno+"_"+safeName+"_"+stamp+".mp4";
                Uri driveUri=copyVideoToDrive(src,Uri.parse(folder),fileName);
                saveReportRecord(eno,ename,eunit,eline,eremarks,op,driveUri);

                runOnUiThread(()->{
                    pd.dismiss();
                    Toast.makeText(this,"Video uploaded & report saved ✓",Toast.LENGTH_LONG).show();
                    showHistory();
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    pd.dismiss();
                    Toast.makeText(this,"Upload failed: "+e.getMessage(),Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    void saveReportRecord(String eno,String ename,String eunit,String eline,String eremarks,int op,Uri driveUri) throws Exception{
        JSONObject o=new JSONObject();
        o.put("empNo",eno);
        o.put("name",ename);
        o.put("unit",eunit);
        o.put("line",eline);
        o.put("operation",operations[op]);
        o.put("machine",machineFor(op));
        o.put("remarks",eremarks);
        o.put("date",new SimpleDateFormat("dd-MM-yyyy HH:mm",Locale.getDefault()).format(new Date()));
        o.put("video",driveUri.toString());
        o.put("storage","Google Drive");

        synchronized(MainActivity.class){
            JSONArray arr=new JSONArray(prefs.getString("list","[]"));
            arr.put(o);
            prefs.edit().putString("list",arr.toString()).commit();
        }
    }

    Uri copyVideoToDrive(Uri source,Uri treeUri,String fileName) throws Exception{
        String treeId=DocumentsContract.getTreeDocumentId(treeUri);
        Uri parent=DocumentsContract.buildDocumentUriUsingTree(treeUri,treeId);
        String mime=getContentResolver().getType(source);
        if(mime==null || !mime.startsWith("video/")) mime="video/mp4";

        Uri dest=DocumentsContract.createDocument(getContentResolver(),parent,mime,fileName);
        if(dest==null) throw new IOException("Could not create file in selected Drive folder");

        try(InputStream in=getContentResolver().openInputStream(source);
            OutputStream out=getContentResolver().openOutputStream(dest,"w")){
            if(in==null || out==null) throw new IOException("Could not open video stream");
            byte[] buf=new byte[1024*64];
            int n;
            while((n=in.read(buf))>0) out.write(buf,0,n);
            out.flush();
        }catch(Exception e){
            try{ DocumentsContract.deleteDocument(getContentResolver(),dest); }catch(Exception ignored){}
            throw e;
        }
        return dest;
    }

    void showHistory(){
        showHistory(prefs.getString("sortMode","Newest"));
    }

    void showHistory(String sortMode){
        LinearLayout r=root();
        Button back=button("← ADD NEW REPORT");
        back.setOnClickListener(v->showForm());
        r.addView(back);

        r.addView(title("REPORT HISTORY",24));

        try{
            JSONArray arr=new JSONArray(prefs.getString("list","[]"));
            r.addView(title("Total reports: "+arr.length(),15));

            final String[] sortOptions={"Newest","Oldest","Emp No","Name","Unit","Line","Operation","Machine"};
            r.addView(title("Sort Reports",16));
            Spinner sortSpinner=new Spinner(this);
            ArrayAdapter<String> sortAdapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,sortOptions);
            sortSpinner.setAdapter(sortAdapter);

            int selected=0;
            for(int i=0;i<sortOptions.length;i++){
                if(sortOptions[i].equals(sortMode)){ selected=i; break; }
            }
            sortSpinner.setSelection(selected,false);
            r.addView(sortSpinner);

            List<JSONObject> reports=new ArrayList<>();
            for(int i=0;i<arr.length();i++) reports.add(arr.getJSONObject(i));

            Comparator<JSONObject> comparator;
            switch(sortMode){
                case "Oldest":
                    comparator=(a,b)->compareDate(a.optString("date"),b.optString("date"));
                    break;
                case "Emp No":
                    comparator=(a,b)->naturalCompare(a.optString("empNo"),b.optString("empNo"));
                    break;
                case "Name":
                    comparator=(a,b)->a.optString("name").compareToIgnoreCase(b.optString("name"));
                    break;
                case "Unit":
                    comparator=(a,b)->naturalCompare(a.optString("unit"),b.optString("unit"));
                    break;
                case "Line":
                    comparator=(a,b)->naturalCompare(a.optString("line"),b.optString("line"));
                    break;
                case "Operation":
                    comparator=(a,b)->a.optString("operation").compareToIgnoreCase(b.optString("operation"));
                    break;
                case "Machine":
                    comparator=(a,b)->a.optString("machine").compareToIgnoreCase(b.optString("machine"));
                    break;
                case "Newest":
                default:
                    comparator=(a,b)->compareDate(b.optString("date"),a.optString("date"));
                    break;
            }
            Collections.sort(reports,comparator);

            sortSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
                public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){
                    String chosen=sortOptions[pos];
                    if(!chosen.equals(sortMode)){
                        prefs.edit().putString("sortMode",chosen).apply();
                        showHistory(chosen);
                    }
                }
                public void onNothingSelected(android.widget.AdapterView<?> p){}
            });

            if(reports.size()==0) r.addView(title("No reports yet",16));

            for(JSONObject o:reports){
                LinearLayout c=new LinearLayout(this);
                c.setOrientation(LinearLayout.VERTICAL);
                c.setPadding(18,18,18,18);
                GradientDrawableCompat.bg(c);

                c.addView(title(o.optString("empNo")+" - "+o.optString("name"),18));
                c.addView(title(o.optString("date"),13));
                c.addView(title("Unit: "+o.optString("unit")+"   Line: "+o.optString("line"),14));
                c.addView(title("Operation: "+o.optString("operation"),15));
                c.addView(title("Machine: "+o.optString("machine"),14));
                if(!o.optString("remarks").isEmpty()) c.addView(title("Remarks: "+o.optString("remarks"),14));

                String storage=o.optString("storage","");
                if(!storage.isEmpty()) c.addView(title("Video storage: "+storage+" ✓",14));

                String vu=o.optString("video");
                if(!vu.isEmpty()){
                    Button play=button(storage.equals("Google Drive") ? "OPEN DRIVE VIDEO" : "PLAY VIDEO");
                    play.setOnClickListener(v->{
                        try{
                            Intent x=new Intent(Intent.ACTION_VIEW);
                            x.setDataAndType(Uri.parse(vu),"video/*");
                            x.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            startActivity(x);
                        }catch(Exception ex){
                            Toast.makeText(this,"Video unavailable. Check Drive app / internet.",Toast.LENGTH_LONG).show();
                        }
                    });
                    c.addView(play);
                }

                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                lp.setMargins(0,0,0,18);
                r.addView(c,lp);
            }
        }catch(Exception e){
            r.addView(title("Unable to load report history: "+e.getMessage(),16));
        }

        setContentView(scroll(r));
    }

    int compareDate(String a,String b){
        try{
            SimpleDateFormat f=new SimpleDateFormat("dd-MM-yyyy HH:mm",Locale.getDefault());
            Date da=f.parse(a), db=f.parse(b);
            if(da==null||db==null) return a.compareToIgnoreCase(b);
            return da.compareTo(db);
        }catch(Exception e){
            return a.compareToIgnoreCase(b);
        }
    }

    int naturalCompare(String a,String b){
        try{
            String ad=a.replaceAll("[^0-9]","");
            String bd=b.replaceAll("[^0-9]","");
            if(!ad.isEmpty()&&!bd.isEmpty()){
                long av=Long.parseLong(ad), bv=Long.parseLong(bd);
                if(av!=bv) return Long.compare(av,bv);
            }
        }catch(Exception ignored){}
        return a.compareToIgnoreCase(b);
    }

    public static class GradientDrawableCompat{
        static void bg(View v){
            android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();
            g.setColor(Color.rgb(245,247,250));
            g.setCornerRadius(20);
            g.setStroke(1,Color.rgb(220,225,232));
            v.setBackground(g);
        }
    }
}
