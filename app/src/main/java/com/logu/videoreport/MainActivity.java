package com.logu.videoreport;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    EditText empNo, empName, unit, line, remarks;
    Spinner operation;
    TextView machine, videoLabel;
    Uri videoUri;
    SharedPreferences prefs;

    final String[] operations = {
      "ELASTIC READY / BACK TACK","LABEL READY / RUNSTITCH OPERATIONS","NECK TAPE CLOSE","BADGE ATTACH / PATCH PKT ATT","V-RIB TACK","SLIT ATTACH / CLOSE","VELT POCKET MAKE","ZIP ATTACH/ TOPSTITCH","SIDE POCKET ATTACH","COLLAR ATTACH / CLOSE",
      "RISE /SHOULDER ATTACH","SERGING / JOIN STRAIGHT SEAMS","SLEEVE ATTACH","SIDE / INSEAM ATTACH","ROUND SLEEVE","BOTTOM RIB / SLEEVE CUFF","NECK RIB ATTACH / ELASTIC ATTACH",
      "DECORATION/ TOPSTITCH","NECK BINDING","SLEEVE HEM","BOTTOM HEM","WAIST TOPSTITCH","BELT ELASTIC ATTACH",
      "BT/BH/BU","HEAT TRANSFER","FLAT SEAMER","TOP ELASTIC","MULTI NEEDLE/ NET FOLDING"
    };

    @Override public void onCreate(Bundle b){ super.onCreate(b); prefs=getSharedPreferences("reports",0); showForm(); }

    TextView title(String s,int size){ TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.rgb(25,35,50));v.setPadding(0,12,0,8);return v; }
    EditText field(String hint){ EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);return e; }
    Button button(String t){ Button b=new Button(this);b.setText(t);return b; }
    LinearLayout root(){ LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(30,24,30,30);return r; }
    ScrollView scroll(LinearLayout r){ ScrollView s=new ScrollView(this);s.addView(r);return s; }

    void showForm(){
      LinearLayout r=root();
      TextView h=title("VIDEO REPORT",24); h.setTextColor(Color.rgb(31,111,235)); r.addView(h);
      r.addView(title("Employee Details",18));
      empNo=field("Emp No"); empName=field("Employee Name"); unit=field("Unit"); line=field("Line No");
      r.addView(empNo);r.addView(empName);r.addView(unit);r.addView(line);
      r.addView(title("Operation Details",18));
      operation=new Spinner(this); ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,operations);operation.setAdapter(a);r.addView(operation);
      machine=title("Machine: SINGLE NEEDLE LOCK STITCH",16);r.addView(machine);
      operation.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){machine.setText("Machine: "+machineFor(pos));}public void onNothingSelected(android.widget.AdapterView<?> p){}});
      remarks=field("Remarks"); remarks.setSingleLine(false); remarks.setMinLines(3);r.addView(remarks);
      r.addView(title("Video",18)); videoLabel=title("No video selected",14);r.addView(videoLabel);
      LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
      Button record=button("Record Video");Button pick=button("Choose Video"); row.addView(record,new LinearLayout.LayoutParams(0,-2,1));row.addView(pick,new LinearLayout.LayoutParams(0,-2,1));r.addView(row);
      record.setOnClickListener(v->{Intent i=new Intent(MediaStore.ACTION_VIDEO_CAPTURE);startActivityForResult(i,11);});
      pick.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("video/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,12);});
      Button submit=button("SUBMIT REPORT");submit.setOnClickListener(v->saveReport());r.addView(submit);
      Button history=button("VIEW REPORT HISTORY");history.setOnClickListener(v->showHistory());r.addView(history);
      setContentView(scroll(r));
    }

    String machineFor(int pos){ if(pos<=9)return "SINGLE NEEDLE LOCK STITCH"; if(pos<=16)return "OVER LOCK"; if(pos<=22)return "FLAT LOCK"; return "SPECIAL MACHINE"; }

    @Override protected void onActivityResult(int req,int res,Intent data){super.onActivityResult(req,res,data);if(res==RESULT_OK&&data!=null&&data.getData()!=null){videoUri=data.getData(); if(req==12){try{getContentResolver().takePersistableUriPermission(videoUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}} videoLabel.setText("Video selected ✓");}}

    void saveReport(){
      if(empNo.getText().toString().trim().isEmpty()||empName.getText().toString().trim().isEmpty()){Toast.makeText(this,"Enter Emp No and Name",Toast.LENGTH_SHORT).show();return;}
      try{
        JSONArray arr=new JSONArray(prefs.getString("list","[]")); JSONObject o=new JSONObject();
        o.put("empNo",empNo.getText().toString().trim());o.put("name",empName.getText().toString().trim());o.put("unit",unit.getText().toString().trim());o.put("line",line.getText().toString().trim());o.put("operation",operations[operation.getSelectedItemPosition()]);o.put("machine",machineFor(operation.getSelectedItemPosition()));o.put("remarks",remarks.getText().toString().trim());o.put("date",new SimpleDateFormat("dd-MM-yyyy HH:mm",Locale.getDefault()).format(new Date()));o.put("video",videoUri==null?"":videoUri.toString());arr.put(o);prefs.edit().putString("list",arr.toString()).apply();Toast.makeText(this,"Report submitted",Toast.LENGTH_SHORT).show();showForm();
      }catch(Exception e){Toast.makeText(this,"Could not save report",Toast.LENGTH_SHORT).show();}
    }

    void showHistory(){
      LinearLayout r=root();Button back=button("← ADD REPORT");back.setOnClickListener(v->showForm());r.addView(back);r.addView(title("REPORT HISTORY",24));
      try{JSONArray arr=new JSONArray(prefs.getString("list","[]")); if(arr.length()==0)r.addView(title("No reports yet",16));
        for(int i=arr.length()-1;i>=0;i--){JSONObject o=arr.getJSONObject(i);LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(18,18,18,18);GradientDrawableCompat.bg(c);
          c.addView(title(o.optString("empNo")+" - "+o.optString("name"),18));c.addView(title(o.optString("date"),13));c.addView(title("Unit: "+o.optString("unit")+"   Line: "+o.optString("line"),14));c.addView(title(o.optString("operation"),15));c.addView(title("Machine: "+o.optString("machine"),14));if(!o.optString("remarks").isEmpty())c.addView(title("Remarks: "+o.optString("remarks"),14));
          String vu=o.optString("video"); if(!vu.isEmpty()){Button play=button("PLAY VIDEO");play.setOnClickListener(v->{try{Intent x=new Intent(Intent.ACTION_VIEW);x.setDataAndType(Uri.parse(vu),"video/*");x.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(x);}catch(Exception ex){Toast.makeText(this,"Video unavailable",Toast.LENGTH_SHORT).show();}});c.addView(play);} LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,18);r.addView(c,lp);
        }
      }catch(Exception e){r.addView(title("Unable to load history",16));}
      setContentView(scroll(r));
    }

    public static class GradientDrawableCompat{ static void bg(View v){ android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(Color.rgb(245,247,250));g.setCornerRadius(20);g.setStroke(1,Color.rgb(220,225,232));v.setBackground(g);} }
}
