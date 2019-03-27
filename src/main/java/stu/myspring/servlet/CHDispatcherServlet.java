package stu.myspring.servlet;

import java.beans.Introspector;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import stu.myspring.annotations.CHAutowired;
import stu.myspring.annotations.CHController;
import stu.myspring.annotations.CHRequestMapping;
import stu.myspring.annotations.CHRequestParam;
import stu.myspring.annotations.CHService;
import stu.myspring.beans.CHBeanDefinition;

/**
 * Servlet implementation class CHDispatcherServlet
 */
public class CHDispatcherServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private Map<String, CHBeanDefinition> beanMaps = new HashMap<String, CHBeanDefinition>();
    private Map<String, Object> context = new ConcurrentHashMap<String, Object>();
    private Map<String, Handler> handlerMapping = new HashMap<String, Handler>();

    /**
     * Default constructor.
     */
    public CHDispatcherServlet() {
        // TODO Auto-generated constructor stub
    }

    /**
     * @see Servlet#init(ServletConfig)
     */
    public void init(ServletConfig config) throws ServletException {

        // 读取配置文件
        String basepackage = getBasepackage(config.getInitParameter("contextConfigLocation"));

        // 存储Bean定义
        loadBeanDefinitions(basepackage);

        // 初始化Bean，同时完成依赖注入
        initBeans();

        // 初始化mapping
        initHandlerMapping();

        System.out.println("init CHDispatcherServlet complete...");

        // 初始化adapter

    }

    private void initHandlerMapping() {
        // TODO Auto-generated method stub
        for (Map.Entry<String, Object> entry : this.context.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (clazz.isAnnotationPresent(CHController.class)) {
                String baseUrl = "";
                if (clazz.isAnnotationPresent(CHRequestMapping.class)) {
                    baseUrl = clazz.getAnnotation(CHRequestMapping.class).value();
                }
                for (Method method : clazz.getMethods()) {
                    if (method.isAnnotationPresent(CHRequestMapping.class)) {
                        String url = (baseUrl + "/" + method.getAnnotation(CHRequestMapping.class).value())
                                .replaceAll("/+", "/");
                        Handler handler = new Handler();
                        handler.setUrl(url);
                        handler.setMethod(method);
                        handler.setController(entry.getValue());
                        this.handlerMapping.put(url, handler);
                    }
                }
            }
        }
    }

    private void initBeans() {
        // TODO Auto-generated method stub
        for (Map.Entry<String, CHBeanDefinition> entry : this.beanMaps.entrySet()) {
            if (!entry.getValue().isLazyInit() && !this.context.containsKey(entry.getKey())) {
                initBean(entry.getKey(), entry.getValue());
            }
        }
    }

    private void initBean(String beanName) {
        // TODO Auto-generated method stub
        if (!this.beanMaps.containsKey(beanName)) {
            throw new RuntimeException(beanName + " not exists!!!");
        }
        initBean(beanName, this.beanMaps.get(beanName));
    }

    private void initBean(String beanName, CHBeanDefinition bd) {
        // TODO Auto-generated method stub
        Class<?> clazz = bd.getBeanClass();
        Object instance = null;
        try {
            instance = clazz.newInstance();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        populateBean(clazz, instance);
        context.put(beanName, instance);
        for (Class<?> i : clazz.getInterfaces()) {
            this.context.put(i.getName(), instance);
        }
    }

    private void populateBean(Class<?> clazz, Object instance) {
        // TODO Auto-generated method stub
        try {
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (field.isAnnotationPresent(CHAutowired.class)) {
                    String beanName = field.getAnnotation(CHAutowired.class).value();
                    if ("".equals(beanName.trim())) {
                        if (field.getType().isInterface()) {
                            beanName = field.getType().getName();
                        } else {
                            beanName = Introspector.decapitalize(field.getType().getSimpleName());
                        }
                    }
                    if (!field.isAccessible()) {
                        field.setAccessible(true);
                    }
                    if (!this.context.containsKey(beanName)) {
                        initBean(beanName);
                    }
                    field.set(instance, this.context.get(beanName));
                }
            }
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }
    }

    private void loadBeanDefinitions(String basepackage) {
        // TODO Auto-generated method stub
        String classPath = this.getClass().getClassLoader().getResource("").getPath();
        String dirPath = classPath + basepackage.replaceAll("\\.", Matcher.quoteReplacement(File.separator));
        File baseDir = new File(dirPath);
        for (File file : baseDir.listFiles()) {
            if (file.isDirectory()) {
                loadBeanDefinitions(basepackage + "." + file.getName());
            } else if (file.getName().endsWith(".class")) {
                // System.out.println(file.getName().substring(0, file.getName().indexOf(".")));
                try {
                    String className = basepackage + "." + file.getName().replace(".class", "");
                    Class<?> clazz = Class.forName(className);
                    if (clazz.isAnnotationPresent(CHController.class)) {
                        CHController annotation = clazz.getAnnotation(CHController.class);
                        CHBeanDefinition beanDefinition = new CHBeanDefinition();
                        beanDefinition.setClassName(className);
                        beanDefinition.setBeanClass(clazz);
                        beanDefinition.setLazyInit(annotation.lazyInit());
                        if (annotation.value() == null || annotation.value().equals("")) {
                            this.beanMaps.put(Introspector.decapitalize(clazz.getSimpleName()),
                                    beanDefinition);
                        } else {
                            this.beanMaps.put(annotation.value(), beanDefinition);
                        }
                    } else if (clazz.isAnnotationPresent(CHService.class)) {
                        CHService annotation = clazz.getAnnotation(CHService.class);
                        CHBeanDefinition beanDefinition = new CHBeanDefinition();
                        beanDefinition.setClassName(className);
                        beanDefinition.setBeanClass(clazz);
                        beanDefinition.setLazyInit(annotation.lazyInit());
                        if (annotation.value() == null || annotation.value().equals("")) {
                            this.beanMaps.put(Introspector.decapitalize(clazz.getSimpleName()),
                                    beanDefinition);
                        } else {
                            this.beanMaps.put(annotation.value(), beanDefinition);
                        }
                        for (Class<?> i : clazz.getInterfaces()) {
                            this.beanMaps.put(i.getName(), beanDefinition);
                        }
                    } else {

                    }
                } catch (ClassNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 根据路径返回待扫描包路径
     * 
     * @param location
     * @return
     */
    private String getBasepackage(String location) {
        try {
            if (location.startsWith("classpath:")) {
                String path = location.substring("classpath:".length());
                String fullPath = this.getClass().getClassLoader().getResource(path).getPath();
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(fullPath);
                NodeList nodeList = doc.getElementsByTagName("scan-package");
                Element node = (Element) nodeList.item(0);
                String basepackage = node.getAttribute("base-package");
                return basepackage;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse
     *      response)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // TODO Auto-generated method stub
        doDispatch(request, response);
    }

    private void doDispatch(HttpServletRequest request, HttpServletResponse response) {
        // TODO Auto-generated method stub
        try {
            Handler handler = getHandler(request);
            if (handler == null) {
                response.getWriter().write("404 not found!!!");
                return;
            }
            Object[] params = getParams(request, response, handler);
            handler.getMethod().invoke(handler.getController(), params);
        } catch (Exception e) {
            // TODO: handle exception
            try {
                response.getWriter().write(e.toString());
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private Object[] getParams(HttpServletRequest request, HttpServletResponse response, Handler handler) {
        // TODO Auto-generated method stub
        Method method = handler.getMethod();
        Object[] params = new Object[method.getParameterCount()];
        Class<?>[] paramsClass = method.getParameterTypes();
        Annotation[][] annotations = method.getParameterAnnotations();
        Map<String, String[]> paramsMap = request.getParameterMap();
        for (int i = 0; i < paramsClass.length; i++) {
            if (paramsClass[i] == HttpServletRequest.class) {
                params[i] = request;
            } else if (paramsClass[i] == HttpServletResponse.class) {
                params[i] = response;
            } else {
                for (int j = 0; j < annotations[i].length; j++) {
                    if (annotations[i][j].annotationType() == CHRequestParam.class) {
                        CHRequestParam annotation = (CHRequestParam) annotations[i][j];
                        String paramName = annotation.value();
                        params[i] = Arrays.toString(paramsMap.get(paramName)).replaceAll("\\[|\\]", "");
                        break;
                    }
                }
            }
        }
        return params;
    }

    private Handler getHandler(HttpServletRequest request) {
        // TODO Auto-generated method stub
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String url = uri.replace(contextPath, "");
        return this.handlerMapping.get(url);
    }

    /**
     * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse
     *      response)
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // TODO Auto-generated method stub
        doGet(request, response);
    }

    @SuppressWarnings("unused")
    private static class Handler {
        Object controller;
        Method method;
        String url;

        public Object getController() {
            return controller;
        }

        public void setController(Object controller) {
            this.controller = controller;
        }

        public Method getMethod() {
            return method;
        }

        public void setMethod(Method method) {
            this.method = method;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

}
