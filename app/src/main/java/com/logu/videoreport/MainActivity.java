package com.logu.videoreport;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    static final int REQ_RECORD = 11;
    static final int REQ_PICK = 12;
    static final int REQ_RECORD_DEST = 13;
    static final int REQ_PICK_DEST = 14;

    EditText empNo, empName, unit, line, remarks;
    Spinner operation;
    TextView machine, videoLabel;
    Uri videoUri;
    Uri pendingPickedVideo;
    boolean videoStoredInDrive=false;
    boolean pendingSubmitAfterDriveSave=false;
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
        v.setText(s);
        v.setTextSize(size);
        v.setTextColor(Color.rgb(25,35,50));
        v.setPadding(0,12,0,8);
        return v;
    }

    EditText field(String hint){
        EditText e=new EditText(this);
        e.setHint(hint);
        e.setSingleLine(true);
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
        pendingPickedVideo=null;
        videoStoredInDrive=false;
        pendingSubmitAfterDriveSave=false;

        LinearLayout r=root();

        TextView h=title("VIDEO REPORT - GOOGLE DRIVE",24);
        h.setTextColor(Color.rgb(31,111,235));
        r.addView(h);

        TextView info=title(
            "Record: choose Google Drive on the Save screen, then record.\n" +
            "Choose Video: select an existing video; on Submit you can save a copy to Google Drive.",
            14
        );
        r.addView(info);

        r.addView(title("Employee Details",18));
        empNo=field("Emp No");
        empName=field("Employee Name");
        unit=field("Unit");
        line=field("Line No");
        r.addView(empNo);
        r.addView(empName);
        r.addView(unit);
        r.addView(line);

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

        Button record=button("RECORD → DRIVE");
        Button pick=button("CHOOSE VIDEO");

        row.addView(record,new LinearLayout.LayoutParams(0,-2,1));
        row.addView(pick,new LinearLayout.LayoutParams(0,-2,1));
        r.addView(row);

        record.setOnClickListener(v->chooseDriveDestinationForRecording());
        pick.setOnClickListener(v->pickVideo());

        Button submit=button("SUBMIT REPORT");
        submit.setOnClickListener(v->submitReport());
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

    String makeVideoFileName(){
        String eno=empNo==null ? "" : empNo.getText().toString().trim();
        String ename=empName==null ? "" : empName.getText().toString().trim();

        String opName="Operation";
        if(operation!=null && operation.getSelectedItemPosition()>=0){
            opName=operations[operation.getSelectedItemPosition()];
        }

        String safeEmpNo=eno.isEmpty()
            ? "NoEmp"
            : eno.replaceAll("[^a-zA-Z0-9_-]","_");

        String safeName=ename.isEmpty()
            ? "NoName"
            : ename.replaceAll("[^a-zA-Z0-9_-]","_");

        String safeOperation=opName.replaceAll("[^a-zA-Z0-9_-]","_")
                                   .replaceAll("_+","_");

        return safeEmpNo+"_"+safeName+"_"+safeOperation+".mp4";
    }

    void chooseDriveDestinationForRecording(){
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/mp4");
        i.putExtra(Intent.EXTRA_TITLE,makeVideoFileName());
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                   Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                   Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,REQ_RECORD_DEST);
    }

    void pickVideo(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("video/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                   Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,REQ_PICK);
    }

    void launchCameraToUri(Uri dest){
        try{
            Intent i=new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT,dest);
            i.setClipData(ClipData.newRawUri("video",dest));
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                       Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i,REQ_RECORD);
        }catch(Exception e){
            Toast.makeText(this,"Unable to open camera: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);

        if(req==REQ_RECORD_DEST){
            if(res==RESULT_OK && data!=null && data.getData()!=null){
                Uri dest=data.getData();
                persistUriPermission(data,dest,true);
                videoUri=dest;
                videoStoredInDrive=isLikelyGoogleDriveUri(dest);
                if(videoLabel!=null){
                    videoLabel.setText(videoStoredInDrive
                        ? "Google Drive selected - opening camera..."
                        : "Save location selected - opening camera...");
                }
                launchCameraToUri(dest);
            }
            return;
        }

        if(req==REQ_RECORD){
            if(res==RESULT_OK && videoUri!=null){
                if(videoLabel!=null){
                    videoLabel.setText(videoStoredInDrive
                        ? "Recorded directly to Google Drive ✓"
                        : "Recorded to selected save location ✓");
                }
                Toast.makeText(this,
                    videoStoredInDrive ? "Recording saved to Google Drive ✓" : "Recording saved ✓",
                    Toast.LENGTH_LONG).show();
            }else{
                videoUri=null;
                videoStoredInDrive=false;
                if(videoLabel!=null) videoLabel.setText("Recording cancelled");
            }
            return;
        }

        if(req==REQ_PICK){
            if(res==RESULT_OK && data!=null && data.getData()!=null){
                videoUri=data.getData();
                pendingPickedVideo=videoUri;
                videoStoredInDrive=isLikelyGoogleDriveUri(videoUri);
                persistUriPermission(data,videoUri,false);
                if(videoLabel!=null){
                    videoLabel.setText(videoStoredInDrive
                        ? "Google Drive video selected ✓"
                        : "Video selected - will save to Drive on Submit");
                }
            }
            return;
        }

        if(req==REQ_PICK_DEST){
            if(res==RESULT_OK && data!=null && data.getData()!=null && pendingPickedVideo!=null){
                Uri dest=data.getData();
                persistUriPermission(data,dest,true);
                copyPickedVideoThenSubmit(pendingPickedVideo,dest);
            }else{
                pendingSubmitAfterDriveSave=false;
                Toast.makeText(this,"Google Drive save cancelled",Toast.LENGTH_SHORT).show();
            }
        }
    }

    void persistUriPermission(Intent data,Uri uri,boolean write){
        int wanted=Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if(write) wanted|=Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        int flags=data.getFlags() & wanted;
        try{
            if(flags!=0) getContentResolver().takePersistableUriPermission(uri,flags);
        }catch(Exception ignored){}
    }

    boolean isLikelyGoogleDriveUri(Uri uri){
        if(uri==null) return false;
        String a=uri.getAuthority();
        String s=uri.toString().toLowerCase(Locale.ROOT);
        return (a!=null && a.toLowerCase(Locale.ROOT).contains("google")) ||
               s.contains("com.google.android.apps.docs") ||
               s.contains("google.drive") ||
               s.contains("drive");
    }

    void submitReport(){
        String eno=empNo.getText().toString().trim();
        String ename=empName.getText().toString().trim();

        if(eno.isEmpty() || ename.isEmpty()){
            Toast.makeText(this,"Enter Emp No and Employee Name",Toast.LENGTH_SHORT).show();
            return;
        }

        if(videoUri==null){
            Toast.makeText(this,"Please record or choose a video",Toast.LENGTH_SHORT).show();
            return;
        }

        if(videoStoredInDrive || isLikelyGoogleDriveUri(videoUri)){
            saveCurrentReport(videoUri,"Google Drive");
            return;
        }

        pendingPickedVideo=videoUri;
        pendingSubmitAfterDriveSave=true;

        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/mp4");
        i.putExtra(Intent.EXTRA_TITLE,makeVideoFileName());
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION |
                   Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                   Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        Toast.makeText(this,"Choose Google Drive in the Save screen",Toast.LENGTH_LONG).show();
        startActivityForResult(i,REQ_PICK_DEST);
    }

    void copyPickedVideoThenSubmit(Uri source,Uri dest){
        final ProgressDialog pd=new ProgressDialog(this);
        pd.setTitle("Saving video");
        pd.setMessage("Copying video to selected location...");
        pd.setCancelable(false);
        pd.show();

        new Thread(()->{
            try{
                copyUri(source,dest);
                videoUri=dest;
                videoStoredInDrive=isLikelyGoogleDriveUri(dest);
                runOnUiThread(()->{
                    pd.dismiss();
                    if(videoLabel!=null){
                        videoLabel.setText(videoStoredInDrive
                            ? "Video saved to Google Drive ✓"
                            : "Video saved to selected location ✓");
                    }
                    if(pendingSubmitAfterDriveSave){
                        pendingSubmitAfterDriveSave=false;
                        saveCurrentReport(dest,videoStoredInDrive ? "Google Drive" : "Selected storage");
                    }
                });
            }catch(Exception e){
                runOnUiThread(()->{
                    pd.dismiss();
                    pendingSubmitAfterDriveSave=false;
                    Toast.makeText(this,"Could not save video: "+e.getMessage(),Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    void copyUri(Uri source,Uri dest) throws Exception{
        try(InputStream in=getContentResolver().openInputStream(source);
            OutputStream out=getContentResolver().openOutputStream(dest,"w")){
            if(in==null || out==null) throw new IOException("Could not open video");
            byte[] buf=new byte[1024*64];
            int n;
            while((n=in.read(buf))>0) out.write(buf,0,n);
            out.flush();
        }
    }

    void saveCurrentReport(Uri storedVideo,String storage){
        try{
            String eno=empNo.getText().toString().trim();
            String ename=empName.getText().toString().trim();
            String eunit=unit.getText().toString().trim();
            String eline=line.getText().toString().trim();
            String eremarks=remarks.getText().toString().trim();
            int op=operation.getSelectedItemPosition();

            JSONObject o=new JSONObject();
            o.put("empNo",eno);
            o.put("name",ename);
            o.put("unit",eunit);
            o.put("line",eline);
            o.put("operation",operations[op]);
            o.put("machine",machineFor(op));
            o.put("remarks",eremarks);
            o.put("date",new SimpleDateFormat("dd-MM-yyyy HH:mm",Locale.getDefault()).format(new Date()));
            o.put("video",storedVideo.toString());
            o.put("storage",storage);

            synchronized(MainActivity.class){
                JSONArray arr=new JSONArray(prefs.getString("list","[]"));
                arr.put(o);
                prefs.edit().putString("list",arr.toString()).commit();
            }

            Toast.makeText(this,"Report saved ✓",Toast.LENGTH_LONG).show();
            showHistory();
        }catch(Exception e){
            Toast.makeText(this,"Could not save report: "+e.getMessage(),Toast.LENGTH_LONG).show();
        }
    }

    void showHistory(){
        showHistory(prefs.getString("sortMode","Date - Newest First"));
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

            final String[] sortOptions={
                "Date - Newest First",
                "Date - Oldest First",
                "Emp No - Low to High",
                "Emp No - High to Low",
                "Name - A to Z",
                "Name - Z to A",
                "Unit - Low to High",
                "Unit - High to Low",
                "Line - Low to High",
                "Line - High to Low",
                "Operation - A to Z",
                "Operation - Z to A",
                "Machine - A to Z",
                "Machine - Z to A"
            };

            r.addView(title("Sort Reports",16));

            Spinner sortSpinner=new Spinner(this);
            ArrayAdapter<String> sortAdapter=new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                sortOptions
            );
            sortSpinner.setAdapter(sortAdapter);

            int selected=0;
            for(int i=0;i<sortOptions.length;i++){
                if(sortOptions[i].equals(sortMode)){
                    selected=i;
                    break;
                }
            }

            sortSpinner.setSelection(selected,false);
            r.addView(sortSpinner);

            List<JSONObject> reports=new ArrayList<>();
            for(int i=0;i<arr.length();i++){
                reports.add(arr.getJSONObject(i));
            }

            Comparator<JSONObject> comparator;

            switch(sortMode){
                case "Date - Oldest First":
                    comparator=(a,b)->compareDate(a.optString("date"),b.optString("date"));
                    break;
                case "Emp No - Low to High":
                    comparator=(a,b)->naturalCompare(a.optString("empNo"),b.optString("empNo"));
                    break;
                case "Emp No - High to Low":
                    comparator=(a,b)->naturalCompare(b.optString("empNo"),a.optString("empNo"));
                    break;
                case "Name - A to Z":
                    comparator=(a,b)->a.optString("name").compareToIgnoreCase(b.optString("name"));
                    break;
                case "Name - Z to A":
                    comparator=(a,b)->b.optString("name").compareToIgnoreCase(a.optString("name"));
                    break;
                case "Unit - Low to High":
                    comparator=(a,b)->naturalCompare(a.optString("unit"),b.optString("unit"));
                    break;
                case "Unit - High to Low":
                    comparator=(a,b)->naturalCompare(b.optString("unit"),a.optString("unit"));
                    break;
                case "Line - Low to High":
                    comparator=(a,b)->naturalCompare(a.optString("line"),b.optString("line"));
                    break;
                case "Line - High to Low":
                    comparator=(a,b)->naturalCompare(b.optString("line"),a.optString("line"));
                    break;
                case "Operation - A to Z":
                    comparator=(a,b)->a.optString("operation").compareToIgnoreCase(b.optString("operation"));
                    break;
                case "Operation - Z to A":
                    comparator=(a,b)->b.optString("operation").compareToIgnoreCase(a.optString("operation"));
                    break;
                case "Machine - A to Z":
                    comparator=(a,b)->a.optString("machine").compareToIgnoreCase(b.optString("machine"));
                    break;
                case "Machine - Z to A":
                    comparator=(a,b)->b.optString("machine").compareToIgnoreCase(a.optString("machine"));
                    break;
                case "Date - Newest First":
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

            if(reports.size()==0){
                r.addView(title("No reports yet",16));
            }

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

                if(!o.optString("remarks").isEmpty()){
                    c.addView(title("Remarks: "+o.optString("remarks"),14));
                }

                String storage=o.optString("storage","");
                if(!storage.isEmpty()){
                    c.addView(title("Video storage: "+storage+" ✓",14));
                }

                String vu=o.optString("video");
                if(!vu.isEmpty()){
                    Button play=button("PLAY VIDEO");

                    play.setOnClickListener(v->{
                        playVideoInApp(
                            Uri.parse(vu),
                            o.optString("empNo")+" - "+o.optString("name"),
                            o.optString("operation")
                        );
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

    void playVideoInApp(Uri uri,String employee,String operationName){
        LinearLayout r=root();

        Button back=button("← BACK TO REPORT HISTORY");
        back.setOnClickListener(v->showHistory());
        r.addView(back);

        r.addView(title("VIDEO PLAYER",24));
        r.addView(title(employee,18));
        r.addView(title(operationName,15));

        TextView status=title("Loading video from Google Drive...",14);
        r.addView(status);

        VideoView vv=new VideoView(this);
        MediaController controls=new MediaController(this);
        controls.setAnchorView(vv);
        vv.setMediaController(controls);

        int h=(int)(getResources().getDisplayMetrics().density*360);
        LinearLayout.LayoutParams vp=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            h
        );
        vp.setMargins(0,12,0,12);
        r.addView(vv,vp);

        Button retry=button("RETRY VIDEO");
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(v->{
            retry.setVisibility(View.GONE);
            status.setText("Loading video...");
            try{
                vv.setVideoURI(uri);
                vv.requestFocus();
            }catch(Exception e){
                status.setText("Unable to load video");
                retry.setVisibility(View.VISIBLE);
            }
        });
        r.addView(retry);

        setContentView(scroll(r));

        vv.setOnPreparedListener(mp->{
            status.setText("Video ready ✓");
            mp.setLooping(false);
            vv.start();
        });

        vv.setOnErrorListener((mp,what,extra)->{
            status.setText("Could not read this Drive video. Check internet and Drive access.");
            retry.setVisibility(View.VISIBLE);
            Toast.makeText(
                this,
                "Could not play the Drive video. Please check Google Drive sync / internet.",
                Toast.LENGTH_LONG
            ).show();
            return true;
        });

        try{
            vv.setVideoURI(uri);
            vv.requestFocus();
        }catch(Exception e){
            status.setText("Unable to open this video.");
            retry.setVisibility(View.VISIBLE);
        }
    }

    int compareDate(String a,String b){
        try{
            SimpleDateFormat f=new SimpleDateFormat("dd-MM-yyyy HH:mm",Locale.getDefault());
            Date da=f.parse(a);
            Date db=f.parse(b);
            if(da==null || db==null) return a.compareToIgnoreCase(b);
            return da.compareTo(db);
        }catch(Exception e){
            return a.compareToIgnoreCase(b);
        }
    }

    int naturalCompare(String a,String b){
        try{
            String ad=a.replaceAll("[^0-9]","");
            String bd=b.replaceAll("[^0-9]","");

            if(!ad.isEmpty() && !bd.isEmpty()){
                long av=Long.parseLong(ad);
                long bv=Long.parseLong(bd);
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
