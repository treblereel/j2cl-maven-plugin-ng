package com.example;

import com.google.j2cl.junit.apt.J2clTestInput;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(ResourceTest.class)
public class ResourceTest {
    @Test
    public void resourceContents() {
        MyResources res = MyResources.INSTANCE;

        Assert.assertEquals("res-in-root-dir.txt", res.resourceInRoot());
        Assert.assertEquals("res-in-package.txt", res.resourceInPackage());
        Assert.assertEquals("res-in-java-default-package.txt", res.resourceInJavaSourceRoot());
        Assert.assertEquals("res-in-java-nested-package.txt", res.resourceInJavaPackage());

        Assert.assertEquals("filtered main resource", res.applicationMainResource());
        Assert.assertEquals("main version", res.applicationSharedResource());

        MyTestResources testRes = MyTestResources.INSTANCE;
        Assert.assertEquals("filtered main resource", testRes.applicationMainResource());
        Assert.assertEquals("test resource", testRes.applicationTestResource());
        Assert.assertEquals("test version", testRes.applicationSharedResource());
        Assert.assertEquals("test-res-in-root-dir.txt", testRes.testResourceInRoot());
    }
}