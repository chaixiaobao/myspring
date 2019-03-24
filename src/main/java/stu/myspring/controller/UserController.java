package stu.myspring.controller;

import java.io.IOException;

import javax.servlet.http.HttpServletResponse;

import stu.myspring.annotations.CHAutowired;
import stu.myspring.annotations.CHController;
import stu.myspring.annotations.CHRequestMapping;
import stu.myspring.annotations.CHRequestParam;
import stu.myspring.service.UserService;

@CHController
@CHRequestMapping("/demo")
public class UserController {

    @CHAutowired
    UserService userService;

    @CHRequestMapping("/test")
    public void print(HttpServletResponse response, @CHRequestParam("name") String userName,
            @CHRequestParam("age") String age) throws IOException {
        String result = userService.service(userName);
        response.getWriter().write("just simple test!!! -> " + result + "->" + age);
    }

}
