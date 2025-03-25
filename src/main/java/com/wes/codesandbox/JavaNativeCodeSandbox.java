package com.wes.codesandbox;

import com.wes.codesandbox.model.ExecuteCodeRequest;
import com.wes.codesandbox.model.ExecuteCodeResponse;
import org.springframework.stereotype.Component;

/**
 * Java原生实现直接复用模板方法
 */
@Component
public class JavaNativeCodeSandbox extends JavaCodeSandboxTemplate{
    @Override
    public ExecuteCodeResponse  executeCode(ExecuteCodeRequest executeCodeRequest) {
        return super.executeCode(executeCodeRequest);
    }
}
