package com.wes.codesandbox.model;

import lombok.Data;

@Data
public class ExecuteMessage {
    private Integer exitValue;
    private String Message;
    private String errorMessage;
    private Long memory;
    private Long time;
}
