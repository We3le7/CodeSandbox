package com.wes.codesandbox.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.LogContainerResultCallback;

import java.util.List;

public class dockerDemo {
    public static void main(String[] args) throws InterruptedException {

        DockerClient dockerClient=DockerClientBuilder.getInstance().build();

        String image="nginx";
//        PullImageCmd pullImageCmd= dockerClient.pullImageCmd(image);
//        PullImageResultCallback pullImageResultCallback =new PullImageResultCallback(){
//            @Override
//            public void onNext(PullResponseItem item){
//                super.onNext(item);
//            }
//        };
//        pullImageCmd
//                .exec( pullImageResultCallback )
//                .awaitCompletion();
        CreateContainerCmd containerCmd =dockerClient.createContainerCmd(image);
        CreateContainerResponse createContainerResponse=containerCmd
                .withCmd("echo","Hello Docker")
                .exec();
        System.out.println(createContainerResponse);
        String containerId =createContainerResponse.getId();
        ListContainersCmd listContainersCmd= dockerClient.listContainersCmd();
        List<Container> container =listContainersCmd.withShowAll(true).exec();
        for(Container container1:container){
            System.out.println(container1);
        }

        //
        dockerClient.startContainerCmd(containerId).exec();

        LogContainerResultCallback logContainerResultCallback=new LogContainerResultCallback(){
            @Override
            public void onNext(Frame item){
                System.out.println("日志："+new String(item.getPayload()));
                super.onNext(item);
            }
        };
        dockerClient.logContainerCmd(containerId)
                .withStdErr(true)
                .withStdOut(true)
                .exec(logContainerResultCallback).awaitCompletion();
    }
}
