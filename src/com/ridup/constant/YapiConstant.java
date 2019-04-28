package com.ridup.constant;

/**
 * yapi 接口
 *
 * @author ridup
 * @date 2019/1/31 3:29 PM
 */
public interface YapiConstant {
    /**
     * 目录
     */
    String menu="Idea未分类接口";
    /**
     * 新增或者更新接口
     */
    String yapiSave="/api/interface/save";
    /**
     * 获取接口菜单列表
     */
    String yapiListMenu="/api/interface/list_menu";
    /**
     * 更新接口
     */
    String yapiUp="/api/interface/up";
    /**
     * 获取接口列表数据
     */
    String yapiList="/api/interface/list";
    /**
     * 新增接口
     */
    String yapiAdd="/api/interface/add";
    /**
     * 新增接口分类
      */
    String yapiAddCat="/api/interface/add_cat";
    /**
     * 获取接口数据
     */
    String yapiGet="/api/interface/get";

    /**
     * 获取菜单列表
     */
    String yapiCatMenu="/api/interface/getCatMenu";

    /**
     * 通过路径获得接口
     */
    String yapiGetByPath="/api/interface/getbypath";
}
