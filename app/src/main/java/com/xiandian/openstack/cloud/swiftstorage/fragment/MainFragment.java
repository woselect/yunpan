package com.xiandian.openstack.cloud.swiftstorage.fragment;


import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SyncStatusObserver;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.support.v4.content.res.TypedArrayUtils;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.core.util.InternCache;
import com.fasterxml.jackson.databind.ser.std.StdDelegatingSerializer;
import com.woorea.openstack.swift.Swift;
import com.woorea.openstack.swift.api.ContainerResource;
import com.woorea.openstack.swift.model.Object;
import com.woorea.openstack.swift.model.ObjectDownload;
import com.woorea.openstack.swift.model.ObjectForUpload;
import com.woorea.openstack.swift.model.Objects;
import com.xiandian.openstack.cloud.swiftstorage.AppState;
import com.xiandian.openstack.cloud.swiftstorage.LoginActivity;
import com.xiandian.openstack.cloud.swiftstorage.MainActivity;
import com.xiandian.openstack.cloud.swiftstorage.R;
import com.xiandian.openstack.cloud.swiftstorage.base.TaskResult;
import com.xiandian.openstack.cloud.swiftstorage.fs.OSSFile;
import com.xiandian.openstack.cloud.swiftstorage.fs.OSSFileSystem;
import com.xiandian.openstack.cloud.swiftstorage.fs.SFile;
import com.xiandian.openstack.cloud.swiftstorage.sdk.service.OpenStackClientService;
import com.xiandian.openstack.cloud.swiftstorage.utils.DisplayUtils;
import com.xiandian.openstack.cloud.swiftstorage.utils.FileIconHelper;
import com.xiandian.openstack.cloud.swiftstorage.utils.FileUtils;
import com.xiandian.openstack.cloud.swiftstorage.utils.GraphicsUtil;
import com.xiandian.openstack.cloud.swiftstorage.utils.PromptDialogUtil;
import com.xiandian.openstack.cloud.swiftstorage.utils.Sort_dialog;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.FileNameMap;
import java.net.URLDecoder;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 展示所有文件的Fragment，内部包含一个ListView。
 *
 * @author 云计算应用与开发项目组
 * @since  V1.0
 */
