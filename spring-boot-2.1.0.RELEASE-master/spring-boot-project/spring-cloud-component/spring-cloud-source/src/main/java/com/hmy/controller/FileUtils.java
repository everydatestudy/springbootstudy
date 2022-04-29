package com.hmy.controller;


import javax.servlet.http.HttpServletResponse;
import java.io.*;

public class FileUtils {

    public static final int DEFAULT_SPEED = 1024 * 1024;

    /**
     * 将读取到的字节利用写流写出去
     * @param is 读流
     * @param os 写流
     * @param speed 写出，读入速度
     * @return boolean
     */
    public static boolean writeFile(InputStream is, OutputStream os, int speed) {
        boolean success = false;
        BufferedInputStream bis = null;
        BufferedOutputStream bos = null;
        try {
            bis = new BufferedInputStream(is);
            bos = new BufferedOutputStream(os);
            int fileSize = is.available();
            long written = 0;
            byte bytes[] = new byte[speed];
            while (written < fileSize) {
                if (written + speed > fileSize) {
                    speed = (int) (fileSize - written);
                    bytes = new byte[speed];
                }
                bis.read(bytes);
                bos.write(bytes);
                bos.flush();
                written += speed;
            }
            success = true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            bis.close();
            bos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return success;
    }

    /**
     * @see FileUtils#writeFile(InputStream, OutputStream, int)
     * @param file 被读取的文件
     * @param os 写出流
     * @return boolean
     * @throws IOException
     */
    public static boolean writeFile(File file, OutputStream os) throws IOException {
        return writeFile(new FileInputStream(file), os, DEFAULT_SPEED);
    }

    public static String getSuffix(String path) {
        int index = path.lastIndexOf(".");
        return index != -1 ? path.substring(index) : "";
    }

    /**
     * 将文件read拷贝到write
     * @param read 被读取的文件
     * @param write 指向文件
     * @return boolean
     * @throws IOException
     */
    public static boolean copyFile(File read, File write) throws IOException {
        return writeFile(new FileInputStream(read), new FileOutputStream(write), DEFAULT_SPEED);
    }

//    public static boolean copyFile(MultipartFile read, File write) throws IOException {
//        return writeFile(read.getInputStream(), new FileOutputStream(write), DEFAULT_SPEED);
//    }

    public static void downloadFile(InputStream is, HttpServletResponse response) throws IOException {
        FileUtils.writeFile(is, response.getOutputStream(), DEFAULT_SPEED);
    }

    public static void downloadFile(File file, HttpServletResponse response) throws IOException {
        String fileName = file.getName();
        response.setHeader("Content-Disposition", "attachment;filename=" + new String(fileName.getBytes("GBK"), "ISO8859_1"));
        FileUtils.writeFile(file, response.getOutputStream());
    }

    public static boolean createFile(String path,String content){
        File myFilePath = new File(path);
        try {
            if (!myFilePath.exists()) {
                myFilePath.createNewFile();
            }
            FileWriter resultFile = new FileWriter(myFilePath);
            PrintWriter myFile = new PrintWriter(resultFile);
            myFile.println(content);
            myFile.close();
            resultFile.close();
            return true;
        }
        catch (Exception e) {
            System.out.println("新建文件操作出错");
            return false;
        }
    }


    public static boolean deleteFile(String path){
        File myDelFile = new File(path);
        try {
            myDelFile.delete();
            return true;
        }
        catch (Exception e) {
            System.out.println("删除文件操作出错");
            return false;
        }
    }

    public static boolean updateFileName(String filepath,String file2path){
        File toBeRenamed = new File(filepath);
        if (!toBeRenamed.exists() || toBeRenamed.isDirectory()) {

            System.out.println("File does not exist: "+filepath);
            return false;
        }
        File newFile = new File(file2path);
        if (toBeRenamed.renameTo(newFile)) {
            return true;
        } else {
            return false;

        }
    }

}



