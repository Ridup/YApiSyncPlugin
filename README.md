YApiSyncPlugin

# YApi 接口上传插件使用说明

## 写在前面

此插件主要用于工程代码同步至 YApi 接口平台，以便查看接口状态和信息。
上传接口时，请自觉维护好接口列表
- [x] 修复路径中包含常量所导致的异常情况
- [x] 增加对模块路径的个性化中文名称匹配
- [x] 对枚举等对象进行格式化展示
- [ ] 支持批量 Api 接口上传
- [ ] 简化配置流程

## 使用方法

1. 下载`YApiSyncPlugin.jar`,打开 idea preferneces->plugins-> install plugin from disk,导入 jar 包后(install)，重启
2. 先登录 YApi 平台，使用域账号登陆
3. 配置信息  
   配置信息位置, 在项目目录下，.idea 文件夹下，找到 misc.xml (如果找不到.idea 请查看是否被折叠或被隐藏) 如果是 .ipr 模式创建的 就找到 项目名.ipr

```xml

  <component name="yapi">
    <option name="projectToken">用户项目的Token</option>
    <option name="projectId">用户项目ID</option>
    <option name="yapiUrl">http://127.0.0.1:3000</option>
    <option name="projectType">api</option>
  </component>

  <!-- 中台管理台接口平台配置示例 -->
  <component name="yapi">
    <option name="projectToken">4a948bd3ea80a842d30b5f3d8ee855615dc177924d495e441a0802d1cff9b02f</option>
    <option name="projectId">50</option>
    <option name="yapiUrl">http://127.0.0.1:3000</option>
    <option name="projectType">api</option>
  </component>
```
4. 上传

- 如果是 dubbo 项目，选中 dubbo interface 文件中的一个方法（要<b>选中</b>方法名称），右击 YApiSyncPlugin(alt+u 快捷键)
- 如果是 api 项目，选中 controller 类中的方法名称或类名（要<b>选中</b>方法名称，或类名，选中类名为当前类所有接口都上传），右击 YApiSyncPlugin(alt+u 快捷键)

5. 选中

- <b>双击</b>方法或类名即为选中,如果未选择任何地方 上传 默认按类(`Idea未分类接口`)上传
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
aaa
