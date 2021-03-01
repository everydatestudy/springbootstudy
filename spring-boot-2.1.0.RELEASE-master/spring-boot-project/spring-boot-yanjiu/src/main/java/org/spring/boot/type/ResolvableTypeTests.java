package org.spring.boot.type;
//通过以上，已经了解到获取Type的几种场景

//Class
//Field字段
//Method参数
//Constructor参数
//
//总结一下:需求是要获取Type和GenericType,但每个对象获取方法并不一致,怎么办?
//需要做一个适配器,Spring中的ResolvableType就是来解决这个问题的.
//ResolvableType可以传入上面任何参数,然后定义了公共的方法来统一方法规范调用，简化了获取Type的成本
//
//比如MethodParameter已经对Method和Constructor做了一个整合,ResolvableType则对上面整体都做了一个整合


public class ResolvableTypeTests {

}
