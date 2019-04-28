package com.ridup.util;

import com.google.common.base.Strings;

/**
 * @author mu_ran@yahoo.com
 * @version V3.0
 * @since 3.0.1 2020/6/21 22:52
 */
public class ComponentDesUtil {

    /**
     * @description: 获得菜单
     * @param: [text]
     * @return: java.lang.String
     * @author: ridup
     * @date: 2019/5/18
     */
    public static String getMenu(String text) {
        if (Strings.isNullOrEmpty(text) || !text.contains("*/")) {
            return null;
        }
        String[] menuList = text.split("\\*/")[0].split("@name");
        if (menuList.length > 1) {
            return DesUtil.trimFirstAndLastChar(menuList[1].split("\\*")[0].replace("*", "").replace(":","").replace("\n", " ").replace(" ",""), ' ').trim();
        } else {
            return null;
        }
    }

}
