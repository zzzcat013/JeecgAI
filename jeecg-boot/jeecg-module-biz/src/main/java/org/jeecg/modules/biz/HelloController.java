package org.jeecg.modules.biz;

import lombok.extern.slf4j.Slf4j;
import org.jeecg.common.api.vo.Result;
import org.jeecg.common.util.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * @Description: 测试demo
 * @Author: jeecg-boot
 * @Date:2018-12-29
 * @Version:V1.0
 */
@Slf4j
@RestController
@RequestMapping("/hello")
public class HelloController {

    @Autowired
    private RedisUtil redisUtil;

    @GetMapping(value = "/test")
    public Result<String> test() {
        Result<String> result = new Result<String>();
        result.setResult("Test Hello World!");
        result.setSuccess(true);
        return result;
    }

    @GetMapping(value = "/ok")
    public String hello() {
        return "Hello World!";
    }
}
