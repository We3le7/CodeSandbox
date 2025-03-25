
package com.wes.codesandbox.Controller;

import cn.hutool.http.server.HttpServerRequest;
import cn.hutool.http.server.HttpServerResponse;
import com.wes.codesandbox.JavaDockerCodeSandbox;
import com.wes.codesandbox.model.ExecuteCodeRequest;
import com.wes.codesandbox.model.ExecuteCodeResponse;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController("/")
public class MainController {
    private static final String AUTH_REQUEST_HEADER="auth";
    private static final String AUTH_REQUEST_SECRET="secretKey";
    @Resource
    private JavaDockerCodeSandbox javaDockerCodeSandbox;
    @GetMapping("/health")
    public String health() {
        return"ok";
    }
    //TODO 策略模式实现不同语言代码沙箱
    @PostMapping("/executeCode")
    ExecuteCodeResponse executeCode(@RequestBody ExecuteCodeRequest executeCodeRequest, HttpServletRequest request,
    HttpServletResponse response){

        String authHeader= request.getHeader(AUTH_REQUEST_HEADER);
        if(!AUTH_REQUEST_SECRET.equals(authHeader)){
            response.setStatus(403);
            return null;
        }
        if (executeCodeRequest== null) {

            throw new RuntimeException("请求参数为空");
        }
        ExecuteCodeResponse executeCodeResponse=javaDockerCodeSandbox.executeCode(executeCodeRequest);
        System.out.println(executeCodeResponse);
        return  executeCodeResponse;
    }
}
