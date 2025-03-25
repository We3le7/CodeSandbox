package com.wes.codesandbox;
import cn.hutool.core.util.ArrayUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.wes.codesandbox.model.ExecuteCodeRequest;
import com.wes.codesandbox.model.ExecuteCodeResponse;
import com.wes.codesandbox.model.ExecuteMessage;
import com.wes.codesandbox.model.JudgeInfo;
import com.wes.codesandbox.utils.ProcessUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
@Component
public class JavaDockerCodeSandbox extends JavaCodeSandboxTemplate {
    private static final Boolean FIRST_INIT = false;
    public static void main(String[]args){
        JavaDockerCodeSandbox javaDockerCodeSandbox=new JavaDockerCodeSandbox();
        ExecuteCodeRequest executeCodeRequest=new ExecuteCodeRequest();
        executeCodeRequest.setInputList(Arrays.asList("1 2","1 3"));
        String code=ResourceUtil.readStr("testCode.simpleComputeArgs/Main.java",StandardCharsets.UTF_8);
        executeCodeRequest.setCode(code);
        executeCodeRequest.setLanguage("java");
        ExecuteCodeResponse executeCodeResponse =javaDockerCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
    }

    /**
     * 3.创建容器，执行代码
     * @param userCodeFile
     * @param inputList
     * @return
     */

    @Override
    public List<ExecuteMessage> runFile(File userCodeFile,List<String> inputList) {
        String userCodeParent = userCodeFile.getParentFile().getAbsolutePath();
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();
        String image = "openjdk:8-alpine";
        List<ExecuteMessage> executeMessageList = new ArrayList<>();
        //初次运行则拉取镜像
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);

            }
        }

        //创建容器,可交互容器，能接受多次输入输出
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        //创建容器时指定文件路径
        HostConfig hostConfig = new HostConfig();
        //编写容器配置，内存，网络等等
        hostConfig.withMemory(100 * 1000 * 1000L)
                .withMemorySwap(0L)
                .withCpuCount(1L)
//                .withSecurityOpts(Arrays.asList("seccomp=安全管理配置"))
                .setBinds(new Bind(userCodeParent, new Volume("/app")));
        CreateContainerResponse createContainerResponse = containerCmd
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
                .withHostConfig(hostConfig)
                .withAttachStdin(true)
                .withAttachStdin(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        System.out.println(createContainerResponse);
        String containerId = createContainerResponse.getId();
        //启动容器
        dockerClient.startContainerCmd(containerId).exec();
//        docker exec funny_satoshi java -cp /app Main 1 3
        long maxTime = 0L;
        final Boolean[] timeout = {true};
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[]{"java", "-cp", "/app", "Main"}, inputArgsArray);

            // 创建执行命令
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();
            System.out.println("创建执行命令：" + execCreateCmdResponse);

            ExecuteMessage executeMessage = new ExecuteMessage();
            String execId = execCreateCmdResponse.getId();

            StringBuilder stdoutBuilder = new StringBuilder(); // 用于合并 STDOUT 数据
            StringBuilder stderrBuilder = new StringBuilder(); // 用于合并 STDERR 数据

            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {
                @Override
                public void onComplete() {
                    //规定时间内完成则不超时
                    timeout[0] = false;
                    super.onComplete();
                }

                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (StreamType.STDERR.equals(streamType)) {
                        stderrBuilder.append(new String(frame.getPayload()));
                        // 检查是否接收到完整输出（例如以换行符结尾）
                        if (frame.getPayload().length > 0 && frame.getPayload()[frame.getPayload().length - 1] == '\n') {
                            // 去掉末尾的换行符
                            String output = stderrBuilder.toString().trim();
                            executeMessage.setErrorMessage(output);
                            System.err.println("输出错误结果：" + executeMessage.getErrorMessage());
                            stderrBuilder.setLength(0); // 清空 StringBuilder
                        }
                    } else {
                        stdoutBuilder.append(new String(frame.getPayload()));
                        // 检查是否接收到完整输出（例如以换行符结尾）
                        if (frame.getPayload().length > 0 && frame.getPayload()[frame.getPayload().length - 1] == '\n') {
                            // 去掉末尾的换行符
                            String output = stdoutBuilder.toString().trim();
                            executeMessage.setMessage(output);
                            System.out.println("输出结果：" + executeMessage.getMessage());
                            stdoutBuilder.setLength(0); // 清空 StringBuilder
                        }
                    }
                    super.onNext(frame);
                }
            };

            try {
                stopWatch.start();
                // 执行命令并等待完成
                dockerClient.execStartCmd(execId).exec(execStartResultCallback).awaitCompletion();
                stopWatch.stop();
                executeMessage.setTime(stopWatch.getLastTaskTimeMillis());
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                Thread.currentThread().interrupt(); // 恢复中断状态
                throw new RuntimeException(e);
            }

            // 异步获取内存统计信息
            final long[] memoryUsage = {0L}; // 用于存储内存使用情况
            try (StatsCmd statsCmd = dockerClient.statsCmd(containerId)) {
                statsCmd.exec(new ResultCallback<Statistics>() {
                    @Override
                    public void onStart(Closeable closeable) {
                        System.out.println("开始获取内存统计信息...");
                    }

                    @Override
                    public void onNext(Statistics stats) {
                        if (stats.getMemoryStats() != null) {
                            memoryUsage[0] = stats.getMemoryStats().getUsage();
                            System.out.println("内存占用: " + memoryUsage[0]);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
//                        System.err.println("获取内存统计信息失败: " + throwable.getMessage());
                    }

                    @Override
                    public void onComplete() {
                        System.out.println("内存统计信息获取完成");
                    }

                    @Override
                    public void close() throws IOException {
                        System.out.println("关闭内存统计信息获取");
                    }
                });
//
                // 等待一段时间以确保统计信息被获取
                Thread.sleep(1000); // 根据实际情况调整等待时间
            } catch (Exception e) {
                System.err.println("获取内存统计信息时出错: " + e.getMessage());
            }

            // 设置内存使用情况
            executeMessage.setMemory(memoryUsage[0]);
            maxTime = Math.max(executeMessage.getTime(), maxTime);
            executeMessageList.add(executeMessage);}
            dockerClient.stopContainerCmd(containerId).exec();
            try {
                RemoveContainerCmd removeContainerCmd = dockerClient.removeContainerCmd(containerId);
                removeContainerCmd.withForce(true); // 强制删除，如果容器仍在运行则强制删除
                removeContainerCmd.exec();
                System.out.println("容器已删除: " + containerId);
            } catch (Exception e) {
                System.err.println("删除容器时出错: " + e.getMessage());
            }
            return executeMessageList;

    }
}





