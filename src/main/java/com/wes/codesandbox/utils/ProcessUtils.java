package com.wes.codesandbox.utils;

import com.wes.codesandbox.model.ExecuteMessage;
import org.springframework.util.StopWatch;

import java.io.*;

public class ProcessUtils {
    /**
     *
     * @param executeProcess
     * @param opName
     * @return
     */
    public static ExecuteMessage processGetMessage(Process executeProcess,String opName){
        ExecuteMessage executeMessage=new ExecuteMessage();
        try{
            StopWatch stopWatch=new StopWatch();
            stopWatch.start();
            //等待程序执行，获取错误码
            int exitValue = executeProcess.waitFor();
            executeMessage.setExitValue(exitValue);
            if(exitValue!=0){
                System.out.println(opName+"失败：错误码："+exitValue);
                //分批获取进程输出
                BufferedReader bufferedReader=new BufferedReader(new InputStreamReader(executeProcess.getInputStream()));
                StringBuilder  executeOutput=new StringBuilder();
                //逐行读取
                String  executeOutputLine ;
                while ((executeOutputLine=bufferedReader.readLine())!=null){
                    executeOutput.append(executeOutputLine);
                }
                StringBuilder  executeErrorOutput=new StringBuilder();
                //分批获取进程错误输出
                BufferedReader  errorBufferedReader=new BufferedReader(new InputStreamReader(executeProcess.getErrorStream()));
                //逐行读取
                String  errorOutputLine;
                while ((errorOutputLine=errorBufferedReader.readLine())!=null){
                    executeErrorOutput.append(errorOutputLine);
                }
                executeMessage.setErrorMessage( executeErrorOutput.toString());
            }else{
                System.out.println(opName+"成功");
                //分批获取进程输出
                BufferedReader  bufferedReader=new BufferedReader(new InputStreamReader(executeProcess.getInputStream()));
                StringBuilder  executeOutput=new StringBuilder();
                //逐行读取
                String  executeOutputLine ;
                while ((executeOutputLine=bufferedReader.readLine())!=null){
                    executeOutput.append(executeOutputLine);
                }
                executeMessage.setMessage( executeOutput.toString());
            }
            stopWatch.stop();
            executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
        }catch (Exception e){
            e.printStackTrace();
        }
        return   executeMessage;
    }

    public static ExecuteMessage interactProcessGetMessage(Process executeProcess,String args){
    ExecuteMessage executeMessage=new ExecuteMessage();
        try{

            InputStream  inputStream=executeProcess.getInputStream();
            OutputStream  outputStream=executeProcess.getOutputStream();
            OutputStreamWriter  outputStreamWriter=new OutputStreamWriter(outputStream);
            outputStreamWriter.write(args);
            outputStreamWriter.flush();
            //分批获取进程输出
            BufferedReader  bufferedReader=new BufferedReader(new InputStreamReader(executeProcess.getInputStream()));
            StringBuilder  executeOutput=new StringBuilder();
            //逐行读取
            String  executeOutputLine ;
            while ((executeOutputLine=bufferedReader.readLine())!=null){
                executeOutput.append(executeOutputLine);
            }
            executeMessage.setMessage( executeOutput.toString());
            //资源回收
            outputStreamWriter.close();
            outputStream.close();
            inputStream.close();
            executeProcess.destroy();
    }catch (Exception e){
        e.printStackTrace();
    }
        return   executeMessage;
}
}