public class MainFragment extends Fragment
        implements OnRefreshListener, SFileListViewAdapter.ItemClickCallable, SFileEditable {

    private List<SFile> tempFiles;

    //枚举类型的定义
    enum Operate {
        NORMAL,//初始状态
        SELECTALL,//全选
        UNSELECTALL,//全不选
        OPEN,//打开文件夹
        DOWNLOAD,//下载文件
        BACK,//返回，在根目录下连续点击返回，则退出
        NEWDIR,//新建文件夹
        DELETE,//删除
        COPY,//复制
        MOVE,//移动
        RENAME,//重命名
        DETALL,//详细
        UPLOAD,//上传
        REFRESH,//刷新
        PASTE,//粘贴
        MOVETO,//移动到目标文件夹
        SEARCH,//搜索
    }

    enum OperateAll {
        NORMAL,     //初始状态
        SELECTALL,  //全选
        UNSELECTALL,//全不选
    }


    //定义一个枚举类型的变量
    Operate operate = Operate.NORMAL;//最开始为正常模式，显示根目录下所有的文件文件夹
    OperateAll operateAll = OperateAll.NORMAL;//刚开始为正常模式

    //Log 信息标签。
    private String TAG = MainFragment.class.getSimpleName();
    //Context
    private Context context;
    //图片工具类
    FileIconHelper fileIconHelper;
    //操作，确定和取消，默认隐藏，有操作是显示
    private LinearLayout fileActionBar;
    //确定按钮
    Button btnConfirm;
    //取消按钮
    Button btnCancel;
    //下拉刷新
    private SwipeRefreshLayout fileListSwipe;
    //File List View
    private ListView fileListView;
    //是否是复制
    private boolean iscopy = false;
    //复制的文件名称
    String copyFileName = null;
    //复制到的文件地址
    String copyToFileName = null;
    //复制文件的类型
    String copyFileType = null;
    //是否是移动
    private boolean ismove = false;
    //移动的文件名称
    String moveFileName = null;
    //移动到的位置
    String moveToFileName = null;
    //移动的文件类型
    String moveFileType = null;
    //File List View Adapter
    private SFileListViewAdapter fileListViewAdapter;
    //File data model
    List<SFileData> fileListData = new ArrayList<SFileData>();
    //当前展示的文件夹列表（注意：太多临时变量不容易维护）
    private List<SFile> swiftFolders;
    //当前展示的文件列表（注意：太多临时变量不容易维护）
    private List<SFile> swiftFiles;

    SFile copySFile = null;//在这里随便定义的，不知道对不对
    //下载的id
    private int downid = 0;
    //进度条
    private ProgressBar pb;
    //文件大小
    private int fileSize = 0;
    //已下载的文件大小
    private int downLoadFileSize = 0;


    //另外建的方法

    /***
     * 改的通过文件名获得文件类型的方法
     **/
    private String getFileContentType(String fileName) {
        int num = fileName.lastIndexOf(".");
        if (num == -1)
            return "application/msword";
        int length = fileName.length();
        String suffix = fileName.substring(num + 1, length);
        Log.v("suffix", "" + suffix);
        if (suffix.equalsIgnoreCase("jpg") || suffix.equalsIgnoreCase("jpeg") || suffix.equalsIgnoreCase("png") || suffix.equalsIgnoreCase("gif") || suffix.equalsIgnoreCase("bmp")) {
            return "image/*";
        } else if (suffix.equalsIgnoreCase("mp4") || suffix.equalsIgnoreCase("avi") || suffix.equalsIgnoreCase("mpeg") || suffix.equalsIgnoreCase("flv") || suffix.equalsIgnoreCase("mov") || suffix.equalsIgnoreCase("wmv")) {
            return "video/*";
        } else if (suffix.equalsIgnoreCase("txt")) {
            return "text/plain";
        } else if (suffix.equalsIgnoreCase("pdf")) {
            return "application/pdf";
        } else if (suffix.equalsIgnoreCase("mid") || suffix.equalsIgnoreCase("mp3") || suffix.equalsIgnoreCase("wav") || suffix.equalsIgnoreCase("wma") || suffix.equalsIgnoreCase("wav")) {
            return "audio/*";
        } else {
            return "application/msword";
        }

    }

    /*改的在当前文件夹中找到需要删除的目录，并删除*/
    private void recycleDir(String containerName, String dirName) {
        Log.v("删除的目录名：dirName", dirName + "++++++++++");
        //在当前目录下，swiftFolders 查找 dirName 文件夹 ,找到该文件则删除
        for (int i = 0; i < swiftFolders.size(); i++) {
            SFile folder = swiftFolders.get(i);
            Log.v("目录名：", folder.getName() + "++++++++++");
            //folder。getName（）中包含了文件夹的详细路径，并一/结尾，而dirName中没有路径信息，因此比较时，必须使用cleanName方法清除路径信息和/

            if (dirName.equals(cleanName(folder.getName()))) {
                Log.v("目录相等：", folder.getName() + "----------");
                removeDir(containerName, folder);//删除指定目录下的文件和目录
                getService().recycle(containerName, folder.getName(), "DIR");//删除指定目录
            }


        }
    }

    /*改的删除本目录下的文件目录（包括目录下的文件和目录）删除的文件和目录将进入回收站*/
    private void removeDir(String containerName, SFile dir) {
        ArrayList<SFile> dirList = new ArrayList<SFile>(dir.listDirectories());
        ArrayList<SFile> fileList = new ArrayList<SFile>(dir.listFiles());
        for (int i = 0; i < dirList.size(); i++) {
            removeDir(containerName, dirList.get(i));
            getService().recycle(containerName, dirList.get(i).getName(), "DIR");
        }
        for (int j = 0; j < fileList.size(); j++) {
            String fileName = fileList.get(j).getName();
            String contentType = fileList.get(j).getContentType();
            Log.v("删除的完整路径：fileName", fileName + "++++++++");
            getService().recycle(containerName, fileName, contentType);
        }

    }

    /*改的获取文件后缀*/
    private String suffix(String file) {
        int i = file.lastIndexOf(".");
        return file.substring(i + 1, file.length());
    }

    /*改的通过路径名获得目录名，path中包含了目录路径，以 / 结尾*/
    private String getDirName(String path) {
        String temp = path.substring(0, path.length() - 1);//去掉最后的“/”
        int num = temp.lastIndexOf("/");//找到最后/的位置 ，/后面就是目录名了
        return temp.substring(num + 1, temp.length());
    }
    /*（下载1）改的将文件下载到模拟器的存储卡下的download目录中*/
    private void write(OutputStream outstream, InputStream inStream) {
        try {
            int bufferSize =1024;
            byte [] buffer =new byte[bufferSize];
            int len =0;
            while ((len=inStream.read(buffer)) != -1){
                outstream.write(buffer,0,len);
            }
            Log.v("$下载成功！$","!!!!!!!!!!!!!!");
            outstream.close();
        } catch (Exception e) {
            throw  new RuntimeException(e.getMessage(),e);
        }
    }

    /*（下载2）改的在模拟器download 目录下创建一个文件夹，如果该文件夹已经存在，则返回，如果不存在则创建*/
    private void makeDir(String dir) {
        File destFile =null;
        try {
            destFile =new File(Environment.getExternalStorageDirectory().getCanonicalPath()+"/Download/"+dir);
            if(destFile.isDirectory() && destFile.exists())
                return;

            if(destFile != null){
                if(!destFile.mkdir() && !destFile.isDirectory())
                    Log.e(TAG,"Error:make dir fail");
            }

        } catch (IOException e) {
            System.out.print("下载文件出错了嘻嘻");
        }

    }

    /*（下载3）改的下载目录*/
    private void downloadDir(String containerName, SFile dir, String parentDir) {
        //获得需要下载的目录的文件夹系统和文件系统
        ArrayList <SFile> dirList =new ArrayList<SFile>(dir.listDirectories());
        ArrayList <SFile> fileList =new ArrayList<SFile>(dir.listFiles());

        for (int i = 0; i < fileList.size(); i++) {
            String fileName =fileList.get(i).getName();
            Log.v("下载的文件名：",fileName+"++++++++++++");
            ObjectDownload objectDownload =getService().downloadObject(containerName,fileName);

            try {
                Log.v("parentDir父文件夹:","++++++++++++"+parentDir);
                Log.v("cleanName(fileName):","++++++++++++"+cleanName(fileName));
                //将下载到的文件对象写到sd 卡的download 文件夹下的对应文件名中
                write(new FileOutputStream(Environment.getExternalStorageDirectory().getCanonicalPath()+"/Download/"+parentDir+"/"+cleanName(fileName))
                        ,objectDownload.getInputStream());
            } catch (IOException e) {
            }

        }
        for (int i = 0; i < dirList.size(); i++) {
            String dirName =parentDir+"/"+getDirName(dirList.get(i).getName());
            Log.v("dirName:","+++++++++++"+dirName);
            makeDir(dirName);
            downloadDir(containerName,dirList.get(i),dirName);
        }



    }


    /*改的通过文件夹名字（包括路径）获得文件对应的SFile对象的方法*/
    private SFile getSFileByDirName(SFile root, String name) {

        List<SFile> currFolders =new ArrayList<SFile>(root.listDirectories());
        Log.v("传过来的完整路径name",name+"+++++++++++++");
        for (int i = 0; i < currFolders.size(); i++) {
            Log.v("相等吗目录名：",currFolders.get(i).getName());
            if (currFolders.get(i).getName().equals(name)){
                 copySFile = currFolders.get(i);//没有定义这个变量

            }else
                getSFileByDirName(currFolders.get(i),name);
        }
        return  copySFile;
    }

    /*改的复制目录的方法*/
    private void copyDir(String containerName, SFile source, OSSFileSystem dest) {
        //获得需要复制的的文件夹下的所有的目录和文件
        ArrayList <SFile> sourcelistDirs =new ArrayList<SFile>(source.listDirectories());//需要复制的文件夹下的目录系统
        ArrayList <SFile> sourcelistFiles =new ArrayList<SFile>(source.listFiles());//需要复制的文件夹下的文件系统
        //复制文件夹下的目录
        for (int i = 0; i < sourcelistDirs.size(); i++) {
            Log.v("复制的目录名：",sourcelistDirs.get(i).getName()+"++++++++++++++++++++++++++++++++++");//是完整路径
            String temp =sourcelistDirs.get(i).getName();
            String dir =temp.substring(0,temp.length()-1);//去掉目录最后的/
            dir=cleanName(dir);//获得目录名
            getService().createDirectory(containerName,dest.getName()+dir+"/");//创建目录
            OSSFileSystem tempSFile = new OSSFileSystem(dest,dest.getName()+dir+"/");//构建以新目录名命名的文件系统
            dest.putDirectory(dest.getName()+dir,tempSFile);
            copyDir(containerName,sourcelistDirs.get(i),tempSFile);//参：容器名、需要操作的文件夹、需要操作的位置
        }
        //复制文件夹下的文件
        for (int i = 0; i < sourcelistFiles.size(); i++) {
            Log.v("复制的文件名：",sourcelistFiles.get(i).getName());//完整路径
            getService().copy(containerName,//容器名
                    sourcelistFiles.get(i).getName(),//旧的路径
                    dest.getName()+cleanName(sourcelistFiles.get(i).getName()),//新的路径
                    sourcelistFiles.get(i).getContentType());//文件类型
        }

    }










    /**
     * &#x7f3a;&#x7701;&#x6784;&#x9020;&#x5668;&#x3002;
     */
    public MainFragment() {

    }

    /**
     * 构造视图。
     *
     * @param inflater:界面XML
     * @param container：
     * @param savedInstanceState
     * @return
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        context = this.getActivity();
        //(1) Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        fileIconHelper = new FileIconHelper(context);
        //(2)操作按钮（确认和取消），当移动，复制等操作是出现。
        fileActionBar = (LinearLayout) rootView.findViewById(R.id.layout_operation_bar);
        btnConfirm = (Button) rootView.findViewById(R.id.btnConfirm);
        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (iscopy){
                    operate = Operate.PASTE;//点击“粘贴到此 ”/  "确定" 按钮时，将操作模式改为粘贴
                    String [] temp =copyFileName.split(":");//将关于复制的数据用：切割
                    for (int i = 0; i < temp.length; i++) {
                        String [] fileAndType = temp[i].split(">");
                        String file =fileAndType[0];//文件或文件夹名
                        String Type =fileAndType[1];//类型
                        if(Type.equals("DIR")){//复制目录
                            boolean dirISExist =false;//假设复制的目录不存在
                            for (int j = 0; j < swiftFolders.size(); j++) {
                                //在同一目录下复制粘贴文件，不做任何操作
                                if (swiftFolders.get(j).getName().equals(file)){
                                    return;
                                }//目录已存在
                                else  if(getDirName(swiftFolders.get(j).getName()).equals(getDirName(file))){
                                    new AlertDialog.Builder(getActivity()).setTitle("文件已存在，是否全部覆盖？")
                                            .setPositiveButton("确定"
                                                    , new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(DialogInterface dialog, int which) {
                                                            Log.v("确定复制！","!!!!!!!!!!!!!!!!");
                                                            new GetOSSObjectsTask().execute();//启动异步任务执行
                                                            fileActionBar.setVisibility(View.INVISIBLE);//将按设置成不可见fileActionBar是那个视图
                                                        }
                                                    }).setNegativeButton("取消"
                                            , new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialog, int which) {
                                                    copyFileName =null;//清除copy 的内容
                                                    fileActionBar.setVisibility(View.INVISIBLE);//设置成不可见
                                                }
                                            }).show();
                                      dirISExist =true;
                                      break;
                                }
                            }
                            if (!dirISExist){//目录不存在
                                new GetOSSObjectsTask().execute();//启动异步任务
                                fileActionBar.setVisibility(View.INVISIBLE);//将按钮设置成不可见
                            }
                        }else{//复制文件
                            boolean fileIsExist =false;
                            for (int j = 0; j < swiftFiles.size(); j++) {
                                //在同一目录下复制粘贴文件，不做任何操作
                                if (swiftFiles.get(i).getName().equals(file)){
                                    return;
                                }//同名文件已存在
                                else if(cleanName(swiftFiles.get(i).getName()).equals(cleanName(file))){
                                    new AlertDialog.Builder(getActivity()).setTitle("文件已存在，是否全部覆盖？")
                                            .setPositiveButton("确定"
                                                    , new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(DialogInterface dialog, int which) {
                                                            Log.v("文件确定复制！","!!!!!!!!!");
                                                            new GetOSSObjectsTask().execute();//启动异步任务
                                                            fileActionBar.setVisibility(View.INVISIBLE);//设置成不可见
                                                        }
                                                    })
                                            .setNegativeButton("取消"
                                                    , new DialogInterface.OnClickListener() {
                                                        @Override
                                                        public void onClick(DialogInterface dialog, int which) {
                                                            copyFileName =null;
                                                            fileActionBar.setVisibility(View.INVISIBLE);//设置成不可见

                                                        }
                                                    }).show();
                                    fileIsExist =true;
                                    break;
                                }
                            }
                            if (!fileIsExist){//一切正常
                                new GetOSSObjectsTask().execute();//启动异步任务
                                fileActionBar.setVisibility(View.INVISIBLE);//设置成不可见
                            }
                        }

                    }
                }
                btnConfirm.setText("确定");//把粘贴改成确定
            }
        });
        btnCancel = (Button) rootView.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                iscopy =false;
                fileActionBar.setVisibility(View.INVISIBLE);//不可见
                btnConfirm.setText("确定");//改成确定

            }

        });

        //(3) 下拉刷新
        fileListSwipe = (SwipeRefreshLayout) rootView.findViewById(R.id.swipe_refresh_files);
        fileListSwipe.setColorSchemeResources(android.R.color.holo_blue_bright,
                android.R.color.holo_green_light,
                android.R.color.holo_orange_light,
                android.R.color.holo_red_light);

        fileListSwipe.setOnRefreshListener(this);//增加刷新方法

        //(4) 文件列表视图
        fileListView = (ListView) rootView.findViewById(R.id.main_list_root);
        //创适配器
        fileListViewAdapter = new SFileListViewAdapter(context, fileListData, this);
        fileListView.setAdapter(fileListViewAdapter);
        //(5) 读取云存储数据，填充视图
        GetOSSObjectsTask getOSSObjectsTask = new GetOSSObjectsTask();
        getOSSObjectsTask.execute();

        return rootView;
    }


    ///////////////////////////////////////////////

    /**
     * 目前APP的状态记录。
     *
     * @return
     */
    private AppState getAppState() {
        return AppState.getInstance();
    }

    /**
     * 服务。
     *
     * @return
     */
    private OpenStackClientService getService() {
        return OpenStackClientService.getInstance();
    }


    /////////////////////获取云存储对象，转换为文件系统，并填充listView的任务/////////////////////<

    /**
     * 获取云存储的对象。
     */
    private class GetOSSObjectsTask extends AsyncTask<String, Object, TaskResult<Objects>> {


        /**
         * 后台线程任务。
         *
         * @param params
         * @return
         */
        protected TaskResult<Objects> doInBackground(String... params) {
            String containerName = getAppState().getSelectedContainer().getName();//容器名
            //上传
            if (operate == Operate.UPLOAD) {
                try {
                    /****第一中简单的上传
                     //设置文件上传对象对应的文件
                     File file=new File(Environment.getExternalStorageDirectory().getCanonicalPath()+"/test11.txt");
                     FileInputStream fileInput=new FileInputStream(file) ;
                     Log.v("file",file.length()+"");
                     SFile selectedDirectory=getAppState().getSelectedDirectory();
                     String path =selectedDirectory.getName();//获得当前文件系统的文件路径名
                     path =path +"test11.txt";
                     Log.v("路径：",path);
                     Log.v("容器：",getAppState().getSelectedContainer().getName());
                     Log.v("开始上传：","…………");
                     //上传文件。第四个参数“上传位置”包括文件路径和文件名
                     getService().upload(getAppState().getSelectedContainer().getName(),fileInput,"text/plain",path);
                     **/
                    String[] temp = params[0].split(">");
                    String fileName = temp[0];
                    String contentType = temp[1];
                    //设置文件上传对象对应的文件
                    File file = new File(Environment.getExternalStorageDirectory().getCanonicalPath() + "/" + temp[0]);
                    FileInputStream fileInput = new FileInputStream(file);
                    Log.v("file的长度", file.length() + "");
                    SFile selectedDirectory = getAppState().getSelectedDirectory();
                    String path = selectedDirectory.getName();//获得当前文件系统的文件路径名
                    path = path + fileName;
                    Log.v("路径：", path);
                    Log.v("容器：", getAppState().getSelectedContainer().getName());
                    Log.v("开始上传：", "…………");
                    //上传文件。第四个参数“上传位置”包括文件路径和文件名
                    getService().upload(getAppState().getSelectedContainer().getName(), fileInput, contentType, path);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            //新建文件夹
            if (operate == operate.NEWDIR) {
                //当前选择目录
                SFile selectedDirectory = getAppState().getSelectedDirectory();
                String curName = selectedDirectory.getName();//获得当前文件系统文件路径名
                Log.v("curName", curName + "++++++++");
                Log.v("params[0]", params[0] + "---------");
                //在当前文件夹下创建目录param[0]--就是传递过来的参数（即用户输入的文件夹名）
                getService().createDirectory(getAppState().getSelectedContainer().getName(), curName + params[0] + "/");
            }

            //删除
            else if (operate == Operate.DELETE) {
                String[] fileinfo = params[0].split(":");
                Log.v("有几个文件fileInfo.length", fileinfo.length + "----");
                for (int i = 0; i < fileinfo.length; i++) {
                    String[] temp = fileinfo[i].split(">");
                    String fileName = temp[0];
                    String contentType = temp[1];
                    Log.v("fileName", fileName + "+++++++");
                    Log.v("contentType", contentType + "----------");
                    Log.v("fileInfo[0]", fileName + "^^^^^^^^^");

                    if ("DIR".equals(contentType)) {//删除目录
                        recycleDir(containerName, fileName);
                    } else {//删除文件
                        for (int j = 0; j < swiftFiles.size(); j++) {
                            String tempName = swiftFiles.get(j).getName();
                            if (fileName.equals(cleanName(tempName))) {
                                String tempType = swiftFiles.get(j).getContentType();
                                Log.v("删除的文件路径：", tempName + "++++++++");
                                Log.v("容器名：", containerName + "…………");
                                Log.v("删除的文件类型：", contentType + "++++++++");
                                getService().recycle(containerName, tempName, contentType);
                                break;
                            }
                        }
                    }

                }

            }
            //重命名
            else if (operate == Operate.RENAME) {
                String[] fileInfo = params[0].split(":");
                //Log.v("fileInfo.length",fileInfo.length+"---------") ;
                String[] temp = fileInfo[0].split(">");
                String oldFileName = temp[0];//旧路径
                String contentType = temp[1];//类型
                String newFileName = fileInfo[1];
                Log.v("oldfileName", oldFileName + "-------");
                Log.v("conttentType", contentType + "-------");
                Log.v("newfileName", newFileName + "-------");

                //如果改的需要修改的是文件夹，则OoldFIleName 会以/结尾
                if (contentType.equals("DIR")) {
                    for (int i = 0; i < swiftFolders.size(); i++) {
                        String tempName = swiftFolders.get(i).getName();
                        Log.v("tempName", tempName + "-----------");
                        if (oldFileName.equals(tempName)) {
                            String tempPath = oldFileName.substring(0, oldFileName.length() - 1);
                            String filePath = tempPath.substring(0, tempPath.lastIndexOf("/") + 1);
                            Log.v("文件系统i的getname",  swiftFolders.get(i).getName()+"");
                            Log.v("文件路径filepath", filePath);
                            Log.v("newFileName", newFileName);
                            getService().createDirectory(containerName, filePath + newFileName + "/");
                            //创建目录
                            OSSFileSystem tempSFile = new OSSFileSystem((OSSFileSystem) swiftFolders.get(i), filePath + newFileName + "/");
                            ((OSSFileSystem) swiftFolders.get(i)).getParent().putDirectory(filePath + newFileName + "/", tempSFile);
                            renameDir(containerName, swiftFolders.get(i), tempSFile);
                            tempName = tempName.substring(0, tempName.length() - 1);//去掉最后的/
                            recycleDir(containerName, cleanName(tempName));//删除原目录，第二个参数仅包括目录名，不含路径信息
                            break;
                        }
                    }
                } else {
                    int num = oldFileName.lastIndexOf("/");
                    String newPath = oldFileName.substring(0, num + 1) + newFileName;
                    getService().rename(containerName, oldFileName, newPath, contentType);
                }


            }

            //下载
            else if(operate == Operate.DOWNLOAD){
                //只能下载文件-
                /*//当前选择目录
                Log.v("params[0]",params[0]+"………………………………");
                String[] temp =params[0].split(">");
                String downloadFileName = temp [0];//文件名
                int id = Integer.parseInt(temp[1]);//文件ID+++++
                Swift swift = getService().getSwift(getAppState().getSelectedTenant().getId());//获得swift对象
                //在当前文件夹下创建目录 param[0],param[0]就是传递过来的参数（即用户输入的文件夹）
                ObjectDownload  objectDownload = swift.containers().container(getAppState().getSelectedContainer().getName()).download(downloadFileName).execute();
                try {
                    Log.v("下载","…………………………………………");
                    //将下载到的文件对象写入到sd卡的 download 文件夹下的对应文件名中
                    write(new FileOutputStream(
                            Environment.getExternalStorageDirectory().getCanonicalPath()+"/Download/"+cleanName(downloadFileName))
                            ,objectDownload.getInputStream());
                } catch (Exception e) {

                }*/

                //下载文件和文件夹
                Swift  swift =getService().getSwift(getAppState().getSelectedTenant().getId());//创建一个swift 对象
                String[] fileInfo = params[0].split(":");
                Log.v("ifleInfo.length",fileInfo.length+"-------");
                containerName = getAppState().getSelectedContainer().getName();//获得容器名
                for (int i = 0; i < fileInfo.length; i++) {
                    String[] temp = fileInfo[i].split(">");
                    String  fileName = temp[0];//如果是文件，则包含文件路径信息，如果是文件夹，则不

                    String  contentType =temp[1];
                    String  index = temp[2];
                    Log.v("fileName名字",fileName+"+++++");
                    Log.v("conttentType类型",contentType+"+++++");
                    Log.v("文件索引",index+"+++++");
                    Log.v("fileInfo[0]",fileInfo[i]+"………………");

                    if("DIR".equals(contentType)){//下载目录
                        try {
                            makeDir(fileName);//创建目录
                            downloadDir(containerName,swiftFolders.get(Integer.parseInt(index)),fileName);//下载目录
                        } catch (Exception e) {
                        }

                    }else{//下载文件、
                        //在当前文件夹下创建目录 param[0],param[0]就是传递过来的参数（即用户输入的文件夹名）
                        ObjectDownload objectDownload =swift.containers().container(containerName).download(fileName).execute();
                        try {
                            Log.v("开始下载","++++++++++");
                            write(new FileOutputStream
                                    (Environment.getExternalStorageDirectory().getCanonicalPath()+"/Download/"+cleanName(fileName)),objectDownload.getInputStream());
                        } catch (Exception e) {

                        }
                    }


                }
            }
            //粘贴
            else  if (operate ==Operate.PASTE){
                //当前选择目录
                iscopy =false;
                String  fileName =null;
                String  contentType =null;
                SFile selectedDirectory =getAppState().getSelectedDirectory();//获得当前选择的目录系统对象
                String [] fileInfo = copyFileName.split(":");//分割
                Log.v("有几个fileInfo.length",fileInfo.length+"----------------");
                containerName = getAppState().getSelectedContainer().getName();
                for (int i = 0; i < fileInfo.length; i++) {
                    String  temp[] =fileInfo[i].split(">");
                    fileName =temp[0];
                    contentType = temp[1];
                    Log.v("复制的完整路径fileName",fileName+"+++++++++++++");
                    Log.v("复制的文件类型contentType",contentType+"+++++++++++++");
                    Log.v("复制的第一串fileInfo[0]",fileInfo[0]+"+++++++++++++");
                    if(contentType.equals("DIR")){//是文件夹
                        SFile root = selectedDirectory.getRoot();//获得根文件系统
                        SFile myDirFile =getSFileByDirName(root,fileName);//获得需要复制的目录系统
                        Log.v("sourceFile来源：",myDirFile.getName()+"+++++++++"+myDirFile.getContentType());
                        boolean dirFlag =true;
                        for (int j = 0; j < swiftFolders.size(); j++) {
                            if(cleanName(swiftFolders.get(j).getName()).equals(cleanName(fileName))){
                                copyDir(containerName,myDirFile, (OSSFileSystem) swiftFolders.get(j));//没加转型复制目录
                                dirFlag = false;
                                break;
                            }
                        }
                        if (dirFlag){//目录不存在
                            getService().createDirectory(containerName,selectedDirectory.getName()+getDirName(fileName)+"/");//创建目录
                            //新建目录的目录系统
                            OSSFileSystem tempSFile =new OSSFileSystem((OSSFileSystem) selectedDirectory.getParent(),selectedDirectory.getName()+getDirName(fileName)+"/");
                           //将新建的目录系统添加到父目录系统中
                            selectedDirectory.getParent().putDirectory(selectedDirectory.getName()+getDirName(fileName)+"/",tempSFile);
                            copyDir(containerName,(OSSFileSystem)myDirFile,tempSFile);//没加转型复制目录
                        }
                    }else{//是文件
                        //复制文件
                        String tempFileName ="";
                        boolean Flag =true;
                        for (int j = 0; j < swiftFiles.size(); j++) {
                            tempFileName =swiftFiles.get(j).getName();//得到路径包括文件名
                            if(cleanName(fileName).equals(cleanName(tempFileName))){//文件已存在，删除原有文件，复制新文件
                                Log.v("文件相等tempFileName",tempFileName+"+++++++++++");
                                Log.v("当前选择的目录系统名","selectedDirectory.getName():"+selectedDirectory.getName()+"+++++++++++");
                                getService().recycle(containerName,tempFileName,contentType);//删除指定目录
                                getService().copy(containerName,fileName,selectedDirectory.getName()+cleanName(fileName),contentType);//复制的方法
                                Flag = false;
                            }
                        }
                        if(Flag){//文件不存在
                            Log.v("当前文件夹的名字",selectedDirectory.getName());//是完整路径
                            Log.v("复制的contentType",contentType+"+++++++++++");
                            getService().copy(containerName,fileName,selectedDirectory.getName()+cleanName(fileName),contentType);
                        }

                    }
                }

            }







            try {
                //(6) 通过云存储服务，获得当前容器的对象
                Objects objs = getService().getObjects(getAppState().getSelectedContainer().getName());
                return new TaskResult<Objects>(objs);
            } catch (Exception except) {
                return new TaskResult<Objects>(except);
            }
        }




        /**
         * 任务执行完毕。
         *
         * @param result
         */
        protected void onPostExecute(TaskResult<Objects> result) {

            //(7). 如果数据有效
            if (result.isValid()) {
                //当前选择目录
                SFile selectedDirectory = getAppState().getSelectedDirectory();
                //转换读取的对象为文件系统
                SFile fs = getAppState().readFromObjects(result.getResult());
                getAppState().setOSSFS(fs);

                //如果当前选择目录存在（如进入子目录）
                if (selectedDirectory != null && selectedDirectory.hasData() && selectedDirectory.getName() != null) {
                    //重新寻找对应的目录，默认路径不变
                    getAppState().setSelectedDirectory(
                            getAppState().findChild(getAppState().getSelectedDirectory().getRoot(), selectedDirectory.getName()));
                } else {
                    //如果空的，设置为最新读取的数值
                    getAppState().setSelectedDirectory(getAppState().getOSSFS());
                }
                //(8) 根据模拟的文件系统填充ListView
                fillListView();
            } else {
                //提示错误，返回登录
                PromptDialogUtil.showErrorDialog(getActivity(),
                        R.string.alert_error_get_objects, result.getException(),
                        new Intent(getActivity(), LoginActivity.class));
            }
        }
    }




    /////////////////////获取云存储对象，转换为文件系统，并填充listView的任务/////////////////////>

    /////////////////////并填充listView的任务/////////////////////<

    /**
     * 填充“所有”的当前目录数据。
     */
    private void fillListView() {
        setFileListData();

        fileListViewAdapter.notifyDataSetChanged();
        if (getAppState().getSelectedDirectory() != null) {
            //调用MainActivity改变Toolbar的路径信息
            ((MainActivity) getActivity()).setToolbarTitles(getString(R.string.menu_swiftdisk), getAppState().getSelectedDirectory().getName());
        } else {
            GetOSSObjectsTask getObjectsTask = new GetOSSObjectsTask();
            getObjectsTask.execute();
        }
//        //调用MainActivity改变Toolbar的路径信息
//        ((MainActivity) getActivity()).setToolbarTitles(getString(R.string.menu_swiftdisk), getAppState().getSelectedDirectory().getName());
    }

    /**
     * File保持的名称含有路径，进行分解，只取文件名称。
     *
     * @param path
     * @return the string
     */
    private String cleanName(String path) {
        String[] parts = path.split("/");
        return parts[parts.length - 1];
    }

    /**
     * 根据当前选择目录，转换成ListData。
     */
    private void setFileListData() {
        //显示格式，这里简单统一处理
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        //清空

        fileListData.clear();

        //子目录文件夹
        SFile currentFolder = getAppState().getSelectedDirectory();
        if (currentFolder != null) {
            swiftFolders = new ArrayList<SFile>(currentFolder.listDirectories());
            swiftFiles = new ArrayList<SFile>(currentFolder.listFiles());
        }
        if (swiftFolders != null) {
            //文件夹
            for (int i = 0; i < swiftFolders.size(); i++) {
                SFile dir = swiftFolders.get(i);
                SFileData fileData = new SFileData();
                //1 Icon 2 name 3 time 4 size 5 folder 6 index 7  checked
                //默认目录图标
                fileData.setImageResource(R.drawable.ic_file_folder);
                fileData.setFileName(cleanName(dir.getName()));
                fileData.setFolder(true);
                //记录对应的信息
                fileData.setIndex(i);

                /*改的全选文件夹的*/
                if (operateAll == OperateAll.SELECTALL)
                    fileData.setChecked(true);
                else if (operateAll == OperateAll.UNSELECTALL)
                    fileData.setChecked(false);
                else
                    fileData.setChecked(false);


                fileData.setFolder(true);
                Calendar calendar = dir.getLastModified();
                //Todo: Why calendar == can be null?
                fileData.setLastModifiedTime(calendar == null ? System.currentTimeMillis() : calendar.getTimeInMillis());
                fileData.setLastModified(calendar == null ? "" : dateFormat.format(calendar.getTime()));
                fileListData.add(fileData);
            }
        }
        if (swiftFiles != null) {
            //文件
            for (int i = 0; i < swiftFiles.size(); i++) {
                SFile file = swiftFiles.get(i);
                SFileData fileData = new SFileData();
                String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/openstack/" + cleanName(file.getName());
                //1 Icon 2 name 3 time 4 size 5 folder 6 index 7  checked
                //目前采用默认图标
                if (file.getContentType().contains("image")) {
                    Bitmap bitmap = fileIconHelper.getImageThumbnail(filePath);
                    if (bitmap != null) {
                        fileData.setImage(bitmap);
                    } else {
                        fileData.setImageResource(R.drawable.ic_file_pic);
                    }

                } else if (file.getContentType().contains("video")) {
                    Bitmap bitmap = fileIconHelper.getVideoThumbnail(filePath);
                    if (bitmap != null) {
                        fileData.setImage(bitmap);
                    } else {
                        fileData.setImageResource(R.drawable.ic_file_video);
                    }
                } else if (file.getContentType().contains("audio")) {
                    fileData.setImageResource(R.drawable.ic_file_music);
                } else {
                    fileData.setImageResource(R.drawable.ic_file_doc);
                }
                //其他暂时都认为是文档，区分office\pdf\txt\html作为扩展内容，由学习者实现.
                //如果需要实现图片预览，目前服务器端没有实现，需要下载本地，产生缩略图

                fileData.setFileName(cleanName(file.getName()));
                fileData.setFolder(true);
                //记录对应的信息
                fileData.setIndex(i);

                /*改的全选文件的*/
                if (operateAll == OperateAll.SELECTALL)
                    fileData.setChecked(true);
                else if (operateAll == OperateAll.UNSELECTALL)
                    fileData.setChecked(false);
                else
                    fileData.setChecked(false);

                Calendar calendar = file.getLastModified();
                fileData.setLastModifiedTime(calendar.getTimeInMillis());
                fileData.setLastModified(dateFormat.format(calendar.getTime()));
                fileData.setFileSize(file.getSize());
                fileData.setFolder(false);
                fileData.setIndex(i);
                fileListData.add(fileData);
            }
        }

        /*最后将操作模式改成NORMAL*/
        operateAll = OperateAll.NORMAL;
        Log.d(TAG, fileListData.toString());
    }


    /////////////////////并填充listView的任务/////////////////////>


    /////////////////////点击条目进入下一级目录/////////////////////<


    /**
     * 进入选择条目，如果是目录进入下一级目录。
     * 如果是文件，传递给Android系统，启动默认支持打开程序开启。
     *
     * @param position
     */
    @Override
    public void intoItem(int position) {

        //选择对应的数据
        SFileData item = fileListData.get(position);
        //是否是目录
        boolean isFolder = item.isFolder();
        //对应的数据Index
        int index = item.getIndex();
        //如何是目录，进入下一级别
        if (isFolder) {
            // 文件夹
            getAppState().setSelectedDirectory(swiftFolders.get(position));
            fillListView();
        } else {

        }
    }

    /////////////////////点击条目进入下一级目录/////////////////////>

    /////////////////////回退操作/////////////////////<

    /**
     * 当主Activity进行回退时，如果不再跟目录，需要回退到上一次目录，返回调用的Activity一个状态，是否回退。
     * 如果是文件，传递给Android系统，启动默认支持打开程序开启。
     *
     * @return false 没有回退，true进行了回退操作
     */
    public boolean onContextBackPress() {
        //如果是跟元素
        if (getAppState().getSelectedDirectory().getParent() == null) {
            return false;
        } else {
            getAppState().setSelectedDirectory(getAppState().getSelectedDirectory().getParent());
            fillListView();
            return true;
        }
    }

    /////////////////////回退操作/////////////////////>

    /////////////////////回退操作/////////////////////>

    /**
     * SwipeRefreshLayout实现了下拉刷新，内部视图是ScrollView、ListView或GridView。
     * 当下拉组件时，调用该方法。
     */
    @Override
    public void onRefresh() {
        fileListSwipe.setRefreshing(true);
        // 获取对象，重新获取当前目录对象
        GetOSSObjectsTask getObjectsTask = new GetOSSObjectsTask();
        getObjectsTask.execute();
        //2秒刷新事件
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                fileListSwipe.setRefreshing(false);
            }
        }, 2000);
    }
    /////////////////////回退操作/////////////////////>

    /**
     * Activity之间调用和回调传递数据使用 startActivityForResult()  setResult() onActivityResult()。
     * 目前上传文件，拍照，上传数据使用外部的Activity。
     *
     * @param requestCode
     * @param resultCode:返回的key
     * @param data：返回的内容
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {

            default:
                break;
        }

    }


    ///////////////可编辑的实现//////////////////

    @Override
    public void search(String fileName) {

    }

    @Override
    public void share() {

    }

    @Override
    public void selectAll() {//全选
        //设置操作模式为selectAll 启动异步任务
        operateAll = OperateAll.SELECTALL;
        new GetOSSObjectsTask().execute("");
    }

    @Override
    public void unselectAll() {//全不选
        operateAll = OperateAll.UNSELECTALL;
        new GetOSSObjectsTask().execute("");

    }

    @Override
    public void openFile(SFile filePath) {

    }

    @Override
    public void createDir(String filePath) {//创建文件夹
        Toast.makeText(getActivity(), "创建文件夹", Toast.LENGTH_SHORT).show();//弹出创建文件夹的提示信息
        /**新建文件夹**/
        final EditText et = new EditText(getActivity());//定义一个输入，文本框变量，该变量作为弹出对话框的视图的内容

        //新建一个弹出对话框
        new AlertDialog.Builder(getActivity()).setTitle("请输入文件夹名字").setView(et)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.v("表示执行了代码", "………………");
                        String newDirName = et.getText().toString();//获得用户输入的内容
                        Log.v("新建文件夹的名字是：", newDirName);
                        operate = Operate.NEWDIR;
                        //将用户输入的内容newDirName传递给异步任务GetObject的 doInBackground方法，这里只能采用这种方式，否则获取不到用户输入的内容
                        new GetOSSObjectsTask().execute(newDirName);
                    }
                }).setNegativeButton("取消", null).show();

    }

    @Override
    public void upload() {//上传
        operate = Operate.UPLOAD;
        new GetOSSObjectsTask().execute("");

        Toast.makeText(getActivity(), "上传文件", Toast.LENGTH_SHORT).show();//弹出提示信息
        /**创建文件夹***/
        final EditText et = new EditText(getActivity());//定义一个输入文本框变量，该变量作为弹出对话框的视图内容
        //新建一个弹出对话框
        new AlertDialog.Builder(getActivity()).setTitle("请输入上传的文件名").setView(et).setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Log.v("上传文件", "………………");
                String fileName = et.getText().toString();//获得用户输入的内容
                Log.v("上传的文件名", fileName);
                try {
                    File tempFile = new File(Environment.getExternalStorageDirectory().getCanonicalPath() + "/" + fileName);
                    if (!tempFile.exists()) {
                        Toast.makeText(getActivity(), "您输入的文件不存在！", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                String contentType = getFileContentType(fileName);
                if (contentType == null)
                    contentType = "application/msword";
                fileName = fileName + ">" + contentType;
                operate = Operate.UPLOAD;
                //将用户输入的内容 newDirName 传递给异步任务GetObject的 doInBackground方法，这里只能采用这种方式，否则获取不到用户输入的内容
                new GetOSSObjectsTask().execute(fileName);
            }
        }).setNegativeButton("取消", null).show();

    }

    @Override
    public void download() {//下载

        //只下载文件
        /*String  fileName ="";
        int   i =0;
        for(SFileData fd: fileListData){
            if (fd.isChecked()){
                if(fd.isFolder()){//是目录
                    Toast.makeText(getActivity(),"不能下载目录！",Toast.LENGTH_SHORT).show();
                    return;
                }else{//是文件
                    i++;
                    fileName =swiftFiles.get(fd.getIndex()).getName();
                    Log.v("ID:",fd.getIndex()+"");
                    Log.v("NAME:",fd.getFileName()+"");//不知道是什么
                    fileName =fileName +">"+fd.getIndex();
                }
            }
        }
        if( i ==1){
            operate =Operate.DOWNLOAD;
            Log.v("fileName:",""+fileName);
            new GetOSSObjectsTask().execute(fileName);
        }else if ( i==0){
            Toast.makeText(getActivity(),"没有指定下载的文件！",Toast.LENGTH_SHORT).show();
        }else{
            Toast.makeText(getActivity(),"不能同时下载多个文件！",Toast.LENGTH_SHORT).show();
        }*/


        //下载文件和文件夹
        String fileName ="";
        /*
        * data数据中包括目录和文件、目录与目录、目录与文件、文件与文件之间用 ： 隔开，目录中包含目录路径和DIR
        * 这两个用 >隔开，文件中包含文件路径和文件路径类型，这两个也用> 隔开
        * */
        String data ="";
        String conttentTYpe ="";
        for (SFileData fd : fileListData) {
            if (fd.isChecked()) {
                Log.v("文件名file:",fd.getFileName());

                if (fd.isFolder()) {//是文件夹
                    fileName =fd.getFileName();//获取下载的文件夹的名字，包括路径，下载目录时，不需要路径信息
                    Log.v("名字file:",fileName+"是文件夹！");
                    //文件夹名 > DIR >文件夹索引，文件夹的索引后面会用到，用来去的跟索引对应的SFile 对象
                    data =data+fileName +">"+"DIR" +">" +fd.getIndex()+":";
                } else {//是文件
                    fileName =swiftFiles.get(fd.getIndex()).getName();//获得要下载的文件名，下载的文件名必须包括路径
                    Log.v("下载的文件名：",fileName+"");
                    conttentTYpe = swiftFiles.get(fd.getIndex()).getContentType();
                    //文件名 > 文件类型 >文件索引 ，文件索引没用， 为了跟下载目录的格式一样
                    data =data + fileName +">"+conttentTYpe +">"+ fd.getIndex()+":";

                }
            }
        }
        if ("".equals(data)) {
            return;
        } else {
            data =data.substring(0,data.length()-1);//去掉最后多余的冒号
            operate =Operate.DOWNLOAD;
            Log.v("data",data);
            new GetOSSObjectsTask().execute(data);
        }


    }




    @Override
    public void takePhoto() {

    }

    @Override
    public void recordvideo() {

    }

    @Override
    public void recordaudio() {

    }

    @Override
    public void rename(String oldFilePath, String newFilePath) {
        /*重命名文件名*/
        /*
        int i=0;
        String fileName ="";
        for (SFileData fd : fileListData) {
            if (fd.isChecked()) {
                if (fd.isFolder()) {
                    Toast.makeText(getActivity(),"不能重命名目录！",Toast.LENGTH_LONG).show();
                    new GetOSSObjectsTask().execute("");
                    return;
                }else{
                    fileName =swiftFiles.get(fd.getIndex()).getName();
                    fileName =fileName +">"+swiftFiles.get(fd.getIndex()).getContentType();
                    Log.v("原文件名：",fileName);
                    i++;
                }
            }

        }

        if (i>1) {
            Toast.makeText(getActivity(),"不能同时重命名多个文件！",Toast.LENGTH_SHORT).show();
            new GetOSSObjectsTask().execute("");
            return;
        }else if(i==1){
            final String oldfileName =fileName;
            final List<SFile> tempFolder =swiftFolders;
            tempFiles = swiftFiles;
            Log.v("重命名文件！","+++++++++++");
            //新建文件
            final EditText et=new EditText(getActivity());//定义一个输入文本框变量，该变量作为弹出对话框的视图内容
            //新建一个弹出对话框
            new AlertDialog.Builder(getActivity()).setTitle("请输入新的文件名：").setView(et).setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String newFileName =et.getText().toString();//获得用户输入的内容
                    for (int i = 0; i < tempFiles.size(); i++) {
                        String tempFile= tempFiles.get(i).getName();
                        if (newFileName.equals(cleanName(tempFile))){
                            Toast.makeText(getActivity(),"新文件名已经存在，不能使用",Toast.LENGTH_LONG).show();
                            new GetOSSObjectsTask().execute("");
                            return;
                        }
                    }
                    String [] temp = oldfileName.split(">");
                    String oldTempFile = cleanName(temp[0]);//去掉文件路径、

                    if (!suffix(oldTempFile).equalsIgnoreCase(suffix(newFileName))){
                        Toast.makeText(getActivity(),"新文件类型与老文件类型不一致！",Toast.LENGTH_LONG).show();
                        new GetOSSObjectsTask().execute("");
                        return;
                    }
                    operate= Operate.RENAME;
                    String data =oldfileName +":"+newFileName;
                    Log.v("data",data);
                    new GetOSSObjectsTask().execute(data);
                }
            }).setNegativeButton("取消",null).show();
            Log.v("新的文件名",et.getText().toString());
            Log.v("重命名文件","+++++++++");
        }

    */
     /*重命名文件和文件夹*/
        int i = 0;
        String fileName = "";
        for (SFileData fd : fileListData) {
            if (fd.isChecked()) {
                if (fd.isFolder()) {
                    fileName = swiftFolders.get(fd.getIndex()).getName();//获取原文件的名字
                    fileName = fileName + ">" + "DIR";
                    Log.v("原文件名：", fileName);
                    i++;
                } else {
                    fileName = swiftFiles.get(fd.getIndex()).getName();
                    fileName = fileName + ">" + swiftFiles.get(fd.getIndex()).getContentType();
                    Log.v("原文件名：", fileName);
                    i++;
                }
            }

        }

        if (i > 1) {
            Toast.makeText(getActivity(), "不能同时重命名多个文件或文件夹！", Toast.LENGTH_SHORT).show();
            new GetOSSObjectsTask().execute("");
            return;
        } else if (i == 1) {
            final String oldfileName = fileName;
            final List<SFile> tempFolder = swiftFolders;
            final List<SFile> tempFiles = swiftFiles;
            Log.v("重命名文件！", "+++++++++++");
            //新建文件
            final EditText et = new EditText(getActivity());//定义一个输入文本框变量，该变量作为弹出对话框的视图内容
            //新建一个弹出对话框
            new AlertDialog.Builder(getActivity()).setTitle("请输入新的文件名：").setView(et).setPositiveButton("确定", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String newFileName = et.getText().toString();//获得用户输入的内容

                    if (oldfileName.endsWith("DIR")) {//修改目录名
                        //循环查找当前目录下是否存在重命名的目录名，如存在，则不允许重命名
                        for (int i = 0; i < tempFolder.size(); i++) {
                            String dir = tempFolder.get(i).getName();
                            String tempDir = dir.substring(0, dir.length() - 1);//去掉最后的/
                            if (newFileName.equals(cleanName(tempDir))) {
                                Toast.makeText(getActivity(), "新目录名已存在，不能使用！", Toast.LENGTH_LONG).show();
                                new GetOSSObjectsTask().execute("");
                                return;
                            }
                        }
                    } else {//修改文件名
                        for (int i = 0; i < tempFiles.size(); i++) {
                            String tempFile = tempFiles.get(i).getName();
                            if (newFileName.equals(cleanName(tempFile))) {
                                Toast.makeText(getActivity(), "新文件名已经存在，不能使用", Toast.LENGTH_LONG).show();
                                new GetOSSObjectsTask().execute("");
                                return;
                            }
                        }
                        String[] temp = oldfileName.split(">");
                        String oldTempFile = cleanName(temp[0]);//去掉文件路径、

                        if (!suffix(oldTempFile).equalsIgnoreCase(suffix(newFileName))) {
                            Toast.makeText(getActivity(), "新文件类型与老文件类型不一致！", Toast.LENGTH_LONG).show();
                            new GetOSSObjectsTask().execute("");
                            return;
                        }
                    }
                    operate = Operate.RENAME;
                    String data = oldfileName + ":" + newFileName;
                    Log.v("data", data);
                    new GetOSSObjectsTask().execute(data);
                }
            }).setNegativeButton("取消", null).show();
            Log.v("新的文件名", et.getText().toString());
            Log.v("重命名文件", "+++++++++");
        }
    }


    /*重命名文件夹
    container:容器名称
    * source: 重命名的源文件夹的文件系统信息
    * dest:新命名的文件夹名
    * */
    private void renameDir(String container, SFile source, OSSFileSystem dest) {
        //需要重命名的文件夹下的目录系统
        ArrayList<SFile> sourcelistDirs = new ArrayList<SFile>(source.listDirectories());
        //需要重命名的文件夹下的文件系统
        ArrayList<SFile> sourcelistFiles = new ArrayList<SFile>(source.listFiles());
        //复制文件夹下的目录
        for (int i = 0; i < sourcelistDirs.size(); i++) {
            String temp = sourcelistDirs.get(i).getName();
            String dir = temp.substring(0, temp.length() - 1);//去掉目录路径最后的/
            dir = cleanName(dir);//获得目录名
            getService().createDirectory(container, dest.getName() + dir + "/");//创建目录
            OSSFileSystem tempSFile = new OSSFileSystem(dest, dest.getName() + dir + "/");
            dest.putDirectory(dest.getName() + dir, tempSFile);
            renameDir(container, sourcelistDirs.get(i), tempSFile);
        }
        for (int j = 0; j < sourcelistFiles.size(); j++) {
            getService().copy(container, sourcelistFiles.get(j).getName(),
                    dest.getName() + cleanName(sourcelistFiles.get(j).getName()),
                    sourcelistFiles.get(j).getContentType());
        }
    }


    @Override
    public void copy(String fromPath, String toPath) {//复制
        String fileName  ="";
        /*
        data数据中包括目录和文件、目录与目录、目录与文件、文件与文件、
        之间用 ： 隔开，目录中包括目录路径、DIR 用 > 隔开 ，文件中包括文件路径、类型 用 > 号隔开
        */
        String data ="";
        String  contentType ="";
        for (SFileData fd : fileListData) { //fileListData 理解是文件系统列表集合
            if(fd.isChecked()){
                Log.v("打印fd.getFileName获得的文件名：",fd.getFileName());
                if (fd.isFolder()){//是文件夹
                    fileName = swiftFolders.get(fd.getIndex()).getName();//获得完整路径
                    Log.v("打印获得的文件名：",swiftFolders.get(fd.getIndex()).getName());
                    Log.v("file",fileName+"是文件夹");
                    data =data +fileName +">" +"DIR" +":";// 文件夹名字 > DIR
                }else{//是文件
                    fileName =swiftFiles.get(fd.getIndex()).getName();//获得完整路径
                    contentType = swiftFiles.get(fd.getIndex()).getContentType();//获得文件类型
                    Log.v("file",fileName+"是文件");
                    data =data +fileName +">" +contentType +":";// 文件名  > 文件类型
                }

            }

        }
        if ("".equals(data)){//为空，返回
            return;
        }else {//不为空，继续
            iscopy =true;//表示是复制
            copyFileName = data.substring(0,data.length()-1);//去掉最后的 : 号
            Log.v("copyFileName",copyFileName);
            btnConfirm.setText("粘贴");//将确定按钮的显示内容改成粘贴
            fileActionBar.setVisibility(View.VISIBLE);//将 “粘贴”，“取消” 按钮设置成可见 fileActionBar 是那个视图
        }
    }

    @Override
    public void move(String fromPath, String toPath) {

    }

    @Override
    public void recycle(String filePath) {//删除
        String fileName = "";
        /*data 数据中包括目录和文件、目录与目录、目录与文件、文件与文件之间用：
        隔开，目录中包括目录路径和DIR，这两个用 >
        隔开，文件中包括文件路径和文件；类型，这两个也用>隔开
        * */
        String data = "";
        String contentType = "";
        for (SFileData fd : fileListData) {
            if (fd.isChecked()) {
                Log.v(" fd.getFileName()", fd.getFileName() + "");
                if (fd.isFolder()) {
                    Log.v(" fd.getFileName()", fd.getFileName() + "是文件夹");
                    fileName = fd.getFileName();
                    data = data + fileName + ">" + "DIR" + ":";
                } else {
                    Log.v("fd.getFileName()", fd.getFileName() + "是文件");
                    fileName = fd.getFileName();
                    contentType = getFileContentType(fileName);
                    data = data + fileName + ">" + contentType + ":";
                }
            }
        }
        if ("".equals(data)) {
            return;
        } else {
            data = data.substring(0, data.length() - 1);
            operate = Operate.DELETE;
            Log.v("data", data);
            new GetOSSObjectsTask().execute(data);
        }

    }

    @Override
    public void sort() {

    }

    @Override
    public void details(int type, boolean ascend) {//文件和文件夹的详情
        int i = 0;
        String fileName = "";
        double fileSize = 0;
        String strFileSize = "";
        String lastModify = "";
        String path = "";
        String contentType = "";
        SFile myFile = null;
        String includeFiles = "";
        DecimalFormat df = new DecimalFormat("#.00");//做数字的格式化处理，取整数部分和两位小数
        for (SFileData fd : fileListData) {
            if (fd.isChecked()) {
                if (fd.isFolder()) {//是文件夹
                    myFile = swiftFolders.get(fd.getIndex());
                    int dirNum = myFile.listDirectories().size();
                    int fileNum = myFile.listFiles().size();
                    includeFiles = "文件" + fileNum + "文件夹：" + dirNum;
                    path = myFile.getName();//获得原文件的名字，包括路径信息
                    fileName = getDirName(path);//新建的方法去掉文件夹路径的信息，值留下文件夹的名字

                    // fileSize =fd.getFileSize();myFile.getSize();

                    fileSize = myFile.getSize();
                    Log.v("文件夹大小：", fileSize + "");
                    if (fileSize < 1024) {
                        strFileSize = fileSize + "B";

                    } else if (fileSize < 1024 * 1024) {
                        fileSize = fileSize / 1024;
                        strFileSize = df.format(fileSize);
                        strFileSize = strFileSize + "KB";

                    } else if (fileSize < 1024 * 1024 * 1024) {
                        fileSize = fileSize / (1024 * 1024);
                        strFileSize = df.format(fileSize);
                        strFileSize = strFileSize + "MB";

                    } else if (fileSize < 1024 * 1024 * 1024 * 1024) {
                        fileSize = fileSize / (1024 * 1024 * 1024);
                        strFileSize = df.format(fileSize);
                        strFileSize = strFileSize + "GB";

                    }
                    lastModify =fd.getLastModified();
                    contentType ="DIR";
                    Log.v("原文件夹名：",fileName);
                    i++;
                } else {//是文件夹
                    myFile =swiftFiles.get(fd.getIndex());
                    path = myFile.getName();//获取原文件的名字
                    fileName=cleanName(path);//删除路径，只留下文件名
                    fileSize = fd.getFileSize();
                    contentType =myFile.getContentType();
                    Log.v("文件大小",fileSize+"");

                    if (fileSize < 1024) {
                        strFileSize = fileSize + "B";

                    } else if (fileSize < 1024 * 1024) {
                        fileSize = fileSize / 1024;
                        strFileSize = df.format(fileSize);
                        strFileSize = strFileSize + "KB";

                    } else if (fileSize < 1024 * 1024 * 1024) {
                        fileSize = fileSize / (1024 * 1024);
                        strFileSize = df.format(fileSize);
                        strFileSize = strFileSize + "MB";

                    } else if (fileSize < 1024 * 1024 * 1024 * 1024) {
                        fileSize = fileSize / (1024 * 1024 * 1024);
                        strFileSize = df.format(fileSize);
                        strFileSize = strFileSize + "GB";
                    }
                    lastModify =fd.getLastModified();
                    Log.v("原文件名",fileName+"");
                    i++;
                }
            }

        }
        if ( i > 1){
            Toast.makeText(getActivity(),"不能同时显示多个文件详情",Toast.LENGTH_LONG).show();
        }else  if( i==1){
            Log.v("显示详情","…………");
            contentType =contentType.replace("%252F","/");//文件类型中的 / 设置的值 %252F 需要替换成/
            contentType =contentType.replace("%2F","/");//文件类型中的 / 设置的值 %2F 需要替换成/

            //新建一个弹出对话框
            if(contentType.equals("DIR"))
                new AlertDialog.Builder(getActivity()).setTitle("详情")
                        .setItems(new String[] {
                                "文件夹名称："+fileName,
                                "文件夹大小："+strFileSize,
                                "包括："+includeFiles,
                                "最新修改时间："+lastModify,
                                "路径："+path
                        },null).setPositiveButton("确定",null).show();
            else
                new AlertDialog.Builder(getActivity()).setTitle("详情")
                        .setItems(new String[] {
                                "文件名称："+fileName,
                                "文件大小："+strFileSize,
                                "文件类型："+contentType,
                                "最新修改时间："+lastModify,
                                "路径："+path
                        },null).setPositiveButton("确定",null).show();
        }
    }


    /**
     * 刷新当前视口。
     */
    public void refresh() {
        fillListView();
    }

    @Override
    public void restroe() {

    }

    @Override
    public void empty() {

    }

    /**
     * 获取选择的文件或目录。
     *
     * @return
     */
    private List<SFile> getSelectedFiles() {
        ArrayList<SFile> selected = new ArrayList<SFile>();
        for (SFileData fd : fileListData) {
            if (fd.isChecked()) {
                selected.add(fd.isFolder() ? swiftFolders.get(fd.getIndex()) : swiftFiles.get(fd.getIndex()));
            }
        }
        return null;
    }

    /**
     * 获取选择的文件或目录。
     *
     * @return
     */
    private SFile getFirstSelectedFile() {
        ArrayList<SFile> selected = new ArrayList<SFile>();
        for (SFileData fd : fileListData) {
            if (fd.isChecked() && !fd.isFolder()) {
                return swiftFiles.get(fd.getIndex());
            }
        }
        return null;
    }

    /**
     * 获取选择的第一个文件或目录。
     *
     * @return
     */
    private SFile getFirstSelected() {
        for (SFileData fd : fileListData) {
            if (fd.isChecked()) {
                return fd.isFolder() ? swiftFolders.get(fd.getIndex()) : swiftFiles.get(fd.getIndex());
            }
        }
        return null;
    }


    ///////////////可编辑的实现//////////////////


}
