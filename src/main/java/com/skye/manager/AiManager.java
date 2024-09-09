package com.skye.manager;

import com.skye.common.ErrorCode;
import com.skye.exception.BusinessException;
import com.sun.xml.internal.bind.v2.TODO;
import com.yupi.yucongming.dev.client.YuCongMingClient;
import com.yupi.yucongming.dev.common.BaseResponse;
import com.yupi.yucongming.dev.model.DevChatRequest;
import com.yupi.yucongming.dev.model.DevChatResponse;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class AiManager {

    @Resource
    private YuCongMingClient client;
// TODO: 2024/9/8 自动注入无法找到bean
    // TODO: 2024/9/8 最新：测试类的对象为null，但正常运行时不会

//    String ak = "r5ftpv01r7z1tkjt6a2x9azbbvv49ea0";
//    String sk = "fe7j5be6j7cnajs62hx4lp84rnyj4l78";
//    private YuCongMingClient client = new YuCongMingClient(ak, sk);

    /**
     * @return
     */
    public String doChat(long modelId, String message){
        // 创建请求对象
        DevChatRequest devChatRequest = new DevChatRequest();
        devChatRequest.setModelId(modelId);
        devChatRequest.setMessage(message);

        // 获取响应对象
        BaseResponse<DevChatResponse> response = client.doChat(devChatRequest);

        if (response == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "AI响应错误");

        }
        return response.getData().getContent();
    }
}
