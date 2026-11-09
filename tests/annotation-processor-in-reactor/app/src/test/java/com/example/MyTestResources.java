package com.example;

@MyAnnotation
public interface MyTestResources {
    public static final MyTestResources INSTANCE = new MyTestResources_Impl();

    @MyAnnotation("app-main.txt")
    String applicationMainResource();

    @MyAnnotation("app-test.txt")
    String applicationTestResource();

    @MyAnnotation("app-shared.txt")
    String applicationSharedResource();

    @MyAnnotation("test-res-in-root-dir.txt")
    String testResourceInRoot();
}