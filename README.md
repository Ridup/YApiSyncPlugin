# YApi 接口同步插件使用说明

<!-- TOC -->

* [YApi 接口同步插件使用说明](#yapi-接口同步插件使用说明)
    - [写在前面（ Foreword ）](#写在前面-foreword-)
    - [快速上手（ Getting Started ）](#快速上手-getting-started-)
    - [JavaDoc注释规范与约定](#javadoc注释规范与约定)
            - [生成属性备注](#生成属性备注)
            - [生成接口名称](#生成接口名称)
            - [@link 参数定义](#link-参数定义)
            - [实现自定义分类](#实现自定义分类)
            - [支持的注解](#支持的注解)
            - [支持@status 注解](#支持status-注解)
            - [支持Swagger](#支持swagger)
            - [支持@path](#支持path)

<!-- /TOC -->

## 写在前面（ Foreword ）

此插件主要用于工程代码同步至 YApi 接口平台，以便查看接口状态和信息。
同步接口时，请自觉维护好接口列表

* [x] 支持SpringMVC的接口信息获取和同步
* [x] 支持出入参的格式和对应的mock类型
* [x] JavaDoc读取和适配
* [x] 修复路径中包含常量所导致的异常情况
* [x] 增加对模块路径的个性化中文名称匹配
* [x] 对枚举等对象进行明细格式化展示，罗列各项
* [x] 适配泛化调用，支持Interface类型接口同步
* [x] 支持批量 Api 接口同步
* [ ] 简化配置流程，增加UI适配
* [ ] 进行抽象处理，支持自定义的接口信息处理
* [ ] 支持定时/全量同步工程接口信息

## 快速上手（ Getting Started ）

1. 下载`YApiSync.jar`,打开 idea preferneces->plugins-> install plugin from disk,导入 jar 包后(install)，重启
2. 先登录 YApi 平台，使用域账号登陆（基于LDAP认证登陆）
3. 配置信息

   配置信息位置, 在项目目录下，.idea 文件夹下，找到 misc.xml (如果找不到.idea 请查看是否被折叠或被隐藏) 如果是 .ipr 模式创建的 就找到 项目名.ipr

```xml

  <component name="yapi">
    <option name="projectToken">用户项目的Token</option>
    <option name="projectId">用户项目ID</option>
    <option name="yapiUrl">http://xxx.xxx.xxx.xxx:3000</option>
    <option name="projectType">api</option>
  </component>

  <!-- 中台管理台接口平台配置示例 -->
  <component name="yapi">
    <option name="projectToken">4a948bd3ea80a842d30b5f3d8ee855615dc177924d495e441a0802d1cff9b02f</option>
    <option name="projectId">50</option>
    <option name="yapiUrl">http://xxx.xxx.xxx.xxx:3000</option>
    <option name="projectType">api</option>
  </component>
```

![](http://gitlab.yunrong.cn/gallery/loan/uploads/465c4c49c6f49603fd463d2da663c41e/20210526124044.png)

4. 同步

* 如果是 dubbo 项目，选中 dubbo interface 文件中的一个方法（要<b>选中</b>方法名称），右击 YApiSync(`ALT+U` 快捷键)
* 如果是 api 项目，选中 controller 类中的方法名称或类名（要<b>选中</b>方法名称，或类名，选中类名为当前类所有接口都同步），右击 YApiSync(`ALT+U` 快捷键)
* 如果是 component/intf 项目，选中接口的方法名称或类名（要<b>选中</b>方法名称，或类名，选中类名为当前类所有接口都上传），右击 YApiUpload(`ALT+U` 快捷键)
* 如果是选中文件夹或者多个文件，右键或者快捷键(`SHIFT+ALT+U`)支持批量同步

5. 选中

* <b>双击</b>方法或类名即为选中, 如果未选择任何地方 同步 默认按类(`Idea未分类接口`)同步

## JavaDoc注释规范与约定

> 良好的 java doc 注释能更清晰的生成更好的文档

#### 生成属性备注

```java
/**
 * 姓名
 */
private Integer name;

```

#### 生成接口名称

```java
第一种方式
    /**
     * 产品列表
     *
     * @param request
     * @return
     */
    @PostMapping("/queryPage")
    public CommonResponse<PageInfo<ProductDto>> queryPage(@RequestBody QueryProductListRequestDto request) {

    }

第二种方式
    /**
     * 产品列表(如果描述过多，把文字放在这里，这种情况下实际使用的是@name的方法名称）
     *
     * @param request
     * @return
     * @name 产品列表查询
     */
    @PostMapping("/queryPage")
    public CommonResponse<PageInfo<ProductDto>> queryPage(@RequestBody QueryProductListRequestDto request) {

    }
```

#### @link 参数定义

```java

第一种@link 方式

/**

* 状态 {@link com.xxx.constant.StatusConstant}

*/
private Integer status;

第二种@link 方式

import com.xxx.constant.StatusConstant;

/**

* 状态 {@link StatusConstant}

*/
private Integer status;

不支持方式
import com.xxx.constant.*;

/**

* 状态 {@link StatusConstant}

*/
private Integer status;

```

#### 实现自定义分类

通过在方法或类注释中加 @name 注释实现，优先级 方法>类>package 下面或者上面的@name, 如果没有自定义 默认为 `Idea未分类接口`

这里如没必要，我们<b>只使用类上面的@name</b>即可；

```java
package com.project.demo;

import com......

/**
 * 产品查询
 * 如果没有@name，那么使用这里的说明；
 * 如果类的文字说明过多时，则可以把名称放在@name下；
 *
 * @author ridup.cn
 * @version V3.0
 * @name 产品查询（先匹配这里）
 * @since 3.0.1  2020/6/11  19:32
 */
@RestController
@RequestMapping(RESTFULConstant.MODULE_OPERATION_PREFIX + "/v1/productQuery")
public class ProductQueryController {

}
```

#### 支持的注解

```java
@org.springframework.web.bind.annotation.RequestMapping
@org.springframework.web.bind.annotation.GetMapping
@org.springframework.web.bind.annotation.PostMapping
@org.springframework.web.bind.annotation.PutMapping
@org.springframework.web.bind.annotation. PutMapping.DeleteMapping
@org.springframework.web.bind.annotation. PutMapping.PatchMapping
@org.springframework.web.bind.annotation.RequestBody
@org.springframework.web.bind.annotation.RequestParam
@org.springframework.web.bind.annotation.RequestHeader
@org.springframework.web.bind.annotation.RequestAttribute
@org.springframework.web.bind.annotation.PathVariable
@javax.validation.constraints.NotNull
@javax.validation.constraints.NotEmpty
@javax.validation.constraints.NotBlank
```

#### 支持@status 注解

支持已发布(done), 设计中(design), 开发中(undone), 已提测(testing), 已过时(deprecated), 暂停开发(stoping), 新增接口默认 开发中, 更新时如果没有写 status 情况下默认使用当前状态

```java

    /**
     * 产品详情查询
     *
     * @param request
     * @return
     * @status done
     */
    @PostMapping("/queryProductDetail")
    public CommonResponse<XXXResponseDto> queryProductDetail(
        @RequestBody XXXRequestDto request) {
    }

```

#### 支持Swagger

支持以下注解

```

  String API_OPERATION = "io.swagger.annotations.ApiOperation";

  String API_MODEL_PROPERTY = "io.swagger.annotations.ApiModelProperty";

  String API_PARAM = "io.swagger.annotations.ApiParam";

```

#### 支持@path

在方法上指定路径，通过@path 注释, 如下代码，其生成的路径为/path/xxxx/queryProductDetail

```JAVA
    /**
     * 产品详情查询
     * @path /path/xxxx/queryProductDetail
     * @param request
     * @return
     * @status done
     */
    @PostMapping("/queryProductDetail")
    public CommonResponse<XXXResponseDto> queryProductDetail(
        @RequestBody XXXRequestDto request) {
    }
```
aaa
