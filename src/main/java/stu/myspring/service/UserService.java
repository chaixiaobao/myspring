package stu.myspring.service;

import stu.myspring.annotations.CHService;

@CHService
public class UserService {

    public String service(String userName) {
        return "Hello " + userName;
    }

}
