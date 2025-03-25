package com.wes.codesandbox;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.wes.codesandbox.model.ExecuteCodeRequest;
import com.wes.codesandbox.model.ExecuteCodeResponse;
import com.wes.codesandbox.model.ExecuteMessage;
import com.wes.codesandbox.model.JudgeInfo;
import com.wes.codesandbox.utils.ProcessUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class JavaCodeSandboxTemplate implements CodeSandbox {
    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";
    private static final String GLOBAL_JAVA_CLASS_NAME = "Main.java";
    private static final Long TIME_OUT = 5000L;
    /**
     * 1.把用户代码储存为文件
     * @param code 用户代码
     * @return
     */
    public File saveCodeFile(String code){
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        //判断目录是否存在没有则新建
        if (!FileUtil.exist(globalCodePathName)) {
            FileUtil.mkdir(globalCodePathName);
        }       //将用户代码隔离存放
        String userCodeParent = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParent + File.separator + GLOBAL_JAVA_CLASS_NAME;
        //将用户代码写入文件
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);
        return userCodeFile;
    }

    /**
     * 2.编译代码文件
     * @param userCodeFile
     * @return
     */
    public ExecuteMessage complieFile(File userCodeFile,ExecuteCodeResponse executeCodeResponse){
        String compliedCmd = String.format("javac -encoding utf-8 %s", userCodeFile.getAbsolutePath());
        try {
            Process executeProcess = Runtime.getRuntime().exec(compliedCmd);
            ExecuteMessage executeMessage = ProcessUtils.processGetMessage(executeProcess, "编译");

            if(executeMessage.getExitValue()!=0){
                executeCodeResponse.setStatus(3);
                executeCodeResponse.setMessage("Compile Error");
            }
            return executeMessage;
        } catch (Exception e) {
            throw new RuntimeException();
        }
    }

    /**
     *3.执行代码
     * @param userCodeFile
     * @param inputList
     * @return
     */
    public List<ExecuteMessage> runFile(File userCodeFile,List<String> inputList){
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        String userCodeParent = userCodeFile.getParentFile().getAbsolutePath();
        for (String inputArgs : inputList) {
            String executeCmd = String.format("java -Dfile.encoding=utf-8 -cp %s Main %s", userCodeParent, inputArgs);
            try {
                Process executeProcess = Runtime.getRuntime().exec(executeCmd);
                //超时控制
                new Thread(()->{
                    try{
                         Thread.sleep(TIME_OUT);
                         System.out.println("超时");
                        executeProcess.destroy();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
                ExecuteMessage executeMessage = ProcessUtils.processGetMessage(executeProcess, "执行");
                executeMessageList.add(executeMessage);
                System.out.println(executeMessage);
            } catch (Exception e) {
                throw new RuntimeException("执行错误");
            }
        }
        return  executeMessageList;
    }

    /**
     * 4.整理输出
     * @param executeMessageList
     * @return
     */
    public ExecuteCodeResponse  getOutputResponse(List<ExecuteMessage> executeMessageList){
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        //取最大值判断程序是否超时
        Long maxTime = 0L;
        Long maxMemory = 0L;
        for (ExecuteMessage executeMessage : executeMessageList) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            Long memory=executeMessage.getMemory();
            if(time!=null){
                maxTime = Math.max(maxTime,time);
            }
            if(memory!=null){
                maxMemory=Math.max(memory,maxMemory);
            }
        }
        if (outputList.size() == executeMessageList.size()) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(maxMemory);
        executeCodeResponse.setJudgeInfo(judgeInfo);
        return executeCodeResponse;
    }

    /**
     * 5.文件清理
     * @param userCodeFile
     * @return
     */
    public boolean deleteFile(File userCodeFile){
        if (userCodeFile.getParent() != null) {
            String userCodeParent = userCodeFile.getParentFile().getAbsolutePath();
            boolean delete = FileUtil.del(userCodeParent);
            System.out.println("删除" + (delete ? "成功" : "失败"));
            return delete;
        }
        return true;
    }

    /**
     * 6.获取错误响应
     *
     * @param e
     * @return
     */
    private ExecuteCodeResponse getErrorResponse(Throwable e) {
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        executeCodeResponse.setOutputList(new ArrayList<>());
        executeCodeResponse.setMessage(e.getMessage());
        //表示代码沙箱错误
        executeCodeResponse.setStatus(3);
        executeCodeResponse.setJudgeInfo(new JudgeInfo());
        return executeCodeResponse;
    }
    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        ExecuteCodeResponse  executeCodeResponse=new ExecuteCodeResponse();
        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        //1.代码储存为文件
        File userCodeFile = saveCodeFile(code);
        //2.编译代码文件
        ExecuteMessage complieFileExecuteMessage= complieFile(userCodeFile,executeCodeResponse);
        if(executeCodeResponse.getMessage()!=null){
            return executeCodeResponse;
        }
        System.out.println(complieFileExecuteMessage);
        //3.执行代码
        List<ExecuteMessage> executeMessageList= runFile(userCodeFile,inputList);
        //4.整理输出
        executeCodeResponse = getOutputResponse(executeMessageList);
       //5.文件清理
        if(!deleteFile(userCodeFile)){
            System.out.println("删除失败");
        }
        return executeCodeResponse;
    }

}
